package com.lavish.expensetracker.service

import com.lavish.expensetracker.repository.ExpenseJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

@Service
class ExpenseCleanupService(
    private val expenseRepository: ExpenseJpaRepository
) {
    private val logger = LoggerFactory.getLogger(ExpenseCleanupService::class.java)

    companion object {
        // Delete soft-deleted records older than 90 days
        private const val CLEANUP_THRESHOLD_DAYS = 90L
        private const val BATCH_SIZE = 1000
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

            val deletedCount = expenseRepository.deleteOldSoftDeletedExpenses(cutoffTime, BATCH_SIZE)

            logger.info("Cleanup completed. Permanently deleted $deletedCount old soft-deleted expenses")

            // Log cleanup statistics
            val remainingSoftDeleted = expenseRepository.countSoftDeletedExpenses()
            logger.info("Remaining soft-deleted expenses: $remainingSoftDeleted")

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

        return expenseRepository.deleteOldSoftDeletedExpenses(cutoffTime, BATCH_SIZE)
    }

    /**
     * Get statistics about soft-deleted expenses
     */
    fun getCleanupStatistics(): Map<String, Any> {
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - TimeUnit.DAYS.toMillis(30)
        val ninetyDaysAgo = now - TimeUnit.DAYS.toMillis(90)

        return mapOf(
            "totalSoftDeleted" to expenseRepository.countSoftDeletedExpenses(),
            "deletedLast30Days" to expenseRepository.countSoftDeletedExpensesSince(thirtyDaysAgo),
            "deletedOlderThan90Days" to expenseRepository.countSoftDeletedExpensesOlderThan(ninetyDaysAgo),
            "cleanupThresholdDays" to CLEANUP_THRESHOLD_DAYS
        )
    }
}
