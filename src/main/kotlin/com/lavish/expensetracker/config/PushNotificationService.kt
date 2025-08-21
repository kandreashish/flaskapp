package com.lavish.expensetracker.config

import com.google.firebase.messaging.*
import com.lavish.expensetracker.model.NotificationType
import org.springframework.stereotype.Service

@Service
class PushNotificationService {
    fun sendNotification(
        token: String?,
        title: String?,
        body: String?,
        type: NotificationType = NotificationType.OTHER
    ) {
        val message = Message.builder()
            .setToken(token)
            .setNotification(
                Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build()
            ).putData("type", type.name)
            .build()

        try {
            val response = FirebaseMessaging.getInstance().send(message)
            println("Successfully sent message: $response")
        } catch (e: FirebaseMessagingException) {
            e.printStackTrace()
        }
    }

    // Data-only single expense notification
    fun sendExpenseNotification(token: String?, amount: String?, description: String, name: String?) {
        val message = Message.builder()
            .setToken(token)
            .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
            .putData("type", "EXPENSE_ADDED")
            .putData("title", "New Expense Added")
            .putData("body", description)
            .putData("amount", amount ?: "₹0.00")
            .putData("senderName", name ?: "Unknown")
            .build()

        try {
            val response = FirebaseMessaging.getInstance().send(message)
            println("Successfully sent expense notification (data-only): $response")
        } catch (e: FirebaseMessagingException) {
            e.printStackTrace()
        }
    }

    fun sendNotificationWithData(token: String?, title: String?, body: String?, data: Map<String, String>?) {
        val messageBuilder = Message.builder()
            .setToken(token)
            .setNotification(
                Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build()
            )

        data?.forEach { (key, value) ->
            messageBuilder.putData(key, value)
        }

        val message = messageBuilder.build()

        try {
            val response = FirebaseMessaging.getInstance().send(message)
            println("Successfully sent message with data: $response")
        } catch (e: FirebaseMessagingException) {
            e.printStackTrace()
        }
    }

    @Deprecated("Use sendNotificationToMultiple instead")
    fun sendToMultipleDevices(tokens: MutableList<String?>?, title: String?, body: String?) {
        val message = MulticastMessage.builder()
            .addAllTokens(tokens)
            .setNotification(
                Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build()
            )
            .build()

        try {
            val response = FirebaseMessaging.getInstance().sendEachForMulticast(message)
            println("Successfully sent messages: " + response.getSuccessCount())
        } catch (e: FirebaseMessagingException) {
            e.printStackTrace()
        }
    }

    // Data-only multicast (legacy) expense notification
    fun sendExpenseNotificationToMultiple(tokens: MutableList<String?>?, amount: String?) {
        val message = MulticastMessage.builder()
            .addAllTokens(tokens)
            .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
            .putData("type", "EXPENSE_ADDED")
            .putData("title", "New Expense Added")
            .putData("body", "Someone added a new expense")
            .putData("amount", amount ?: "₹0.00")
            .build()

        try {
            val response = FirebaseMessaging.getInstance().sendEachForMulticast(message)
            println("Successfully sent expense notifications (data-only): " + response.getSuccessCount())
        } catch (e: FirebaseMessagingException) {
            e.printStackTrace()
        }
    }

    // Data-only multicast expense notification with full metadata
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
        if (tokens.isEmpty()) return emptyList()

        val message = MulticastMessage.builder()
            .addAllTokens(tokens)
            .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
            .putData("type", type.name)
            .putData("title", title)
            .putData("body", body) // use provided body string
            .putData("description", description) // optional raw description
            .putData("amount", amount ?: "₹0.00")
            .putData("senderId", userId ?: "unknown")
            .putData("expenseId", expenseId)
            .build()

        return try {
            val response = FirebaseMessaging.getInstance().sendEachForMulticast(message)
            println("Successfully sent expense notifications (data-only): ${response.successCount}/${tokens.size}")

            val invalidTokens = mutableListOf<String>()
            response.responses.forEachIndexed { index, sendResponse ->
                if (!sendResponse.isSuccessful) {
                    val exception = sendResponse.exception
                    if (exception is FirebaseMessagingException) {
                        when (exception.messagingErrorCode) {
                            MessagingErrorCode.UNREGISTERED,
                            MessagingErrorCode.INVALID_ARGUMENT -> {
                                invalidTokens.add(tokens[index])
                            }
                            else -> println("FCM Error for token ${tokens[index]}: ${exception.messagingErrorCode}")
                        }
                    }
                }
            }
            invalidTokens
        } catch (e: FirebaseMessagingException) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun sendNotificationToMultiple(tokens: List<String>, title: String?, body: String?): List<String> {
        if (tokens.isEmpty()) return emptyList()

        val message = MulticastMessage.builder()
            .addAllTokens(tokens)
            .setNotification(
                Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build()
            )
            .build()

        return try {
            val response = FirebaseMessaging.getInstance().sendEachForMulticast(message)
            println("Successfully sent notifications: ${response.successCount}/${tokens.size}")

            val invalidTokens = mutableListOf<String>()
            response.responses.forEachIndexed { index, sendResponse ->
                if (!sendResponse.isSuccessful) {
                    val exception = sendResponse.exception
                    if (exception is FirebaseMessagingException) {
                        when (exception.messagingErrorCode) {
                            MessagingErrorCode.UNREGISTERED,
                            MessagingErrorCode.INVALID_ARGUMENT -> invalidTokens.add(tokens[index])
                            else -> println("FCM Error for token ${tokens[index]}: ${exception.messagingErrorCode}")
                        }
                    }
                }
            }
            invalidTokens
        } catch (e: FirebaseMessagingException) {
            e.printStackTrace()
            emptyList()
        }
    }

    // High priority profile update notification
    fun sendProfileUpdateNotification(tokens: List<String>, userName: String?): List<String> {
        if (tokens.isEmpty()) return emptyList()

        val displayName = userName ?: "User"
        val message = MulticastMessage.builder()
            .addAllTokens(tokens)
            .setAndroidConfig(
                AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setNotification(
                        AndroidNotification.builder()
                            .setTitle("Profile Updated")
                            .setBody("$displayName updated their profile")
                            .setPriority(AndroidNotification.Priority.HIGH)
                            .setChannelId("profile_updates")
                            .build()
                    )
                    .build()
            )
            .putData("type", NotificationType.PROFILE_UPDATED.name)
            .putData("title", "Profile Updated")
            .putData("body", "$displayName updated their profile")
            .putData("userName", displayName)
            .build()

        return try {
            val response = FirebaseMessaging.getInstance().sendEachForMulticast(message)
            println("Successfully sent profile update notifications: ${response.successCount}/${tokens.size}")

            val invalidTokens = mutableListOf<String>()
            response.responses.forEachIndexed { index, sendResponse ->
                if (!sendResponse.isSuccessful) {
                    val exception = sendResponse.exception
                    if (exception is FirebaseMessagingException) {
                        when (exception.messagingErrorCode) {
                            MessagingErrorCode.UNREGISTERED,
                            MessagingErrorCode.INVALID_ARGUMENT -> invalidTokens.add(tokens[index])
                            else -> println("FCM Error for token ${tokens[index]}: ${exception.messagingErrorCode}")
                        }
                    }
                }
            }
            invalidTokens
        } catch (e: FirebaseMessagingException) {
            e.printStackTrace()
            emptyList()
        }
    }
}