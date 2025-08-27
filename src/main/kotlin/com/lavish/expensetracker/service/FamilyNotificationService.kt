package com.lavish.expensetracker.service

import com.lavish.expensetracker.config.PushNotificationService
import com.lavish.expensetracker.model.NotificationType
import org.springframework.stereotype.Service

@Service
class FamilyNotificationService(
    private val pushNotificationService: PushNotificationService
) {
    /**
     * Send a family related notification to a single device token.
     */
    fun sendToSingle(
        token: String?,
        type: NotificationType,
        title: String?,
        body: String?,
        data: Map<String, String?> = emptyMap(),
        tag: String? = null
    ) {
        if (token.isNullOrBlank()) return
        pushNotificationService.sendTypedNotificationToMultiple(
            type = type,
            tokens = listOf(token),
            title = title,
            body = body,
            data = data,
            tag = tag
        )
    }

    /**
     * Send a family related notification to multiple device tokens.
     * Returns list of invalid tokens for cleanup.
     */
    fun sendToMultiple(
        type: NotificationType,
        tokens: List<String>,
        title: String?,
        body: String?,
        data: Map<String, String?> = emptyMap(),
        tag: String? = null
    ): List<String> {
        return pushNotificationService.sendTypedNotificationToMultiple(
            type = type,
            tokens = tokens,
            title = title,
            body = body,
            data = data,
            tag = tag
        )
    }
}

