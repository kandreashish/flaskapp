package com.lavish.expensetracker.health

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import java.io.File

/**
 * Custom health indicator to report basic system conditions relevant for a Raspberry Pi.
 * Currently validates free disk space against a configurable threshold (in MB).
 */
@Component
class SystemHealthIndicator(
    @Value("\${custom.health.min-disk-free-mb:100}") private val minDiskFreeMb: Long
) : HealthIndicator {

    override fun health(): Health = try {
        val root = File("/")
        val freeBytes = root.usableSpace
        val freeMb = freeBytes / 1024 / 1024
        val statusBuilder = if (freeMb < minDiskFreeMb) {
            Health.down()
                .withDetail("reason", "Low disk space")
        } else {
            Health.up()
        }
        statusBuilder
            .withDetail("diskFreeMb", freeMb)
            .withDetail("minRequiredMb", minDiskFreeMb)
            .build()
    } catch (ex: Exception) {
        Health.unknown().withException(ex).build()
    }
}

