package com.lavish.expensetracker.service

import com.lavish.expensetracker.repository.ExpenseUserRepository
import com.lavish.expensetracker.repository.FamilyRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FamilyCleanupService(
    private val userRepository: ExpenseUserRepository,
    private val familyRepository: FamilyRepository
) {

    private val logger = LoggerFactory.getLogger(FamilyCleanupService::class.java)

    /**
     * Cron job that runs every 5 minutes to clean up orphaned family references
     * Cron expression: 0 * /5 * * * ? (seconds minutes hours day-of-month month day-of-week)
     */
    @Scheduled(cron = "0 */5 * * * ?")
    @Transactional
    fun cleanupOrphanedFamilyReferences() {
        logger.info("Starting cleanup of orphaned family references")

        try {
            // Find all users who have a familyId set
            val usersWithFamilyId = userRepository.findAll().filter { it.familyId != null }
            logger.info("Found ${usersWithFamilyId.size} users with familyId set")

            var cleanedUpCount = 0

            for (user in usersWithFamilyId) {
                val familyId = user.familyId!!

                // Check if the family exists in the database
                val familyExists = familyRepository.existsById(familyId)

                if (!familyExists) {
                    logger.warn("Found orphaned family reference for user ${user.id} (${user.email}). FamilyId: $familyId does not exist in database")

                    // Create a new user instance with null familyId
                    val updatedUser = user.copy(
                        familyId = null,
                        updatedAt = System.currentTimeMillis()
                    )

                    // Save the updated user
                    userRepository.save(updatedUser)
                    cleanedUpCount++

                    logger.info("Cleaned up orphaned family reference for user ${user.id} (${user.email})")
                }
            }

            logger.info("Cleanup completed. Removed orphaned family references from $cleanedUpCount users")

        } catch (exception: Exception) {
            logger.error("Error occurred during family cleanup process", exception)
        }
    }

    /**
     * Manual cleanup method that can be called programmatically
     * Returns the number of users that were cleaned up
     */
    @Transactional
    fun performManualCleanup(): Int {
        logger.info("Starting manual cleanup of orphaned family references")

        val usersWithFamilyId = userRepository.findAll().filter { it.familyId != null }
        var cleanedUpCount = 0

        for (user in usersWithFamilyId) {
            val familyId = user.familyId!!

            if (!familyRepository.existsById(familyId)) {
                val updatedUser = user.copy(
                    familyId = null,
                    updatedAt = System.currentTimeMillis()
                )

                userRepository.save(updatedUser)
                cleanedUpCount++

                logger.info("Manually cleaned up orphaned family reference for user ${user.id} (${user.email})")
            }
        }

        logger.info("Manual cleanup completed. Removed orphaned family references from $cleanedUpCount users")
        return cleanedUpCount
    }
}
