package com.lavish.expensetracker.service

import com.lavish.expensetracker.config.PushNotificationService
import com.lavish.expensetracker.model.NotificationType
import org.springframework.stereotype.Service

@Service
class ExpenseNotificationService(
    private val pushNotificationService: PushNotificationService
) {
    /**
     * Sends an expense related notification to multiple device tokens.
     * Returns list of invalid tokens (to be cleaned up by caller).
     */
    fun sendExpenseNotificationToMultiple(
        title: String,
        body: String,
        type: NotificationType,
        tokens: List<String>,
        amount: String?,
        description: String,
        userId: String? = null,
        expenseId: String
    ): List<String> {
        val data = mapOf(
            "description" to description,
            "amount" to (amount ?: "₹0.00"),
            "senderId" to (userId ?: "unknown"),
            "expenseId" to expenseId
        )
        return pushNotificationService.sendTypedNotificationToMultiple(
            type = type,
            tokens = tokens,
            title = title,
            body = body,
            data = data,
            tag = expenseId
        )
    }

    fun sendNotificationToMultiple(fcmTokens: List<String>, title: String, body: String,type: NotificationType): List<String> {
       return pushNotificationService.sendTypedNotificationToMultiple(
            type = type,
            tokens = fcmTokens,
            title = title,
            body = body
        )
    }
}

