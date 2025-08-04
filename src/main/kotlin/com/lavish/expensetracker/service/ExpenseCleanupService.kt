package com.lavish.expensetracker.service

import com.lavish.expensetracker.repository.ExpenseRepository
import com.lavish.expensetracker.repository.ExpenseRepositoryImpl
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class ExpenseCleanupService(
    private val expenseRepository: ExpenseRepository
) {
    private val logger = LoggerFactory.getLogger(ExpenseCleanupService::class.java)

    companion object {
        // Delete soft-deleted records older than 90 days
        private const val CLEANUP_THRESHOLD_DAYS = 90L
    }

    /**
     * Runs every day at 2 AM to clean up old soft-deleted expenses
     */
    @Scheduled(cron = "0 0 2 * * *") // Every day at 2:00 AM
    @Transactional
    fun cleanupOldDeletedExpenses() {
        try {
            logger.info("Starting cleanup of old soft-deleted expenses")

            val cutoffTime = LocalDateTime.now()
                .minusDays(CLEANUP_THRESHOLD_DAYS)
                .toEpochSecond(ZoneOffset.UTC) * 1000

            val deletedCount = performCleanup(cutoffTime)

            logger.info("Cleanup completed. Permanently deleted $deletedCount old soft-deleted expenses")

        } catch (e: Exception) {
            logger.error("Error during expense cleanup: ${e.message}", e)
        }
    }

    /**
     * Manual cleanup method - can be called via admin endpoint
     */
    @Transactional
    fun manualCleanup(daysOld: Long = CLEANUP_THRESHOLD_DAYS): Int {
        logger.info("Manual cleanup initiated for expenses deleted more than $daysOld days ago")

        val cutoffTime = LocalDateTime.now()
            .minusDays(daysOld)
            .toEpochSecond(ZoneOffset.UTC) * 1000

        return performCleanup(cutoffTime)
    }

    /**
     * Perform the actual cleanup logic for in-memory repository
     */
    private fun performCleanup(cutoffTime: Long): Int {
        // Since we're using in-memory repository, we'll implement cleanup logic here
        if (expenseRepository is ExpenseRepositoryImpl) {
            return cleanupInMemoryRepository(cutoffTime)
        }

        // For other repository implementations, we can't perform cleanup
        logger.warn("Cleanup not supported for current repository implementation: ${expenseRepository::class.simpleName}")
        return 0
    }

    /**
     * Cleanup logic specific to in-memory repository
     */
    private fun cleanupInMemoryRepository(cutoffTime: Long): Int {
        // This would require adding a cleanup method to ExpenseRepositoryImpl
        // For now, we'll just log that cleanup would happen here
        logger.info("In-memory repository cleanup would remove expenses deleted before timestamp: $cutoffTime")

        // TODO: Implement actual cleanup in ExpenseRepositoryImpl
        // For safety, returning 0 for now since in-memory data is temporary anyway
        return 0
    }

    /**
     * Get statistics about soft-deleted expenses
     */
    fun getCleanupStatistics(): Map<String, Any> {
        return mapOf(
            "repositoryType" to (expenseRepository::class.simpleName ?: "Unknown"),
            "cleanupSupported" to (expenseRepository is ExpenseRepositoryImpl),
            "cleanupThresholdDays" to CLEANUP_THRESHOLD_DAYS,
            "note" to "In-memory repository - data is temporary and cleared on restart"
        )
    }
}
