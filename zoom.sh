#!/bin/bash

# Optimized & Pi‑friendly build/deploy script
# Goals:
#  - Avoid hangs on low‑resource Raspberry Pi by skipping unnecessary heavy work
#  - Only rebuild JAR + image when source (git HEAD) changed (unless forced)
#  - Provide safety guards for temperature & free memory
#  - Allow optional flags to force clean / disable docker / force no-cache build
#  - Keep prior behavior as default where reasonable
#
# Flags:
#   --force-clean        Always run gradlew clean (default: only if forced or cache invalid)
#   --force-build        Force Gradle + Docker rebuild even if no source changes
#   --no-docker          Build JAR only, skip any Docker steps
#   --no-gradle          Skip Gradle build (use existing build/libs/app.jar)
#   --no-cache           Pass --no-cache to docker build
#   --quiet-logs         Skip tailing container logs at end
#   --max-workers=N      Pass --max-workers to Gradle (limits parallelism)
#   --help               Show help & exit
# Env overrides:
#   MAX_TEMP_C (default 78)  : Throttle / wait if SoC temp exceeds (loops until safe)
#   MIN_FREE_MB (default 150): Abort early if free memory (MemAvailable) below this
#   JAR_SYMLINK (default app.jar)
#   BUILD_COMMIT_FILE (default .last_build_commit)

set -euo pipefail

# ----------------------- Config & Flag Parsing ----------------------- #
FORCE_CLEAN=false
FORCE_BUILD=false
disable_docker=false
skip_gradle=false
docker_no_cache=false
quiet_logs=false
MAX_WORKERS=""

for arg in "$@"; do
  case "$arg" in
    --force-clean) FORCE_CLEAN=true ;;
    --force-build) FORCE_BUILD=true ;;
    --no-docker) disable_docker=true ;;
    --no-gradle) skip_gradle=true ;;
    --no-cache) docker_no_cache=true ;;
    --quiet-logs) quiet_logs=true ;;
    --max-workers=*) MAX_WORKERS="${arg#*=}" ;;
    --help)
      echo "Usage: $0 [--force-clean] [--force-build] [--no-docker] [--no-gradle] [--no-cache] [--quiet-logs] [--max-workers=N]"; exit 0 ;;
    *) echo "Unknown flag: $arg"; exit 1 ;;
  esac
done

# Defaults
MAX_TEMP_C=${MAX_TEMP_C:-78}
MIN_FREE_MB=${MIN_FREE_MB:-150}
JAR_SYMLINK=${JAR_SYMLINK:-app.jar}
BUILD_COMMIT_FILE=${BUILD_COMMIT_FILE:-.last_build_commit}
GRADLEW=./gradlew
JAR_DIR=build/libs

command -v date >/dev/null || alias date='busybox date'

log() { printf '%s %s\n' "[$(date +%H:%M:%S)]" "$*"; }
section() { echo; log "==== $* ===="; }
warn() { log "⚠️  $*"; }
err() { log "❌ $*"; exit 1; }

# ----------------------- System Information ----------------------- #
section "System Info"
STORAGE=$(df -h / | awk 'NR==2 {print $2" used="$3" avail="$4}')
RAM_TOTAL=$(free -h | awk '/^Mem:/ {print $2}')
TEMP_CMD="$(command -v vcgencmd || true)"
if [ -n "$TEMP_CMD" ]; then
  TEMP_RAW=$($TEMP_CMD measure_temp 2>/dev/null || true)
else
  # Fallback: read thermal zone (in millideg C)
  if [ -f /sys/class/thermal/thermal_zone0/temp ]; then
    t=$(cat /sys/class/thermal/thermal_zone0/temp); TEMP_RAW="temp=$(awk -v v="$t" 'BEGIN{printf "%.1f" , v/1000}')'C"; else TEMP_RAW="temp=unknown"; fi
fi
log "Storage: $STORAGE"
log "RAM: $RAM_TOTAL"
log "Temperature: $TEMP_RAW"
log "Starting build (safe mode for Pi)"

# ----------------------- Safety Guards ----------------------- #
get_temp_c() {
  if [ -n "$TEMP_CMD" ]; then
    "$TEMP_CMD" measure_temp 2>/dev/null | tr -d "'C" | cut -d= -f2 || echo 0
  elif [ -f /sys/class/thermal/thermal_zone0/temp ]; then
    awk '{printf "%.1f", $1/1000}' /sys/class/thermal/thermal_zone0/temp
  else
    echo 0
  fi
}

wait_for_cooldown() {
  local t
  t=$(get_temp_c)
  if [ "${t%%.*}" -ge "$MAX_TEMP_C" ]; then
    warn "Temp ${t}C >= ${MAX_TEMP_C}C. Cooling before build..."
    while true; do
      sleep 10
      t=$(get_temp_c)
      log "Temp now ${t}C"
      [ "${t%%.*}" -lt "$MAX_TEMP_C" ] && break
    done
    log "Temperature acceptable; continuing"
  fi
}

check_memory() {
  if grep -q MemAvailable /proc/meminfo; then
    local avail_kb
    avail_kb=$(awk '/MemAvailable:/ {print $2}' /proc/meminfo)
    local avail_mb=$((avail_kb/1024))
    if [ "$avail_mb" -lt "$MIN_FREE_MB" ]; then
      err "MemAvailable ${avail_mb}MB < MIN_FREE_MB ${MIN_FREE_MB}MB. Aborting to avoid hang."
    fi
    log "MemAvailable: ${avail_mb}MB (threshold ${MIN_FREE_MB}MB)"
  else
    warn "MemAvailable not found; skipping memory guard"
  fi
}

wait_for_cooldown
check_memory

# ----------------------- Git Change Detection ----------------------- #
section "Git Sync"
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  log "Fetching remote..."
  git fetch --quiet || warn "Fetch failed (offline?)"
  LOCAL=$(git rev-parse HEAD)
  if git rev-parse @{u} >/dev/null 2>&1; then
    REMOTE=$(git rev-parse @{u})
    if [ "$LOCAL" != "$REMOTE" ]; then
      log "Pulling updates..."; git pull --rebase --autostash || err "git pull failed"
      LOCAL=$(git rev-parse HEAD)
    else
      log "Repo already up to date"
    fi
  else
    warn "No upstream tracking branch configured"
  fi
else
  warn "Not a git repository; skipping git sync"
  LOCAL="nogit-$(date +%s)"
fi

PREV_BUILD_COMMIT=""
if [ -f "$BUILD_COMMIT_FILE" ]; then
  PREV_BUILD_COMMIT=$(cat "$BUILD_COMMIT_FILE" || true)
fi

SHOULD_BUILD=true
if [ "$FORCE_BUILD" = false ] && [ "$skip_gradle" = false ]; then
  if [ -n "$PREV_BUILD_COMMIT" ] && [ "$PREV_BUILD_COMMIT" = "$LOCAL" ] && [ -f "$JAR_DIR/$JAR_SYMLINK" ]; then
    SHOULD_BUILD=false
    log "No source changes since last build commit ($LOCAL); skipping Gradle build. Use --force-build to override."
  fi
fi

# ----------------------- Gradle Build ----------------------- #
if [ "$skip_gradle" = true ]; then
  log "Skipping Gradle build per flag --no-gradle"
else
  if [ "$FORCE_CLEAN" = true ]; then
    section "Gradle Clean (forced)"
    $GRADLEW clean || err "gradle clean failed"
  elif [ "$SHOULD_BUILD" = true ] && [ ! -d build/classes ]; then
    # First build or missing classes -> clean
    section "Gradle Clean (cold build)"
    $GRADLEW clean || err "gradle clean failed"
  else
    log "Skipping clean (cached)"
  fi

  if [ "$SHOULD_BUILD" = true ]; then
    wait_for_cooldown; check_memory
    section "Gradle BootJar"
    GRADLE_OPTS=(bootJar --console=plain -Dorg.gradle.jvmargs="-Xmx640m -XX:MaxMetaspaceSize=384m -XX:+UseG1GC -XX:G1HeapRegionSize=8m")
    [ -n "$MAX_WORKERS" ] && GRADLE_OPTS+=(--max-workers="$MAX_WORKERS")
    # Lower priority to reduce system contention
    if command -v nice >/dev/null; then
      nice -n 5 $GRADLEW "${GRADLE_OPTS[@]}" || err "Gradle build failed"
    else
      $GRADLEW "${GRADLE_OPTS[@]}" || err "Gradle build failed"
    fi

    ARTIFACT_PATTERN="$JAR_DIR/*-SNAPSHOT.jar"
    JAR_FILE=$(ls $ARTIFACT_PATTERN 2>/dev/null | head -n1 || true)
    [ -z "$JAR_FILE" ] && err "No JAR found matching $ARTIFACT_PATTERN"
    ln -sf "$(basename "$JAR_FILE")" "$JAR_DIR/$JAR_SYMLINK"
    log "Using JAR: $JAR_FILE"
    (command -v sha256sum >/dev/null && sha256sum "$JAR_FILE") || (command -v shasum >/dev/null && shasum -a 256 "$JAR_FILE" || true)
    echo "$LOCAL" > "$BUILD_COMMIT_FILE" || warn "Failed writing build commit file"
  else
    log "Reusing existing JAR: $JAR_DIR/$JAR_SYMLINK"
  fi
fi

[ "$disable_docker" = true ] && { log "--no-docker specified; skipping Docker build/run"; exit 0; }

[ -f "$JAR_DIR/$JAR_SYMLINK" ] || err "Missing JAR $JAR_DIR/$JAR_SYMLINK. Cannot proceed to Docker."

# ----------------------- Docker Compose Detection ----------------------- #
section "Docker Build/Deploy"
if command -v docker >/dev/null 2>&1; then
  if docker compose version >/dev/null 2>&1; then
    DC='docker compose'
  else
    DC='docker-compose'
  fi
else
  err "Docker not installed"
fi

# Use cache unless forced
DOCKER_BUILD_FLAGS=()
[ "$docker_no_cache" = true ] && DOCKER_BUILD_FLAGS+=(--no-cache)

# Rebuild only if jar changed or forced
NEED_IMAGE=true
if [ "$FORCE_BUILD" = false ]; then
  # Compute hash of current symlink target jar
  CURRENT_JAR_REAL=$(readlink -f "$JAR_DIR/$JAR_SYMLINK" 2>/dev/null || echo "$JAR_DIR/$JAR_SYMLINK")
  LAST_HASH_FILE=.last_image_jar_hash
  CUR_HASH=$( (command -v sha256sum >/dev/null && sha256sum "$CURRENT_JAR_REAL" | awk '{print $1}') || (shasum -a 256 "$CURRENT_JAR_REAL" | awk '{print $1}') )
  PREV_HASH=$(cat "$LAST_HASH_FILE" 2>/dev/null || true)
  if [ "$CUR_HASH" = "$PREV_HASH" ] && [ "$docker_no_cache" = false ]; then
    NEED_IMAGE=false
    log "Jar unchanged; skipping Docker image rebuild (use --force-build or --no-cache to override)"
  fi
fi

if [ "$NEED_IMAGE" = true ]; then
  wait_for_cooldown; check_memory
  log "Building Docker image (flags: ${DOCKER_BUILD_FLAGS[*]:-(cache)})"
  # Avoid pruning aggressively each run; only prune dangling occasionally
  if [ -n "$(docker image ls -f dangling=true -q | head -n1)" ]; then
    docker image prune -f --filter "dangling=true" || warn "Image prune failed"
  fi
  $DC build "${DOCKER_BUILD_FLAGS[@]}" expense-tracker || err "Docker build failed"
  echo "$CUR_HASH" > .last_image_jar_hash || true
else
  log "Skipping image build"
fi

# Restart container only if image rebuilt or container not running
CONTAINER_NAME=expense-tracker
IS_RUNNING=$($DC ps --services --status=running 2>/dev/null | grep -c "^${CONTAINER_NAME}$" || true)
if [ "$NEED_IMAGE" = true ] || [ "$IS_RUNNING" -eq 0 ]; then
  log "(Re)starting container..."
  $DC up -d --force-recreate $CONTAINER_NAME || err "Compose up failed"
else
  log "Container already running and image unchanged; skipping restart"
fi

if [ "$quiet_logs" = false ]; then
  log "Recent logs (tail 60):"
  $DC logs --tail=60 $CONTAINER_NAME || warn "Could not fetch logs"
fi

section "Done"
log "All operations completed successfully."
log "Storage: $(df -h / | awk 'NR==2 {print $2" used="$3" avail="$4}')"
log "RAM: $(free -h | awk '/^Mem:/ {print $2" avail="$7}')"
NEW_TEMP=$(get_temp_c)
log "Final Temp: ${NEW_TEMP}C (threshold ${MAX_TEMP_C}C)"
