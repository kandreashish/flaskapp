package com.example.expensetracker.config

import com.google.firebase.messaging.*
import org.springframework.stereotype.Service

@Service
class PushNotificationService {
    fun sendNotification(token: String?, title: String?, body: String?) {
        val message = Message.builder()
            .setToken(token)
            .setNotification(
                Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build()
            )
            .build()

        try {
            val response = FirebaseMessaging.getInstance().send(message)
            println("Successfully sent message: $response")
        } catch (e: FirebaseMessagingException) {
            e.printStackTrace()
        }
    }

    fun sendExpenseNotification(token: String?, amount: String?, description: String, name: String?) {
        val message = Message.builder()
            .setToken(token)
            .setNotification(
                Notification.builder()
                    .setTitle("New Expense Added")
                    .setBody("$name added a new expense")
                    .build()
            )
            .putData("type", "expense")
            .putData("title", "New Expense Added")
            .putData("body", description)
            .putData("amount", amount ?: "$0.00")
            .build()

        try {
            val response = FirebaseMessaging.getInstance().send(message)
            println("Successfully sent expense notification: $response")
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

    @Deprecated("Use sendExpenseNotificationToMultiple instead")
    fun sendExpenseNotificationToMultiple(tokens: MutableList<String?>?, amount: String?) {
        val message = MulticastMessage.builder()
            .addAllTokens(tokens)
            .setNotification(
                Notification.builder()
                    .setTitle("New Expense Added")
                    .setBody("Someone added a new expense")
                    .build()
            )
            .putData("type", "expense")
            .putData("title", "New Expense Added")
            .putData("body", "Someone added a new expense")
            .putData("amount", amount ?: "$0.00")
            .build()

        try {
            val response = FirebaseMessaging.getInstance().sendEachForMulticast(message)
            println("Successfully sent expense notifications: " + response.getSuccessCount())
        } catch (e: FirebaseMessagingException) {
            e.printStackTrace()
        }
    }

    fun sendExpenseNotificationToMultiple(tokens: List<String>, amount: String?, description: String, name: String?): List<String> {
        if (tokens.isEmpty()) return emptyList()

        val message = MulticastMessage.builder()
            .addAllTokens(tokens)
            .setNotification(
                Notification.builder()
                    .setTitle("New Expense Added")
                    .setBody("$name added a new expense")
                    .build()
            )
            .putData("type", "expense")
            .putData("title", "New Expense Added")
            .putData("body", description)
            .putData("amount", amount ?: "$0.00")
            .build()

        return try {
            val response = FirebaseMessaging.getInstance().sendEachForMulticast(message)
            println("Successfully sent expense notifications: ${response.successCount}/${tokens.size}")

            // Return list of invalid tokens for cleanup
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
                            else -> {
                                // Log other errors but don't remove tokens
                                println("FCM Error for token ${tokens[index]}: ${exception.messagingErrorCode}")
                            }
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

            // Return list of invalid tokens for cleanup
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
                            else -> {
                                // Log other errors but don't remove tokens
                                println("FCM Error for token ${tokens[index]}: ${exception.messagingErrorCode}")
                            }
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