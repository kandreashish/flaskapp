package com.lavish.expensetracker.service

import com.lavish.expensetracker.config.PushNotificationService
import com.lavish.expensetracker.model.NotificationType
import org.springframework.stereotype.Service

@Service
class FamilyNotificationService(
    private val pushNotificationService: PushNotificationService
) {
    companion object {
        private const val DEFAULT_SOUND = "default"
    }
    /**
     * Send a family related data-only notification to a single device token.
     */
    fun sendToSingle(
        token: String?,
        type: NotificationType,
        title: String?,
        body: String?,
        data: Map<String, String?> = emptyMap(),
        tag: String? = null,
        sound: String = DEFAULT_SOUND
    ) {
        if (token.isNullOrBlank()) return
        pushNotificationService.sendDataOnlyTypedNotificationToMultiple(
            type = type,
            tokens = listOf(token),
            title = title,
            body = body,
            data = data,
            tag = tag,
            includeSoundFlag = true,
            sound = sound
        )
    }

    /**
     * Send a family related data-only notification to multiple device tokens.
     * Returns list of invalid tokens for cleanup.
     */
    fun sendToMultiple(
        type: NotificationType,
        tokens: List<String>,
        title: String?,
        body: String?,
        data: Map<String, String?> = emptyMap(),
        tag: String? = null,
        sound: String = DEFAULT_SOUND
    ): List<String> {
        return pushNotificationService.sendDataOnlyTypedNotificationToMultiple(
            type = type,
            tokens = tokens,
            title = title,
            body = body,
            data = data,
            tag = tag,
            includeSoundFlag = true,
            sound = sound
        )
    }
}
