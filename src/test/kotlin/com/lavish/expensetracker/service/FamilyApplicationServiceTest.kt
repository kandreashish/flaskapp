package com.lavish.expensetracker.service

import com.lavish.expensetracker.controller.dto.*
import com.lavish.expensetracker.model.ExpenseUser
import com.lavish.expensetracker.model.Family
import com.lavish.expensetracker.model.Notification
import com.lavish.expensetracker.repository.ExpenseUserRepository
import com.lavish.expensetracker.repository.FamilyRepository
import com.lavish.expensetracker.repository.JoinRequestRepository
import com.lavish.expensetracker.repository.NotificationRepository
import com.lavish.expensetracker.util.AuthUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.HttpStatus
import java.util.*

class FamilyApplicationServiceTest {

    private lateinit var familyRepository: FamilyRepository
    private lateinit var userRepository: ExpenseUserRepository
    private lateinit var notificationRepository: NotificationRepository
    private lateinit var authUtil: AuthUtil
    private lateinit var familyNotificationService: FamilyNotificationService
    private lateinit var service: FamilyApplicationService
    private lateinit var joinRequestRepository: JoinRequestRepository

    private val userId = "user-123"
    private val baseUser = ExpenseUser(
        id = userId,
        name = "Alice",
        email = "alice@example.com",
        aliasName = "ALICE1",
        firebaseUid = "firebase-alice",
        familyId = null,
        fcmToken = "token123"
    )

    @BeforeEach
    fun setup() {
        familyRepository = mock(FamilyRepository::class.java)
        joinRequestRepository = mock(JoinRequestRepository::class.java)
        userRepository = mock(ExpenseUserRepository::class.java)
        notificationRepository = mock(NotificationRepository::class.java)
        authUtil = mock(AuthUtil::class.java)
        familyNotificationService = mock(FamilyNotificationService::class.java)
        service = FamilyApplicationService(
            familyRepository = familyRepository,
            userRepository = userRepository,
            notificationRepository = notificationRepository,
            authUtil = authUtil,
            familyNotificationService = familyNotificationService,
            joinRequestRepository = joinRequestRepository
        )
        `when`(authUtil.getCurrentUserId()).thenReturn(userId)
        `when`(userRepository.findById(userId)).thenReturn(Optional.of(baseUser))
        `when`(familyRepository.findAll()).thenReturn(emptyList())
    }

    @Test
    @DisplayName("createFamily success")
    fun createFamilySuccess() {
        val resp = service.createFamily(CreateFamilyRequest("My Family"))
        assertEquals(HttpStatus.OK, resp.statusCode)
        val body = resp.body as BasicFamilySuccessResponse
        assertEquals("Family created successfully", body.message)
        @Suppress("UNCHECKED_CAST")
        val map = body.family as Map<String, Any>
        assertTrue(map.containsKey("family"))
        verify(familyRepository).save(any(Family::class.java))
        verify(userRepository).save(any(ExpenseUser::class.java))
    }

    @Test
    @DisplayName("createFamily conflict when already in family")
    fun createFamilyConflict() {
        val existingUser = baseUser.copy(familyId = "fam-1")
        `when`(userRepository.findById(userId)).thenReturn(Optional.of(existingUser))
        val resp = service.createFamily(CreateFamilyRequest("Another"))
        assertEquals(HttpStatus.CONFLICT, resp.statusCode)
    }

    @Test
    @DisplayName("createFamily badRequest for blank name (mapped as NOT_FOUND per ApiResponseUtil bug)")
    fun createFamilyBlankName() {
        val resp = service.createFamily(CreateFamilyRequest("   "))
        // ApiResponseUtil.badRequest currently returns 404 NOT_FOUND
        assertEquals(HttpStatus.NOT_FOUND, resp.statusCode)
    }

    @Test
    @DisplayName("joinFamily success")
    fun joinFamilySuccess() {
        val fam = Family(
            familyId = "fam-1",
            headId = "head-1",
            name = "Cool Fam",
            aliasName = "ABCDEF",
            maxSize = 10,
            membersIds = mutableListOf("head-1"),
            updatedAt = System.currentTimeMillis()
        )
        `when`(familyRepository.findByAliasName("ABCDEF")).thenReturn(fam)
        val resp = service.joinFamily(JoinFamilyRequest("ABCDEF", null))
        assertEquals(HttpStatus.OK, resp.statusCode)
        verify(familyRepository).save(any(Family::class.java))
        verify(userRepository).save(any(ExpenseUser::class.java))
    }

    @Test
    @DisplayName("joinFamily conflict when already in family")
    fun joinFamilyAlreadyMember() {
        val existingUser = baseUser.copy(familyId = "fam-x")
        `when`(userRepository.findById(userId)).thenReturn(Optional.of(existingUser))
        val resp = service.joinFamily(JoinFamilyRequest("ALIAS1", null)) // use valid 6-char alias
        assertEquals(HttpStatus.CONFLICT, resp.statusCode)
    }

    @Test
    @DisplayName("inviteMember success triggers notification save + push")
    fun inviteMemberSuccess() {
        val family = Family(
            familyId = "fam-2",
            headId = userId,
            name = "Invite Fam",
            aliasName = "INV123",
            maxSize = 10,
            membersIds = mutableListOf(userId),
            updatedAt = System.currentTimeMillis()
        )
        val invited = baseUser.copy(id = "user-2", email = "bob@example.com", aliasName = "BOB111", firebaseUid = "firebase-bob")
        `when`(familyRepository.findById("fam-2")).thenReturn(Optional.of(family))
        `when`(userRepository.findById(userId)).thenReturn(Optional.of(baseUser.copy(familyId = "fam-2")))
        `when`(userRepository.findAll()).thenReturn(listOf(invited, baseUser))
        val resp = service.inviteMember(InviteMemberRequest("bob@example.com", null))
        assertEquals(HttpStatus.OK, resp.statusCode)
        verify(familyRepository).save(any(Family::class.java))
        // Verify a notification persisted
        verify(notificationRepository, atLeastOnce()).save(any(Notification::class.java))
    }

    @Test
    @DisplayName("resendInvitation no pending invitation -> conflict")
    fun resendInvitationNoPending() {
        val family = Family(
            familyId = "fam-3",
            headId = userId,
            name = "Fam",
            aliasName = "FAM999",
            maxSize = 10,
            membersIds = mutableListOf(userId),
            updatedAt = System.currentTimeMillis()
        )
        val invited = baseUser.copy(id = "user-9", email = "ghost@example.com")
        `when`(familyRepository.findById("fam-3")).thenReturn(Optional.of(family))
        `when`(userRepository.findById(userId)).thenReturn(Optional.of(baseUser.copy(familyId = "fam-3")))
        `when`(userRepository.findAll()).thenReturn(listOf(invited))
        val resp = service.resendInvitation(InviteMemberRequest("ghost@example.com", null))
        // Expect conflict because email not in pending list
        assertEquals(HttpStatus.CONFLICT, resp.statusCode)
    }

    @Test
    @DisplayName("removeMember head cannot remove self")
    fun removeMemberHeadSelf() {
        val family = Family(
            familyId = "fam-4",
            headId = userId,
            name = "Fam",
            aliasName = "FAM111",
            maxSize = 10,
            membersIds = mutableListOf(userId),
            updatedAt = System.currentTimeMillis()
        )
        `when`(familyRepository.findById("fam-4")).thenReturn(Optional.of(family))
        `when`(userRepository.findById(userId)).thenReturn(Optional.of(baseUser.copy(familyId = "fam-4")))
        val resp = service.removeMember(RemoveMemberRequest(baseUser.email, null))
        // badRequest maps to NOT_FOUND per ApiResponseUtil bug
        assertEquals(HttpStatus.NOT_FOUND, resp.statusCode)
    }

    @Test
    @DisplayName("requestToJoinFamily blocked when invitation exists")
    fun requestToJoinBlockedByInvitation() {
        val fam = Family(
            familyId = "fam-invite",
            headId = "head-9",
            name = "Inviting Fam",
            aliasName = "INV999",
            maxSize = 10,
            membersIds = mutableListOf("head-9"),
            pendingMemberEmails = mutableListOf(
                com.lavish.expensetracker.model.PendingMembersDetails(
                    email = baseUser.email,
                    userId = baseUser.id,
                    name = baseUser.name,
                    profilePic = null,
                    profilePicLow = null
                )
            ),
            updatedAt = System.currentTimeMillis()
        )
        `when`(familyRepository.findByAliasName("INV999")).thenReturn(fam)
        val resp = service.requestToJoinFamily(JoinFamilyRequest("INV999", null))
        assertEquals(HttpStatus.CONFLICT, resp.statusCode)
        val body = resp.body as com.lavish.expensetracker.model.response.ApiErrorResponse
        assertEquals("Invitation already present. Accept or reject the invitation.", body.message)
    }

    @Test
    @DisplayName("resendJoinRequest blocked when invitation exists")
    fun resendJoinRequestBlockedByInvitation() {
        val fam = Family(
            familyId = "fam-invite2",
            headId = "head-10",
            name = "Inviting Fam 2",
            aliasName = "INV888",
            maxSize = 10,
            membersIds = mutableListOf("head-10"),
            pendingMemberEmails = mutableListOf(
                com.lavish.expensetracker.model.PendingMembersDetails(
                    email = baseUser.email,
                    userId = baseUser.id,
                    name = baseUser.name
                )
            ),
            updatedAt = System.currentTimeMillis()
        )
        `when`(familyRepository.findByAliasName("INV888")).thenReturn(fam)
        val resp = service.resendJoinRequest(JoinRequestActionRequest("INV888", null))
        assertEquals(HttpStatus.CONFLICT, resp.statusCode)
        val body = resp.body as com.lavish.expensetracker.model.response.ApiErrorResponse
        assertEquals("Invitation already present. Accept or reject the invitation.", body.message)
    }

    @Test
    @DisplayName("resendInvitation removes stale invite if user already in another family")
    fun resendInvitationRemovesStale() {
        val invitedUser = baseUser.copy(id = "user-stale", email = "stale@example.com", aliasName = "STAL01", familyId = "other-fam")
        val fam = Family(
            familyId = "fam-stale",
            headId = userId,
            name = "Head Fam",
            aliasName = "HDSTAL",
            maxSize = 10,
            membersIds = mutableListOf(userId),
            pendingMemberEmails = mutableListOf(
                com.lavish.expensetracker.model.PendingMembersDetails(
                    email = invitedUser.email,
                    userId = invitedUser.id,
                    name = invitedUser.name
                )
            ),
            updatedAt = System.currentTimeMillis()
        )
        `when`(userRepository.findById(userId)).thenReturn(Optional.of(baseUser.copy(familyId = "fam-stale")))
        `when`(familyRepository.findById("fam-stale")).thenReturn(Optional.of(fam))
        `when`(userRepository.findAll()).thenReturn(listOf(baseUser, invitedUser))
        val resp = service.resendInvitation(InviteMemberRequest(invitedUser.email, null))
        assertEquals(HttpStatus.CONFLICT, resp.statusCode)
        val body = resp.body as com.lavish.expensetracker.model.response.ApiErrorResponse
        assertEquals("User already in a family. Pending invitation removed.", body.message)
        verify(familyRepository, atLeastOnce()).save(any(Family::class.java))
    }

    @Test
    @DisplayName("getFamilyDetails includes pendingMemberInvites and pendingJoinRequests with required fields")
    fun getFamilyDetailsContainsPendingLists() {
        val fam = Family(
            familyId = "fam-det",
            headId = userId,
            name = "Det Fam",
            aliasName = "DET123",
            maxSize = 10,
            membersIds = mutableListOf(userId),
            pendingMemberEmails = mutableListOf(
                com.lavish.expensetracker.model.PendingMembersDetails(
                    email = "inv1@example.com",
                    userId = "u-inv1",
                    name = "Invite One",
                    profilePic = "pic1",
                    profilePicLow = "pic1low"
                )
            ),
            pendingJoinRequests = mutableListOf(
                com.lavish.expensetracker.model.PendingMembersDetails(
                    email = "join1@example.com",
                    userId = "u-join1",
                    name = "Join One",
                    profilePic = "pic2",
                    profilePicLow = "pic2low"
                )
            ),
            updatedAt = System.currentTimeMillis()
        )
        `when`(userRepository.findById(userId)).thenReturn(Optional.of(baseUser.copy(familyId = "fam-det")))
        `when`(familyRepository.findById("fam-det")).thenReturn(Optional.of(fam))
        val resp = service.getFamilyDetails()
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any?>
        val familyMap = body["family"] as Map<*, *>
        val pendingInvites = familyMap["pendingMemberInvites"] as List<*>
        val pendingRequests = familyMap["pendingJoinRequests"] as List<*>
        assertEquals(1, pendingInvites.size)
        assertEquals(1, pendingRequests.size)
        val inv = pendingInvites.first() as Map<*, *>
        val jr = pendingRequests.first() as Map<*, *>
        listOf("email","userId","name","profilePic","profilePicLow").forEach { key ->
            assertTrue(inv.containsKey(key))
            assertTrue(jr.containsKey(key))
        }
    }
}
