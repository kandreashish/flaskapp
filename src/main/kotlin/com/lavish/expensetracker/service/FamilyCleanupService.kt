package com.lavish.expensetracker.service

import com.lavish.expensetracker.repository.ExpenseRepository
import com.lavish.expensetracker.repository.ExpenseUserRepository
import com.lavish.expensetracker.repository.FamilyRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FamilyCleanupService(
    private val userRepository: ExpenseUserRepository,
    private val expenseRepository: ExpenseRepository,
    private val familyRepository: FamilyRepository
) {

    private val logger = LoggerFactory.getLogger(FamilyCleanupService::class.java)

    // --- Runtime metrics for observability ---
    @Volatile private var lastRunTimestamp: Long? = null
    @Volatile private var lastRunProcessedUsers: Int = 0
    @Volatile private var lastRunOrphansCleaned: Int = 0
    @Volatile private var lastRunDurationMs: Long = 0
    @Volatile private var lastRunErrors: Int = 0

    /**
     * Cron job that runs every 5 minutes to clean up orphaned family references.
     * Cron expression fields: second minute hour day-of-month month day-of-week
     */
    @Scheduled(cron = "0 */5 * * * ?")
    @Transactional
    fun cleanupOrphanedFamilyReferences() {
        val start = System.currentTimeMillis()
        logger.info("Starting cleanup of orphaned family references")

        var cleanedUpCount = 0
        var processed = 0
        var errors = 0

        try {
            // Find all users who have a familyId set
            val usersWithFamilyId = userRepository.findAll().filter { it.familyId != null }
            logger.info("Found ${usersWithFamilyId.size} users with familyId set")

            for (user in usersWithFamilyId) {
                processed++
                val familyId = user.familyId!!

                val familyExists = try {
                    familyRepository.existsById(familyId)
                } catch (e: Exception) {
                    errors++
                    logger.warn("Failed existsById for familyId=$familyId: ${e.message}")
                    continue
                }

                if (!familyExists) {
                    logger.warn("Found orphaned family reference for user ${user.id} (${user.email}). familyId=$familyId not present")

                    val deletedExpensesCount = safeDeleteExpenses(familyId)

                    val updatedUser = user.copy(
                        familyId = null,
                        updatedAt = System.currentTimeMillis()
                    )
                    try {
                        userRepository.save(updatedUser)
                        cleanedUpCount++
                        logger.info("Cleaned orphan for user ${user.id}; deletedExpenses=$deletedExpensesCount familyId=$familyId")
                    } catch (e: Exception) {
                        errors++
                        logger.error("Failed to save user ${user.id} when clearing orphan familyId=$familyId: ${e.message}", e)
                    }
                } else {
                    // Explicit log at debug so operators can enable if needed
                    logger.debug("Family $familyId exists; no cleanup needed for user ${user.id}")
                }
            }
        } catch (exception: Exception) {
            errors++
            logger.error("Error occurred during family cleanup process: ${exception.message}", exception)
        } finally {
            lastRunTimestamp = System.currentTimeMillis()
            lastRunProcessedUsers = processed
            lastRunOrphansCleaned = cleanedUpCount
            lastRunDurationMs = lastRunTimestamp!! - start
            lastRunErrors = errors
            logger.info("Cleanup completed. processed=$processed orphansCleaned=$cleanedUpCount errors=$errors durationMs=$lastRunDurationMs")
        }
    }

    private fun safeDeleteExpenses(familyId: String): Int = try {
        expenseRepository.deleteByFamilyId(familyId)
    } catch (e: Exception) {
        lastRunErrors += 1
        logger.error("Failed deleting expenses for orphan familyId=$familyId: ${e.message}", e)
        0
    }

    /**
     * Manual cleanup method that can be called programmatically.
     * Returns number of orphaned user records cleaned.
     */
    @Transactional
    fun performManualCleanup(): Int {
        logger.info("Starting manual cleanup of orphaned family references")
        val start = System.currentTimeMillis()
        var cleanedUpCount = 0
        val usersWithFamilyId = userRepository.findAll().filter { it.familyId != null }

        for (user in usersWithFamilyId) {
            val familyId = user.familyId!!
            if (!familyRepository.existsById(familyId)) {
                safeDeleteExpenses(familyId)
                val updatedUser = user.copy(
                    familyId = null,
                    updatedAt = System.currentTimeMillis()
                )
                userRepository.save(updatedUser)
                cleanedUpCount++
                logger.info("Manually cleaned orphan for user ${user.id} familyId=$familyId")
            }
        }
        lastRunTimestamp = System.currentTimeMillis()
        lastRunProcessedUsers = usersWithFamilyId.size
        lastRunOrphansCleaned = cleanedUpCount
        lastRunDurationMs = lastRunTimestamp!! - start
        logger.info("Manual cleanup completed. processed=${usersWithFamilyId.size} orphansCleaned=$cleanedUpCount durationMs=$lastRunDurationMs")
        return cleanedUpCount
    }

    fun getLastRunStatus(): Map<String, Any?> = mapOf(
        "lastRunTimestamp" to lastRunTimestamp,
        "lastRunProcessedUsers" to lastRunProcessedUsers,
        "lastRunOrphansCleaned" to lastRunOrphansCleaned,
        "lastRunDurationMs" to lastRunDurationMs,
        "lastRunErrors" to lastRunErrors
    )
}
