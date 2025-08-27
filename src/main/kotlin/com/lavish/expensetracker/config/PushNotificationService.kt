package com.lavish.expensetracker.config

import com.google.firebase.messaging.*
import com.lavish.expensetracker.model.NotificationType
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class PushNotificationService {

    companion object {
        const val EXPENSE_CHANNEL_ID = "expense_notifications"
        const val FAMILY_CHANNEL_ID = "family_notifications"
        const val REMINDER_CHANNEL_ID = "reminder_notifications"
        const val GENERAL_CHANNEL_ID = "general_notifications"

        // TTLs (milliseconds) tailored per channel (Android): expense fast relevance, reminder longer
        private val TTL_EXPENSE: Long = Duration.ofHours(2).toMillis() // deliver quickly or drop
        private val TTL_FAMILY: Long = Duration.ofHours(6).toMillis()
        private val TTL_REMINDER: Long = Duration.ofDays(1).toMillis() // reminder valid for a day
        private val TTL_GENERAL: Long = Duration.ofHours(12).toMillis()
    }

    private fun highPriorityAndroidConfig(
        channelId: String,
        title: String?,
        body: String?,
        tag: String? = null,
        ttlMs: Long? = null,
        collapseKey: String? = null
    ): AndroidConfig =
        AndroidConfig.builder()
            .setPriority(AndroidConfig.Priority.HIGH)
            .apply { ttlMs?.let { setTtl(it) } }
            .apply { collapseKey?.let { setCollapseKey(it) } }
            .setNotification(
                AndroidNotification.builder()
                    .setChannelId(channelId)
                    .setTitle(title)
                    .setBody(body)
                    .apply { if (tag != null) setTag(tag) }
                    .setPriority(AndroidNotification.Priority.HIGH)
                    .build()
            )
            .build()

    // Public API: unified typed multicast sender
    fun sendTypedNotificationToMultiple(
        type: NotificationType,
        tokens: List<String>,
        title: String?,
        body: String?,
        data: Map<String, String?> = emptyMap(),
        tag: String? = null
    ): List<String> {
        val safeTokens = tokens.filter { it.isNotBlank() }
        if (safeTokens.isEmpty()) return emptyList()
        val channelId = channelIdForType(type)
        val ttl = ttlForChannel(channelId)
        val collapseKey = computeCollapseKey(type, tag, data, channelId)

        val builder = MulticastMessage.builder()
            .addAllTokens(safeTokens)
            .setAndroidConfig(highPriorityAndroidConfig(channelId, title, body, tag, ttl, collapseKey))
            .putData("type", type.name)
            .putData("channelId", channelId)

        // Inline data (filter nulls)
        data.filterValues { it != null }.forEach { (k, v) -> builder.putData(k, v!!) }

        title?.let { builder.putData("title", it) }
        body?.let { builder.putData("body", it) }

        val message = builder.build()
        return dispatchMulticast(message, safeTokens, "typed ${type.name}")
    }

    fun sendNotificationWithData(
        token: String?,
        title: String?,
        body: String?,
        data: Map<String, String>?,
        topic: String? = null
    ) {
        val resolvedTopic = topic ?: GENERAL_CHANNEL_ID
        val channelId = channelIdForTopic(resolvedTopic)
        val ttl = ttlForChannel(channelId)
        val collapseKey = computeCollapseKey(null, null, data ?: emptyMap<String, String>(), channelId, topic = resolvedTopic)
        val messageBuilder = Message.builder()
            .setAndroidConfig(highPriorityAndroidConfig(channelId, title, body, ttlMs = ttl, collapseKey = collapseKey))
            .apply { if (!token.isNullOrBlank()) setToken(token) else setTopic(resolvedTopic) }
            .setNotification(
                Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build()
            )
            .putData("channelId", channelId)

        data?.forEach { (key, value) -> messageBuilder.putData(key, value) }

        val message = messageBuilder.build()

        try {
            val response = FirebaseMessaging.getInstance().send(message)
            println("Successfully sent message with data: $response")
        } catch (e: FirebaseMessagingException) {
            e.printStackTrace()
        }
    }

    @Deprecated("Use sendNotificationToMultiple or sendTypedNotificationToMultiple")
    fun sendToMultipleDevices(tokens: MutableList<String?>?, title: String?, body: String?) {
        val safeTokens = tokens?.filterNotNull()?.filter { it.isNotBlank() } ?: emptyList()
        if (safeTokens.isEmpty()) return
        val message = MulticastMessage.builder()
            .addAllTokens(safeTokens)
            .setAndroidConfig(highPriorityAndroidConfig(GENERAL_CHANNEL_ID, title, body, ttlMs = ttlForChannel(GENERAL_CHANNEL_ID)))
            .setNotification(
                Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build()
            )
            .putData("channelId", GENERAL_CHANNEL_ID)
            .build()

        dispatchMulticast(message, safeTokens, "deprecated generic")
    }

    // Refactored to use unified typed sender
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
        return sendTypedNotificationToMultiple(type, tokens, title, body, data, tag = expenseId)
    }

    fun sendNotificationToMultiple(tokens: List<String>, title: String?, body: String?): List<String> =
        sendTypedNotificationToMultiple(NotificationType.GENERAL, tokens, title, body)

    fun sendProfileUpdateNotification(tokens: List<String>, userName: String?): List<String> {
        val displayName = userName ?: "User"
        val title = "Profile Updated"
        val body = "$displayName updated their profile"
        val data = mapOf("userName" to displayName)
        val type = NotificationType.PROFILE_UPDATED
        return sendTypedNotificationToMultiple(type, tokens, title, body, data)
    }

    private fun dispatchMulticast(message: MulticastMessage, tokens: List<String>, label: String): List<String> = try {
        val response = FirebaseMessaging.getInstance().sendEachForMulticast(message)
        println("Successfully sent $label notifications: ${'$'}{response.successCount}/${'$'}{tokens.size}")
        collectInvalidTokens(response, tokens)
    } catch (e: FirebaseMessagingException) {
        e.printStackTrace()
        emptyList()
    }

    private fun collectInvalidTokens(response: BatchResponse, tokens: List<String>): List<String> {
        val invalid = mutableListOf<String>()
        response.responses.forEachIndexed { index, sendResponse ->
            if (!sendResponse.isSuccessful) {
                val exception = sendResponse.exception
                if (exception is FirebaseMessagingException) {
                    when (exception.messagingErrorCode) {
                        MessagingErrorCode.UNREGISTERED, MessagingErrorCode.INVALID_ARGUMENT -> invalid.add(tokens[index])
                        else -> println("FCM Error for token ${'$'}{tokens[index]}: ${'$'}{exception.messagingErrorCode}")
                    }
                }
            }
        }
        return invalid
    }

    private fun channelIdForTopic(topic: String): String = when (topic) {
        EXPENSE_CHANNEL_ID -> EXPENSE_CHANNEL_ID
        FAMILY_CHANNEL_ID -> FAMILY_CHANNEL_ID
        REMINDER_CHANNEL_ID -> REMINDER_CHANNEL_ID
        GENERAL_CHANNEL_ID -> GENERAL_CHANNEL_ID
        else -> GENERAL_CHANNEL_ID
    }

    private fun channelIdForType(type: NotificationType): String = when (type) {
        NotificationType.EXPENSE_ADDED,
        NotificationType.EXPENSE_UPDATED,
        NotificationType.EXPENSE_DELETED -> EXPENSE_CHANNEL_ID

        NotificationType.FAMILY_EXPENSE_ADDED,
        NotificationType.FAMILY_EXPENSE_UPDATED,
        NotificationType.FAMILY_EXPENSE_DELETED,
        NotificationType.FAMILY_MEMBER_JOINED,
        NotificationType.FAMILY_MEMBER_LEFT,
        NotificationType.FAMILY_MEMBER_REMOVED,
        NotificationType.JOIN_FAMILY_INVITATION,
        NotificationType.JOIN_FAMILY_REQUEST,
        NotificationType.JOIN_FAMILY_INVITATION_REJECTED,
        NotificationType.JOIN_FAMILY_INVITATION_CANCELLED,
        NotificationType.JOIN_FAMILY_REQUEST_REJECTED,
        NotificationType.JOIN_FAMILY_INVITATION_ACCEPTED,
        NotificationType.JOIN_FAMILY_REQUEST_ACCEPTED -> FAMILY_CHANNEL_ID

        NotificationType.BUDGET_LIMIT_REACHED,
        NotificationType.PAYMENT_REMINDER,
        NotificationType.REMINDER -> REMINDER_CHANNEL_ID

        NotificationType.PROFILE_UPDATED,
        NotificationType.OTHER,
        NotificationType.GENERAL -> GENERAL_CHANNEL_ID
    }

    private fun ttlForChannel(channelId: String): Long = when (channelId) {
        EXPENSE_CHANNEL_ID -> TTL_EXPENSE
        FAMILY_CHANNEL_ID -> TTL_FAMILY
        REMINDER_CHANNEL_ID -> TTL_REMINDER
        GENERAL_CHANNEL_ID -> TTL_GENERAL
        else -> TTL_GENERAL
    }

    private fun computeCollapseKey(
        type: NotificationType?,
        tag: String?,
        data: Map<String, *>?,
        channelId: String,
        topic: String? = null
    ): String {
        // Priority for collapse key selection:
        // 1. Explicit tag (e.g., expenseId)
        // 2. Expense-specific id fields
        // 3. Family / reminder contextual fields
        // 4. Type name
        // 5. Channel id or topic
        val expenseId = (data?.get("expenseId") ?: data?.get("expense_id"))?.toString()?.takeIf { it.isNotBlank() }
        val familyId = (data?.get("familyId") ?: data?.get("family_id"))?.toString()?.takeIf { it.isNotBlank() }
        val base = when {
            !tag.isNullOrBlank() -> tag
            expenseId != null -> "exp_$expenseId"
            type != null && type.name.startsWith("FAMILY") && familyId != null -> "fam_" + familyId + '_' + type.name.lowercase()
            familyId != null -> "fam_$familyId"
            type != null -> type.name.lowercase()
            !topic.isNullOrBlank() -> topic
            else -> channelId
        }
        // FCM collapse key max length ~64; sanitize
        return base.lowercase().replace("[^a-z0-9_]+".toRegex(), "_").take(64)
    }
}