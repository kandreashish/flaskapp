package com.lavish.expensetracker.service

import com.lavish.expensetracker.controller.dto.JoinRequestActionRequest
import com.lavish.expensetracker.controller.dto.JoinFamilyRequest
import com.lavish.expensetracker.model.*
import com.lavish.expensetracker.repository.ExpenseUserRepository
import com.lavish.expensetracker.repository.FamilyRepository
import com.lavish.expensetracker.repository.JoinRequestRepository
import com.lavish.expensetracker.repository.NotificationRepository
import com.lavish.expensetracker.util.AuthUtil
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

/**
 * Unit tests for join request throttling logic in FamilyApplicationService.
 * Focus: max 5 attempts (initial + 4 resends) in rolling 7-day window, excluding CANCELLED attempts.
 */
class FamilyApplicationServiceJoinRequestThrottleTests {

    private lateinit var familyRepository: FamilyRepository
    private lateinit var userRepository: ExpenseUserRepository
    private lateinit var notificationRepository: NotificationRepository
    private lateinit var authUtil: AuthUtil
    private lateinit var notificationSvc: FamilyNotificationService
    private lateinit var joinRequestRepository: JoinRequestRepository
    private lateinit var service: FamilyApplicationService

    private val userId = "user-1"
    private val familyAlias = "ABCDE1" // assume valid alias length
    private val familyId = "fam-1"

    private val joinRequests = mutableListOf<JoinRequest>()
    private val users = mutableMapOf<String, ExpenseUser>()
    private val families = mutableMapOf<String, Family>()

    @BeforeEach
    fun setup() {
        joinRequests.clear()
        users.clear()
        families.clear()

        authUtil = mock()
        whenever(authUtil.getCurrentUserId()).thenReturn(userId)

        // In-memory user (requires aliasName + firebaseUid per model)
        val user = ExpenseUser(
            id = userId,
            name = "Tester",
            email = "test@example.com",
            aliasName = "TEST01",
            firebaseUid = "firebase-test-uid",
            familyId = null,
            fcmToken = null
        )
        users[userId] = user

        // In-memory family
        val family = Family(
            familyId = familyId,
            headId = "head-1",
            name = "Family Name",
            aliasName = familyAlias,
            maxSize = 10,
            membersIds = mutableListOf(),
            pendingMemberEmails = mutableListOf(),
            pendingJoinRequests = mutableListOf(),
            updatedAt = System.currentTimeMillis()
        )
        families[familyId] = family

        // Mock repositories with simple in-memory behavior used by service
        userRepository = mock()
        whenever(userRepository.findById(userId)).thenAnswer { Optional.ofNullable(users[userId]) }
        whenever(userRepository.findAll()).thenReturn(users.values.toList())
        whenever(userRepository.save(any())).thenAnswer { invocation ->
            val saved = invocation.arguments[0] as ExpenseUser
            users[saved.id] = saved
            saved
        }

        familyRepository = mock()
        whenever(familyRepository.findByAliasName(familyAlias)).thenReturn(families[familyId])
        whenever(familyRepository.findById(familyId)).thenAnswer { Optional.ofNullable(families[familyId]) }
        whenever(familyRepository.save(any())).thenAnswer { invocation ->
            val saved = invocation.arguments[0] as Family
            families[saved.familyId] = saved
            saved
        }
        whenever(familyRepository.findAll()).thenReturn(families.values.toList())

        notificationRepository = mock()
        notificationSvc = mock()

        joinRequestRepository = mock()
        // save
        whenever(joinRequestRepository.save(any())).thenAnswer { invocation ->
            val jr = invocation.arguments[0] as JoinRequest
            // replace if exists
            val idx = joinRequests.indexOfFirst { it.id == jr.id }
            if (idx >= 0) joinRequests[idx] = jr else joinRequests.add(jr)
            jr
        }
        // queries
        whenever(joinRequestRepository.findByRequesterIdAndFamilyIdOrderByCreatedAtDesc(any(), any())).thenAnswer { invocation ->
            val reqId = invocation.arguments[0] as String
            val famId = invocation.arguments[1] as String
            joinRequests.filter { it.requesterId == reqId && it.familyId == famId }
                .sortedByDescending { it.createdAt }
        }
        whenever(joinRequestRepository.findByRequesterIdAndStatus(any(), any())).thenAnswer { invocation ->
            val reqId = invocation.arguments[0] as String
            val status = invocation.arguments[1] as JoinRequestStatus
            joinRequests.filter { it.requesterId == reqId && it.status == status }
        }
        whenever(joinRequestRepository.findByRequesterIdAndFamilyIdAndStatus(any(), any(), any())).thenAnswer { invocation ->
            val reqId = invocation.arguments[0] as String
            val famId = invocation.arguments[1] as String
            val status = invocation.arguments[2] as JoinRequestStatus
            joinRequests.firstOrNull { it.requesterId == reqId && it.familyId == famId && it.status == status }
        }
        whenever(joinRequestRepository.findByRequesterId(any())).thenAnswer { invocation ->
            val reqId = invocation.arguments[0] as String
            joinRequests.filter { it.requesterId == reqId }
        }
        whenever(joinRequestRepository.findByFamilyIdAndStatus(any(), any())).thenAnswer { invocation ->
            val famId = invocation.arguments[0] as String
            val status = invocation.arguments[1] as JoinRequestStatus
            joinRequests.filter { it.familyId == famId && it.status == status }
        }
        whenever(joinRequestRepository.findById(any<String>())).thenAnswer { invocation ->
            val id = invocation.arguments[0] as String
            Optional.ofNullable(joinRequests.firstOrNull { it.id == id })
        }

        service = FamilyApplicationService(
            familyRepository,
            userRepository,
            notificationRepository,
            authUtil,
            notificationSvc,
            joinRequestRepository
        )
    }

    @Test
    fun `allows up to 5 attempts then blocks with MAX_RETRIES`() {
        // 1st attempt - requestToJoinFamily
        val resp1 = service.requestToJoinFamily(JoinFamilyRequest(aliasName = familyAlias, notificationId = null))
        assertEquals(200, resp1.statusCode.value())
        repeat(4) { // 4 resends to reach 5 total
            val resp = service.resendJoinRequest(JoinRequestActionRequest(aliasName = familyAlias, message = null))
            assertEquals(200, resp.statusCode.value(), "Resend ${it + 1} should succeed")
        }
        // 6th attempt should fail
        val blockResp = service.resendJoinRequest(JoinRequestActionRequest(aliasName = familyAlias, message = null))
        assertEquals(409, blockResp.statusCode.value())
        val body = blockResp.body as Map<*, *>
        assertEquals("MAX_RETRIES", body["reason"]) // unified reason
        assertEquals("Max retries over. Ask family owner to send", body["message"])
        // ensure only latest pending + previous rejected
        val pendingCount = joinRequests.count { it.status == JoinRequestStatus.PENDING }
        assertEquals(1, pendingCount)
        val rejectedCount = joinRequests.count { it.status == JoinRequestStatus.REJECTED }
        assertEquals(4, rejectedCount)
    }

    @Test
    fun `cancelled attempts do not count toward limit`() {
        // Create 4 cancelled attempts manually
        repeat(4) { idx ->
            val jr = JoinRequest(
                id = "c$idx",
                requesterId = userId,
                familyId = familyId,
                message = null,
                status = JoinRequestStatus.CANCELLED,
                createdAt = System.currentTimeMillis() - 1000 * (idx + 1),
                updatedAt = System.currentTimeMillis() - 1000 * (idx + 1)
            )
            joinRequests.add(jr)
        }
        // Now send 5 non-cancelled attempts => should allow all 5
        val first = service.requestToJoinFamily(JoinFamilyRequest(aliasName = familyAlias, notificationId = null))
        assertEquals(200, first.statusCode.value())
        repeat(4) { // 4 resends
            val resp = service.resendJoinRequest(JoinRequestActionRequest(aliasName = familyAlias, message = null))
            assertEquals(200, resp.statusCode.value())
        }
        // Next should block (5 counted already)
        val blocked = service.resendJoinRequest(JoinRequestActionRequest(aliasName = familyAlias, message = null))
        assertEquals(409, blocked.statusCode.value())
    }

    @Test
    fun `requests outside 7 day window are ignored for counting`() {
        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
        val oldTime = System.currentTimeMillis() - sevenDaysMs - 2000
        // 5 old attempts (would have hit limit if inside window)
        repeat(5) { idx ->
            joinRequests.add(
                JoinRequest(
                    id = "old$idx",
                    requesterId = userId,
                    familyId = familyId,
                    message = null,
                    status = JoinRequestStatus.REJECTED, // counted status but outside window
                    createdAt = oldTime - idx,
                    updatedAt = oldTime - idx
                )
            )
        }
        // New attempt should succeed because all old attempts are outside window
        val resp = service.requestToJoinFamily(JoinFamilyRequest(aliasName = familyAlias, notificationId = null))
        assertEquals(200, resp.statusCode.value())
    }

    @Test
    fun `getOwnPendingJoinRequests returns only latest pending per family`() {
        // Add two pending requests manually for same family with different createdAt
        val older = JoinRequest(
            id = "r1",
            requesterId = userId,
            familyId = familyId,
            message = null,
            status = JoinRequestStatus.PENDING,
            createdAt = System.currentTimeMillis() - 5000,
            updatedAt = System.currentTimeMillis() - 5000
        )
        val newer = older.copy(id = "r2", createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
        joinRequests.add(older)
        joinRequests.add(newer)
        val resp = service.getOwnPendingJoinRequests()
        assertEquals(200, resp.statusCode.value())
        val body = resp.body as Map<*, *>
        val list = body["pendingJoinRequests"] as List<*>
        assertEquals(1, list.size) // only latest per family
        val entry = list.first() as Map<*, *>
        val request = entry["request"] as JoinRequest
        assertEquals("r2", request.id)
    }

    @Test
    fun `resendJoinRequestById respects throttle limit`() {
        val firstResp = service.requestToJoinFamily(JoinFamilyRequest(aliasName = familyAlias, notificationId = null))
        assertEquals(200, firstResp.statusCode.value())
        val firstBody = firstResp.body as Map<*, *>
        val firstId = firstBody["requestId"] as String
        // 4 resends by id (total 5 attempts)
        repeat(4) { idx ->
            val resp = service.resendJoinRequestById(com.lavish.expensetracker.controller.dto.JoinRequestByIdActionRequest(requestId = firstId, message = null))
            assertEquals(200, resp.statusCode.value(), "Resend by id ${idx + 1} should succeed")
        }
        // 6th attempt should block
        val blocked = service.resendJoinRequestById(com.lavish.expensetracker.controller.dto.JoinRequestByIdActionRequest(requestId = firstId, message = null))
        assertEquals(409, blocked.statusCode.value())
        val body = blocked.body as Map<*, *>
        assertEquals("MAX_RETRIES", body["reason"])
        assertEquals("Max retries over. Ask family owner to send", body["message"])
    }

    @Test
    fun `cancelling pending request excludes it from attempt counting`() {
        // Send initial
        val first = service.requestToJoinFamily(JoinFamilyRequest(aliasName = familyAlias, notificationId = null))
        assertEquals(200, first.statusCode.value())
        // Cancel it (now no counted attempts because CANCELLED excluded)
        val cancelResp = service.cancelOwnJoinRequest(JoinRequestActionRequest(aliasName = familyAlias, message = null))
        assertEquals(200, cancelResp.statusCode.value())
        // Now send 5 fresh attempts should still be allowed
        val second = service.requestToJoinFamily(JoinFamilyRequest(aliasName = familyAlias, notificationId = null))
        assertEquals(200, second.statusCode.value())
        repeat(4) { idx ->
            val resp = service.resendJoinRequest(JoinRequestActionRequest(aliasName = familyAlias, message = null))
            assertEquals(200, resp.statusCode.value(), "Resend ${idx + 1} should succeed post cancellation")
        }
        // Next should block
        val blocked = service.resendJoinRequest(JoinRequestActionRequest(aliasName = familyAlias, message = null))
        assertEquals(409, blocked.statusCode.value())
    }
}
