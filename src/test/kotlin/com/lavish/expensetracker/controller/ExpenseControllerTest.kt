package com.lavish.expensetracker.controller

import com.lavish.expensetracker.config.PushNotificationService
import com.lavish.expensetracker.exception.ExpenseAccessDeniedException
import com.lavish.expensetracker.exception.ExpenseCreationException
import com.lavish.expensetracker.exception.ExpenseNotFoundException
import com.lavish.expensetracker.exception.ExpenseValidationException
import com.lavish.expensetracker.model.*
import com.lavish.expensetracker.repository.FamilyRepository
import com.lavish.expensetracker.repository.NotificationRepository
import com.lavish.expensetracker.service.ExpenseService
import com.lavish.expensetracker.service.UserDeviceService
import com.lavish.expensetracker.service.UserService
import com.lavish.expensetracker.util.AuthUtil
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.util.*

/**
 * NOTE: This test class focuses on maximizing line coverage for ExpenseController by directly
 * invoking controller methods with mocked dependencies. It intentionally exercises both success and
 * failure / edge branches for each public endpoint to achieve near-100% coverage of controller logic.
 */
class ExpenseControllerTest {

    private lateinit var expenseService: ExpenseService
    private lateinit var authUtil: AuthUtil
    private lateinit var pushNotificationService: PushNotificationService
    private lateinit var userService: UserService
    private lateinit var userDeviceService: UserDeviceService
    private lateinit var familyRepository: FamilyRepository
    private lateinit var notificationRepository: NotificationRepository

    private lateinit var controller: ExpenseController

    private val baseUser = ExpenseUser(
        id = "user-1",
        name = "Test User",
        email = "user@test.com",
        aliasName = "useralias",
        firebaseUid = "firebase-1",
        familyId = null,
        currencyPreference = "₹"
    )

    private fun expense(
        id: String = UUID.randomUUID().toString(),
        userId: String = baseUser.id,
        familyId: String? = null,
        amount: Int = 100,
        category: String = "FOOD",
        description: String = "Lunch",
        ts: Long = System.currentTimeMillis()
    ) = ExpenseDto(
        expenseId = id,
        userId = userId,
        amount = amount,
        category = category,
        description = description,
        date = ts,
        familyId = familyId,
        expenseCreatedOn = ts,
        createdBy = userId,
        modifiedBy = userId,
        updatedUserName = "Tester",
        lastModifiedOn = ts,
        synced = false,
        deleted = false,
        deletedOn = null,
        deletedBy = null
    )

    private fun paged(list: List<ExpenseDto> = listOf(expense()), page: Int = 0, size: Int = 10) = PagedResponse(
        content = list,
        page = page,
        size = size,
        totalElements = list.size.toLong(),
        totalPages = 1,
        isFirst = true,
        isLast = true,
        hasNext = false,
        hasPrevious = false,
        totalSumForMonth = list.sumOf { it.amount }.toDouble(),
        offset = null,
        lastExpenseId = list.lastOrNull()?.expenseId
    )

    @BeforeEach
    fun setup() {
        expenseService = mock(ExpenseService::class.java)
        authUtil = mock(AuthUtil::class.java)
        pushNotificationService = mock(PushNotificationService::class.java)
        userService = mock(UserService::class.java)
        userDeviceService = mock(UserDeviceService::class.java)
        familyRepository = mock(FamilyRepository::class.java)
        notificationRepository = mock(NotificationRepository::class.java)

        controller = ExpenseController(
            expenseService,
            authUtil,
            pushNotificationService,
            userService,
            userDeviceService,
            familyRepository,
            notificationRepository
        )

        // Common stubs
        `when`(authUtil.getCurrentUserId()).thenReturn(baseUser.id)
        `when`(userService.findById(baseUser.id)).thenReturn(baseUser)
        `when`(
            expenseService.getPersonalExpensesByUserIdWithOrder(
                anyString(),
                anyInt(),
                anyInt(),
                anyString(),
                anyBoolean()
            )
        ).thenAnswer { paged() }
        `when`(
            expenseService.getPersonalExpensesByUserIdAfterCursor(
                anyString(),
                anyString(),
                anyInt(),
                anyString(),
                anyBoolean()
            )
        ).thenAnswer { paged() }
        `when`(
            expenseService.getExpensesByFamilyIdAndUserFamilyWithOrder(
                anyString(),
                anyInt(),
                anyInt(),
                anyString(),
                anyBoolean()
            )
        ).thenAnswer { paged() }
        `when`(
            expenseService.getExpensesByFamilyIdAndUserFamilyAfterCursor(
                anyString(),
                anyString(),
                anyInt(),
                anyString(),
                anyBoolean()
            )
        ).thenAnswer { paged() }
        `when`(expenseService.getMonthlyExpenseSum(anyString(), anyInt(), anyInt(), anyOrNull())).thenReturn(500)
        `when`(expenseService.getExpenseCountByUserIdAndMonth(anyString(), anyInt(), anyInt())).thenReturn(5)
        `when`(expenseService.getFamilyMonthlyExpenseSum(anyInt(), anyInt(), anyString())).thenReturn(900)
        `when`(expenseService.getFamilyExpenseCountByUserIdAndMonth(anyString(), anyInt(), anyInt())).thenReturn(9)
        `when`(
            expenseService.getExpensesByUserIdAndCategory(
                anyString(),
                anyString(),
                anyInt(),
                anyInt()
            )
        ).thenAnswer { paged() }
        `when`(
            expenseService.getExpensesByUserIdAndDateRange(
                anyString(),
                anyLong(),
                anyLong(),
                anyInt(),
                anyInt()
            )
        ).thenAnswer { paged() }
        `when`(
            expenseService.getExpensesSince(
                anyString(),
                anyLong(),
                anyInt(),
                anyString(),
                anyBoolean()
            )
        ).thenAnswer { paged() }
        `when`(
            expenseService.getExpensesSinceWithCursor(
                anyString(),
                anyLong(),
                anyString(),
                anyInt(),
                anyString(),
                anyBoolean()
            )
        ).thenAnswer { paged() }
        `when`(
            expenseService.getExpensesSinceDate(
                anyString(),
                anyLong(),
                anyInt(),
                anyString(),
                anyBoolean()
            )
        ).thenAnswer { paged() }
        `when`(
            expenseService.getExpensesSinceDateWithCursor(
                anyString(),
                anyLong(),
                anyString(),
                anyInt(),
                anyString(),
                anyBoolean()
            )
        ).thenAnswer { paged() }
        `when`(
            expenseService.getFamilyExpensesSince(
                anyString(),
                anyLong(),
                anyInt(),
                anyString(),
                anyBoolean()
            )
        ).thenAnswer { paged() }
        `when`(
            expenseService.getFamilyExpensesSinceWithCursor(
                anyString(),
                anyLong(),
                anyString(),
                anyInt(),
                anyString(),
                anyBoolean()
            )
        ).thenAnswer { paged() }
        `when`(userService.getAllFcmTokens(anyString())).thenReturn(listOf("devToken"))
    }

    @Test
    fun getExpenses_withoutCursor() {
        val res = controller.getExpenses(0, 10, null, "invalidField", false)
        assertEquals(1, res.content.size)
    }

    @Test
    fun getExpenses_withCursor() {
        val res = controller.getExpenses(0, 10, "lastId", "date", true)
        assertEquals(1, res.content.size)
    }

    @Test
    fun getExpensesForFamily_notInFamilyThrows() {
        assertThrows(ResponseStatusException::class.java) {
            controller.getExpensesForFamily(0, 10, null, "date", false)
        }
    }

    @Test
    fun getExpensesForFamily_withFamily_andCursor() {
        val fam = Family(
            "fam1",
            headId = baseUser.id,
            name = "Family",
            aliasName = "Fam",
            membersIds = mutableListOf(baseUser.id)
        )
        val userWithFamily = baseUser.copy(familyId = fam.familyId)
        `when`(userService.findById(baseUser.id)).thenReturn(userWithFamily)
        `when`(familyRepository.findById(fam.familyId)).thenReturn(Optional.of(fam))
        val res = controller.getExpensesForFamily(0, 10, "cursorId", "date", true)
        assertEquals(1, res.content.size)
    }

    @Test
    fun createExpense_personal_success_withInvalidTokensCleanup() {
        val dto = expense(id = "", userId = "", familyId = null)
        `when`(expenseService.createExpense(any())).thenAnswer { (it.arguments[0] as ExpenseDto).copy(expenseId = "newId") }
        `when`(
            pushNotificationService.sendExpenseNotificationToMultiple(
                anyString(),
                anyString(),
                any(),
                anyList(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
            )
        )
            .thenReturn(listOf("badToken"))
        val response = controller.createExpense(dto)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        verify(userDeviceService).removeInvalidTokens(listOf("badToken"))
    }

    @Test
    fun createExpense_family_success_savesNotification() {
        val fam = Family(
            "fam1",
            headId = baseUser.id,
            name = "Family",
            aliasName = "Fam",
            membersIds = mutableListOf(baseUser.id)
        )
        val userWithFamily = baseUser.copy(familyId = fam.familyId)
        `when`(userService.findById(baseUser.id)).thenReturn(userWithFamily)
        `when`(familyRepository.findById(fam.familyId)).thenReturn(Optional.of(fam))
        `when`(userService.getFamilyMembersFcmTokens(fam.familyId)).thenReturn(listOf(userWithFamily))
        `when`(
            pushNotificationService.sendExpenseNotificationToMultiple(
                anyString(),
                anyString(),
                any(),
                anyList(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
            )
        )
            .thenReturn(emptyList())
        val dto = expense(id = "", userId = "", familyId = fam.familyId)
        `when`(expenseService.createExpense(any())).thenAnswer { (it.arguments[0] as ExpenseDto).copy(expenseId = "id2") }
        controller.createExpense(dto)
        verify(notificationRepository, atLeastOnce()).save(any())
    }

    @Test
    fun createExpense_validationErrors() {
        val bad = expense(
            id = "",
            userId = "",
            amount = -10,
            category = "",
            description = "<script>alert(1)</script>".padEnd(600, 'x')
        )
        val ex = assertThrows(ExpenseValidationException::class.java) { controller.createExpense(bad) }
        assertTrue(ex.validationErrors.isNotEmpty())
    }

    @Test
    fun createExpense_familyNotFound() {
        val dto = expense(id = "", userId = "", familyId = "famX")
        `when`(familyRepository.findById("famX")).thenReturn(Optional.empty())
        val response = controller.createExpense(dto)
        assertEquals(HttpStatus.PRECONDITION_FAILED, response.statusCode)
    }

    @Test
    fun createExpense_familyMembershipDenied() {
        val fam = Family("fam1", headId = "otherHead", name = "Family", aliasName = "Fam", membersIds = mutableListOf())
        `when`(familyRepository.findById(fam.familyId)).thenReturn(Optional.of(fam))
        val dto = expense(id = "", userId = "", familyId = fam.familyId)
        val response = controller.createExpense(dto)
        assertEquals(HttpStatus.PRECONDITION_FAILED, response.statusCode)
    }

    @Test
    fun getExpenseById_owner() {
        val e = expense()
        `when`(expenseService.getExpenseById(e.expenseId)).thenReturn(e)
        val res = controller.getExpenseById(e.expenseId)
        assertEquals(e.expenseId, res.body!!.expenseId)
    }

    @Test
    fun getExpenseById_familyAccess() {
        val fam = Family(
            "fam1",
            headId = baseUser.id,
            name = "Family",
            aliasName = "Fam",
            membersIds = mutableListOf(baseUser.id, "u2")
        )
        val owner = ExpenseUser(
            id = "u2",
            name = "U2",
            email = "u2@test.com",
            aliasName = "a2",
            firebaseUid = "f2",
            familyId = fam.familyId,
            currencyPreference = "₹"
        )
        val userWithFamily = baseUser.copy(familyId = fam.familyId)
        val e = expense(userId = owner.id, familyId = fam.familyId)
        `when`(userService.findById(baseUser.id)).thenReturn(userWithFamily)
        `when`(userService.findById(owner.id)).thenReturn(owner)
        `when`(expenseService.getExpenseById(e.expenseId)).thenReturn(e)
        val res = controller.getExpenseById(e.expenseId)
        assertEquals(e.expenseId, res.body!!.expenseId)
    }

    @Test
    fun getExpenseById_accessDenied() {
        val other = expense(userId = "otherUser")
        `when`(expenseService.getExpenseById(other.expenseId)).thenReturn(other)
        assertThrows(ExpenseAccessDeniedException::class.java) { controller.getExpenseById(other.expenseId) }
    }

    @Test
    fun getExpenseById_notFound() {
        `when`(expenseService.getExpenseById("missing")).thenReturn(null)
        assertThrows(ExpenseNotFoundException::class.java) {
            controller.getExpenseById("missing")
        }
    }

    @Test
    fun updateExpense_success() {
        val existing = expense()
        val updated = existing.copy(amount = 200)
        `when`(expenseService.getExpenseById(existing.expenseId)).thenReturn(existing)
        `when`(expenseService.updateExpense(eq(existing.expenseId), any())).thenReturn(updated)
        val res = controller.updateExpense(existing.expenseId, updated)
        assertEquals(200, (res.body as ExpenseDto).amount)
    }

    @Test
    fun updateExpense_validationError() {
        val existing = expense()
        `when`(expenseService.getExpenseById(existing.expenseId)).thenReturn(existing)
        val badUpdate = existing.copy(amount = -5)
        assertThrows(ExpenseValidationException::class.java) { controller.updateExpense(existing.expenseId, badUpdate) }
    }

    @Test
    fun updateExpense_notFound() {
        val id = "missing"
        `when`(expenseService.getExpenseById(id)).thenReturn(null)
        val res = controller.updateExpense(id, expense(id = id))
        assertEquals(HttpStatus.PRECONDITION_FAILED, res.statusCode)
    }

    @Test
    fun updateExpense_familyNotFound() {
        val existing = expense(familyId = "fam1")
        `when`(expenseService.getExpenseById(existing.expenseId)).thenReturn(existing)
        `when`(familyRepository.findById("fam1")).thenReturn(Optional.empty())
        val res = controller.updateExpense(existing.expenseId, existing)
        assertEquals(HttpStatus.PRECONDITION_FAILED, res.statusCode)
    }

    @Test
    fun updateExpense_familyMembershipDenied() {
        val existing = expense(familyId = "fam1")
        val fam = Family("fam1", headId = "other", name = "F", aliasName = "F", membersIds = mutableListOf())
        `when`(expenseService.getExpenseById(existing.expenseId)).thenReturn(existing)
        `when`(familyRepository.findById("fam1")).thenReturn(Optional.of(fam))
        val res = controller.updateExpense(existing.expenseId, existing)
        assertEquals(HttpStatus.PRECONDITION_FAILED, res.statusCode)
    }

    @Test
    fun updateExpense_internalServerError() {
        val existing = expense()
        `when`(expenseService.getExpenseById(existing.expenseId)).thenReturn(existing)
        `when`(expenseService.updateExpense(eq(existing.expenseId), any())).thenThrow(RuntimeException("db fail"))
        val res = controller.updateExpense(existing.expenseId, existing)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, res.statusCode)
    }

    @Test
    fun deleteExpense_owner() {
        val existing = expense()
        `when`(expenseService.getExpenseById(existing.expenseId)).thenReturn(existing)
        `when`(expenseService.deleteExpense(existing.expenseId)).thenReturn(true)
        val res = controller.deleteExpense(existing.expenseId)
        assertEquals(HttpStatus.OK, res.statusCode)
    }

    @Test
    fun deleteExpense_deleteReturnsFalse() {
        val existing = expense()
        `when`(expenseService.getExpenseById(existing.expenseId)).thenReturn(existing)
        `when`(expenseService.deleteExpense(existing.expenseId)).thenReturn(false)
        val res = controller.deleteExpense(existing.expenseId)
        assertEquals(HttpStatus.NOT_FOUND, res.statusCode)
    }

    @Test
    fun deleteExpense_forbidden() {
        val existing = expense(userId = "otherUser")
        `when`(expenseService.getExpenseById(existing.expenseId)).thenReturn(existing)
        val res = controller.deleteExpense(existing.expenseId)
        assertEquals(HttpStatus.FORBIDDEN, res.statusCode)
    }

    @Test
    fun deleteExpense_internalServerError() {
        val existing = expense()
        `when`(expenseService.getExpenseById(existing.expenseId)).thenReturn(existing)
        `when`(expenseService.deleteExpense(existing.expenseId)).thenThrow(RuntimeException("boom"))
        val res = controller.deleteExpense(existing.expenseId)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, res.statusCode)
    }

    @Test
    fun getExpensesByCategory() {
        val res = controller.getExpensesByCategory("FOOD", 0, 10)
        assertEquals(1, res.content.size)
    }

    @Test
    fun getExpensesBetweenDates() {
        val start = LocalDate.now().minusDays(1).toString()
        val end = LocalDate.now().toString()
        val res = controller.getExpensesBetweenDates(start, end, 0, 10)
        assertEquals(1, res.content.size)
    }

    @Test
    fun notifyExpense_success() {
        val e = expense()
        `when`(expenseService.getExpenseById(e.expenseId)).thenReturn(e)
        `when`(userService.getAllFcmTokens(baseUser.id)).thenReturn(listOf("token1", "token2"))
        `when`(pushNotificationService.sendNotificationToMultiple(anyList(), anyString(), anyString())).thenReturn(
            emptyList()
        )
        val res = controller.notifyExpense(ExpenseController.ExpenseNotificationRequest(e.expenseId))
        assertTrue(res.body!!.contains("successfully"))
    }

    @Test
    fun notifyExpense_noTokens() {
        val e = expense()
        `when`(expenseService.getExpenseById(e.expenseId)).thenReturn(e)
        `when`(userService.getAllFcmTokens(baseUser.id)).thenReturn(emptyList())
        val res = controller.notifyExpense(ExpenseController.ExpenseNotificationRequest(e.expenseId))
        assertEquals(HttpStatus.BAD_REQUEST, res.statusCode)
    }

    @Test
    fun notifyExpense_invalidTokensRemoved() {
        val e = expense()
        `when`(expenseService.getExpenseById(e.expenseId)).thenReturn(e)
        `when`(userService.getAllFcmTokens(baseUser.id)).thenReturn(listOf("t1", "t2", "t3"))
        `when`(pushNotificationService.sendNotificationToMultiple(anyList(), anyString(), anyString())).thenReturn(
            listOf("t2")
        )
        val res = controller.notifyExpense(ExpenseController.ExpenseNotificationRequest(e.expenseId))
        assertEquals(HttpStatus.OK, res.statusCode)
        verify(userDeviceService).removeInvalidTokens(listOf("t2"))
    }

    @Test
    fun getMonthlyExpenseSum_invalidMonth() {
        val res = controller.getMonthlyExpenseSum(2024, 13)
        assertEquals(HttpStatus.BAD_REQUEST, res.statusCode)
    }

    @Test
    fun getMonthlyExpenseSum_success() {
        val res = controller.getMonthlyExpenseSum(2024, 8)
        assertEquals(HttpStatus.OK, res.statusCode)
    }

    @Test
    fun getFamilyMonthlyExpenseSum_notInFamily() {
        val res = controller.getFamilyMonthlyExpenseSum(2024, 8)
        assertEquals(HttpStatus.PRECONDITION_FAILED, res.statusCode)
    }

    @Test
    fun getFamilyMonthlyExpenseSum_invalidYear() {
        val userWithFamily = baseUser.copy(familyId = "fam1")
        `when`(userService.findById(baseUser.id)).thenReturn(userWithFamily)
        val res = controller.getFamilyMonthlyExpenseSum(1999, 8)
        assertEquals(HttpStatus.BAD_REQUEST, res.statusCode)
    }

    @Test
    fun getFamilyMonthlyExpenseSum_invalidMonth() {
        val userWithFamily = baseUser.copy(familyId = "fam1")
        `when`(userService.findById(baseUser.id)).thenReturn(userWithFamily)
        val res = controller.getFamilyMonthlyExpenseSum(2024, 0)
        assertEquals(HttpStatus.BAD_REQUEST, res.statusCode)
    }

    @Test
    fun getFamilyMonthlyExpenseSum_success() {
        val userWithFamily = baseUser.copy(familyId = "fam1")
        `when`(userService.findById(baseUser.id)).thenReturn(userWithFamily)
        val res = controller.getFamilyMonthlyExpenseSum(2024, 8)
        assertEquals(HttpStatus.OK, res.statusCode)
    }

    @Test
    fun getExpensesSince_withoutCursor() {
        val res = controller.getExpensesSince(System.currentTimeMillis() - 1000, 10, null, "lastModifiedOn", true)
        assertEquals(1, res.content.size)
    }

    @Test
    fun getExpensesSince_withCursor() {
        val res = controller.getExpensesSince(System.currentTimeMillis() - 1000, 10, "cursorId", "lastModifiedOn", true)
        assertEquals(1, res.content.size)
    }

    @Test
    fun getExpensesSinceDate_invalidDateFormat() {
        assertThrows(ExpenseValidationException::class.java) {
            controller.getExpensesSinceDate("2024-13-50", 10, null, "date", true)
        }
    }

    @Test
    fun getExpensesSinceDate_withCursor() {
        val fam = Family(
            "fam1",
            headId = baseUser.id,
            name = "Family",
            aliasName = "F",
            membersIds = mutableListOf(baseUser.id)
        )
        val userWithFamily = baseUser.copy(familyId = fam.familyId)
        `when`(userService.findById(baseUser.id)).thenReturn(userWithFamily)
        `when`(familyRepository.findById(fam.familyId)).thenReturn(Optional.of(fam))
        val date = LocalDate.now().minusDays(3).toString()
        val res = controller.getFamilyExpensesSinceDate(date, 10, "cursorId", "date", true)
        assertEquals(1, res.content.size)
    }

    @Test
    fun getExpensesSinceDate_withoutCursor() {
        val date = LocalDate.now().minusDays(5).toString()
        val res = controller.getExpensesSinceDate(date, 10, null, "date", true)
        assertEquals(1, res.content.size)
    }

    @Test
    fun getFamilyExpensesSince_noFamily() {
        assertThrows(ResponseStatusException::class.java) {
            controller.getFamilyExpensesSince(System.currentTimeMillis() - 1000, 10, null, "lastModifiedOn", true)
        }
    }

    @Test
    fun getFamilyExpensesSince_withCursor() {
        val fam = Family(
            "fam1",
            headId = baseUser.id,
            name = "Family",
            aliasName = "Fam",
            membersIds = mutableListOf(baseUser.id)
        )
        val userWithFamily = baseUser.copy(familyId = fam.familyId)
        `when`(userService.findById(baseUser.id)).thenReturn(userWithFamily)
        `when`(familyRepository.findById(fam.familyId)).thenReturn(Optional.of(fam))
        val res =
            controller.getFamilyExpensesSince(System.currentTimeMillis() - 1000, 10, "cursorId", "lastModifiedOn", true)
        assertEquals(1, res.content.size)
    }

    @Test
    fun getFamilyExpensesSinceDate_invalidDate() {
        val fam = Family(
            "fam1",
            headId = baseUser.id,
            name = "Family",
            aliasName = "Fam",
            membersIds = mutableListOf(baseUser.id)
        )
        val userWithFamily = baseUser.copy(familyId = fam.familyId)
        `when`(userService.findById(baseUser.id)).thenReturn(userWithFamily)
        `when`(familyRepository.findById(fam.familyId)).thenReturn(Optional.of(fam))

        assertThrows(ExpenseValidationException::class.java) {
            controller.getFamilyExpensesSinceDate("2024-15-01", 10, null, "date", true)
        }
    }

    @Test
    fun getFamilyExpensesSinceDate_success_withoutCursor() {
        val fam = Family(
            "fam1",
            headId = baseUser.id,
            name = "Family",
            aliasName = "Fam",
            membersIds = mutableListOf(baseUser.id)
        )
        val userWithFamily = baseUser.copy(familyId = fam.familyId)
        `when`(userService.findById(baseUser.id)).thenReturn(userWithFamily)
        `when`(familyRepository.findById(fam.familyId)).thenReturn(Optional.of(fam))
        val date = LocalDate.now().minusDays(3).toString()
        val res = controller.getFamilyExpensesSinceDate(date, 10, null, "date", true)
        assertEquals(1, res.content.size)
    }

    @Test
    fun authUnauthorizedRemapped() {
        `when`(authUtil.getCurrentUserId()).thenThrow(ResponseStatusException(HttpStatus.UNAUTHORIZED, "Auth required"))
        val ex =
            assertThrows(ResponseStatusException::class.java) { controller.getExpenses(0, 10, null, "date", false) }
        assertEquals(HttpStatus.UNAUTHORIZED, ex.statusCode)
    }

    @Test
    fun authForbiddenRemapped() {
        `when`(authUtil.getCurrentUserId()).thenThrow(ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden"))
        val ex =
            assertThrows(ResponseStatusException::class.java) { controller.getExpenses(0, 10, null, "date", false) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode) // mapped to BAD_REQUEST per controller logic
    }

    @Test
    fun getExpensesForFamily_familyMissingInRepo() {
        val famId = "famMissing"
        val userWithFamily = baseUser.copy(familyId = famId)
        `when`(userService.findById(baseUser.id)).thenReturn(userWithFamily)
        `when`(familyRepository.findById(famId)).thenReturn(Optional.empty())
        assertThrows(ResponseStatusException::class.java) {
            controller.getExpensesForFamily(0, 10, null, "date", false)
        }
    }

    @Test
    fun createExpense_serviceThrows() {
        val dto = expense(id = "", userId = "", familyId = null)
        `when`(expenseService.createExpense(any())).thenThrow(RuntimeException("insert error"))
        assertThrows(ExpenseCreationException::class.java) { controller.createExpense(dto) }
    }

    @Test
    fun createExpense_notificationFailureHandled() {
        val dto = expense(id = "", userId = "", familyId = null)
        `when`(expenseService.createExpense(any())).thenAnswer { (it.arguments[0] as ExpenseDto).copy(expenseId = "nid") }
        `when`(
            pushNotificationService.sendExpenseNotificationToMultiple(
                anyString(),
                anyString(),
                any(),
                anyList(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
            )
        ).thenThrow(RuntimeException("fcm fail"))
        val res = controller.createExpense(dto)
        assertEquals(HttpStatus.CREATED, res.statusCode)
    }

    @Test
    fun createExpense_cleanupInvalidTokensErrorIgnored() {
        val dto = expense(id = "", userId = "", familyId = null)
        `when`(expenseService.createExpense(any())).thenAnswer { (it.arguments[0] as ExpenseDto).copy(expenseId = "nid2") }
        `when`(
            pushNotificationService.sendExpenseNotificationToMultiple(
                anyString(),
                anyString(),
                any(),
                anyList(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
            )
        ).thenReturn(listOf("bad1"))
        doThrow(RuntimeException("cleanup fail")).`when`(userDeviceService).removeInvalidTokens(listOf("bad1"))
        val res = controller.createExpense(dto)
        assertEquals(HttpStatus.CREATED, res.statusCode)
    }

    @Test
    fun getExpensesForFamily_withoutCursor() {
        val fam = Family(
            "fam2",
            headId = baseUser.id,
            name = "Fam2",
            aliasName = "F2",
            membersIds = mutableListOf(baseUser.id)
        )
        val userWithFamily = baseUser.copy(familyId = fam.familyId)
        `when`(userService.findById(baseUser.id)).thenReturn(userWithFamily)
        `when`(familyRepository.findById(fam.familyId)).thenReturn(Optional.of(fam))
        val res = controller.getExpensesForFamily(0, 10, null, "date", false)
        assertEquals(1, res.content.size)
    }

    @Test
    fun createExpense_familyNotificationFamilyMissing() {
        val famId = "famMissing2"
        val userWithFamily = baseUser.copy(familyId = famId)
        `when`(userService.findById(baseUser.id)).thenReturn(userWithFamily)
        // familyRepository returns empty so saveNotificationToDatabase early exits
        val dto = expense(id = "", userId = "", familyId = famId)
        `when`(expenseService.createExpense(any())).thenAnswer { (it.arguments[0] as ExpenseDto).copy(expenseId = "fid") }
        val res = controller.createExpense(dto)
        assertEquals(HttpStatus.PRECONDITION_FAILED, res.statusCode) // because family not found earlier in create flow
    }

    @Test
    fun createExpense_familyNotificationSentToAllDevices() {
        // Setup family with multiple members
        val familyMember1 = ExpenseUser(
            id = "member1", name = "Member 1", email = "member1@test.com",
            aliasName = "m1", firebaseUid = "fb1", familyId = "fam1", currencyPreference = "₹"
        )
        val familyMember2 = ExpenseUser(
            id = "member2", name = "Member 2", email = "member2@test.com",
            aliasName = "m2", firebaseUid = "fb2", familyId = "fam1", currencyPreference = "₹"
        )
        val familyMember3 = ExpenseUser(
            id = "member3", name = "Member 3", email = "member3@test.com",
            aliasName = "m3", firebaseUid = "fb3", familyId = "fam1", currencyPreference = "₹"
        )

        val fam = Family(
            "fam1", headId = baseUser.id, name = "Test Family", aliasName = "TestFam",
            membersIds = mutableListOf(baseUser.id, familyMember1.id, familyMember2.id, familyMember3.id)
        )
        val userWithFamily = baseUser.copy(familyId = fam.familyId)

        // Mock family repository
        `when`(familyRepository.findById(fam.familyId)).thenReturn(Optional.of(fam))
        `when`(userService.findById(baseUser.id)).thenReturn(userWithFamily)

        // Mock family members with their FCM tokens
        `when`(userService.getFamilyMembersFcmTokens(fam.familyId)).thenReturn(
            listOf(userWithFamily, familyMember1, familyMember2, familyMember3)
        )

        // Mock FCM tokens for each family member (simulating multiple devices per user)
        `when`(userService.getAllFcmTokens(userWithFamily.id)).thenReturn(listOf("user1_device1", "user1_device2"))
        `when`(userService.getAllFcmTokens(familyMember1.id)).thenReturn(
            listOf(
                "member1_device1",
                "member1_device2",
                "member1_device3"
            )
        )
        `when`(userService.getAllFcmTokens(familyMember2.id)).thenReturn(listOf("member2_device1"))
        `when`(userService.getAllFcmTokens(familyMember3.id)).thenReturn(listOf("member3_device1", "member3_device2"))

        // Expected all FCM tokens (8 total devices across 4 family members)
        val expectedTokens = listOf(
            "user1_device1", "user1_device2",           // Base user: 2 devices
            "member1_device1", "member1_device2", "member1_device3",  // Member1: 3 devices
            "member2_device1",                          // Member2: 1 device
            "member3_device1", "member3_device2"        // Member3: 2 devices
        )

        // Mock successful notification sending (no invalid tokens)
        `when`(
            pushNotificationService.sendExpenseNotificationToMultiple(
                anyString(), anyString(), any(), eq(expectedTokens), anyString(), anyString(), anyString(), anyString()
            )
        ).thenReturn(emptyList())

        // Create family expense
        val dto = expense(id = "", userId = "", familyId = fam.familyId, amount = 150, description = "Family Dinner")
        `when`(expenseService.createExpense(any())).thenAnswer {
            (it.arguments[0] as ExpenseDto).copy(expenseId = "family_expense_123")
        }

        // Execute the expense creation
        val response = controller.createExpense(dto)

        // Verify successful response
        assertEquals(HttpStatus.CREATED, response.statusCode)

        // Verify notification was sent to ALL family member devices
        verify(pushNotificationService).sendExpenseNotificationToMultiple(
            title = anyString(),
            body = anyString(),
            type = any(),
            tokens = eq(expectedTokens), // Verify all 8 device tokens are included
            amount = anyString(),
            description = eq("Family Dinner"),
            userId = eq(userWithFamily.id),
            expenseId = eq("family_expense_123")
        )

        // Verify notification was saved to database for each family member
        verify(notificationRepository, times(4)).save(any()) // 4 family members = 4 database notifications

        // Verify no invalid token cleanup was needed (all tokens were valid)
        verify(userDeviceService, never()).removeInvalidTokens(any())
    }

    @Test
    fun createExpense_personalNotificationSentToUserDevices() {
        // Mock user with multiple personal devices
        `when`(userService.getAllFcmTokens(baseUser.id)).thenReturn(
            listOf("personal_device1", "personal_device2", "personal_device3")
        )

        // Mock successful notification sending
        `when`(
            pushNotificationService.sendExpenseNotificationToMultiple(
                anyString(), anyString(), any(), eq(listOf("personal_device1", "personal_device2", "personal_device3")),
                anyString(), anyString(), anyString(), anyString()
            )
        ).thenReturn(emptyList())

        // Create personal expense (no family)
        val dto = expense(id = "", userId = "", familyId = null, amount = 50, description = "Personal Coffee")
        `when`(expenseService.createExpense(any())).thenAnswer {
            (it.arguments[0] as ExpenseDto).copy(expenseId = "personal_expense_456")
        }

        // Execute the expense creation
        val response = controller.createExpense(dto)

        // Verify successful response
        assertEquals(HttpStatus.CREATED, response.statusCode)

        // Verify notification was sent to all user's personal devices
        verify(pushNotificationService).sendExpenseNotificationToMultiple(
            title = anyString(),
            body = anyString(),
            type = any(),
            tokens = eq(listOf("personal_device1", "personal_device2", "personal_device3")),
            amount = anyString(),
            description = eq("Personal Coffee"),
            userId = eq(baseUser.id),
            expenseId = eq("personal_expense_456")
        )

        // Verify no database notification was saved (personal expenses don't save to notification table)
        verify(notificationRepository, never()).save(any())
    }

    @Test
    fun createExpense_handlesInvalidTokensAndNotifiesValidDevices() {
        // Setup family
        val familyMember = ExpenseUser(
            id = "member1", name = "Member 1", email = "member1@test.com",
            aliasName = "m1", firebaseUid = "fb1", familyId = "fam1", currencyPreference = "₹"
        )
        val fam = Family(
            "fam1", headId = baseUser.id, name = "Test Family", aliasName = "TestFam",
            membersIds = mutableListOf(baseUser.id, familyMember.id)
        )
        val userWithFamily = baseUser.copy(familyId = fam.familyId)

        `when`(familyRepository.findById(fam.familyId)).thenReturn(Optional.of(fam))
        `when`(userService.findById(baseUser.id)).thenReturn(userWithFamily)
        `when`(userService.getFamilyMembersFcmTokens(fam.familyId)).thenReturn(listOf(userWithFamily, familyMember))

        // Mix of valid and invalid tokens
        `when`(userService.getAllFcmTokens(userWithFamily.id)).thenReturn(listOf("valid_token1", "invalid_token1"))
        `when`(userService.getAllFcmTokens(familyMember.id)).thenReturn(
            listOf(
                "valid_token2",
                "invalid_token2",
                "valid_token3"
            )
        )

        val allTokens = listOf("valid_token1", "invalid_token1", "valid_token2", "invalid_token2", "valid_token3")
        val invalidTokens = listOf("invalid_token1", "invalid_token2")

        // Mock notification service returning invalid tokens
        `when`(
            pushNotificationService.sendExpenseNotificationToMultiple(
                anyString(), anyString(), any(), eq(allTokens), anyString(), anyString(), anyString(), anyString()
            )
        ).thenReturn(invalidTokens)

        // Create family expense
        val dto = expense(id = "", userId = "", familyId = fam.familyId)
        `when`(expenseService.createExpense(any())).thenAnswer {
            (it.arguments[0] as ExpenseDto).copy(expenseId = "test_expense")
        }

        // Execute the expense creation
        val response = controller.createExpense(dto)

        // Verify successful response (even with some invalid tokens)
        assertEquals(HttpStatus.CREATED, response.statusCode)

        // Verify notification was attempted to all devices
        verify(pushNotificationService).sendExpenseNotificationToMultiple(
            anyString(), anyString(), any(), eq(allTokens), anyString(), anyString(), anyString(), anyString()
        )

        // Verify invalid tokens were cleaned up
        verify(userDeviceService).removeInvalidTokens(eq(invalidTokens))

        // Verify database notifications were still saved
        verify(notificationRepository, times(2)).save(any()) // 2 family members
    }

    // ===== ADDITIONAL EDGE CASE TESTS =====

    @Test
    fun createExpense_emptyExpenseId() {
        val dto = expense(id = "", userId = "", familyId = null, amount = 0, category = "", description = "")
        val ex = assertThrows(ExpenseValidationException::class.java) {
            controller.createExpense(dto)
        }
        assertTrue(ex.validationErrors.any { it.contains("Amount") })
        assertTrue(ex.validationErrors.any { it.contains("Category") })
    }

    @Test
    fun createExpense_extremelyLargeAmount() {
        val dto =
            expense(id = "", userId = "", amount = ExpenseController.MAX_AMOUNT.toInt(), description = "Huge expense")
        `when`(expenseService.createExpense(any())).thenAnswer {
            (it.arguments[0] as ExpenseDto).copy(expenseId = "large_id")
        }
        val response = controller.createExpense(dto)
        println(response)
        assertEquals(HttpStatus.CREATED, response.statusCode)
    }

    @Test
    fun createExpense_negativeAmount() {
        val dto = expense(id = "", userId = "", amount = -100, description = "Negative")
        assertThrows(ExpenseValidationException::class.java) {
            controller.createExpense(dto)
        }
    }

    @Test
    fun createExpense_zeroAmount() {
        val dto = expense(id = "", userId = "", amount = 0, description = "Zero amount")
        assertThrows(ExpenseValidationException::class.java) {
            controller.createExpense(dto)
        }
    }

    @Test
    fun createExpense_sqlInjectionInDescription() {
        val dto = expense(id = "", userId = "", description = "'; DROP TABLE expenses; --")
        `when`(expenseService.createExpense(any())).thenAnswer {
            (it.arguments[0] as ExpenseDto).copy(expenseId = "safe_id")
        }
        val response = controller.createExpense(dto)
        assertEquals(HttpStatus.CREATED, response.statusCode)
    }

    @Test
    fun createExpense_xssInDescription() {
        val dto = expense(id = "", userId = "", description = "<script>alert('xss')</script>")
        assertThrows(ExpenseValidationException::class.java) {
            controller.createExpense(dto)
        }
    }

    @Test
    fun createExpense_unicodeInDescription() {
        val dto = expense(id = "", userId = "", description = "🍕🥤💰 Unicode expense ₹₹₹")
        `when`(expenseService.createExpense(any())).thenAnswer {
            (it.arguments[0] as ExpenseDto).copy(expenseId = "unicode_id")
        }
        val response = controller.createExpense(dto)
        assertEquals(HttpStatus.CREATED, response.statusCode)
    }

    @Test
    fun createExpense_veryLongDescription() {
        val longDescription = "A".repeat(1000) // Extremely long description
        val dto = expense(id = "", userId = "", description = longDescription)
        assertThrows(ExpenseValidationException::class.java) {
            controller.createExpense(dto)
        }
    }

    @Test
    fun createExpense_invalidCategory() {
        val dto = expense(id = "", userId = "", category = "INVALID_CATEGORY_123")
        assertThrows(ExpenseValidationException::class.java) {
            controller.createExpense(dto)
        }
    }

    @Test
    fun createExpense_emptyCategory() {
        val dto = expense(id = "", userId = "", category = "")
        assertThrows(ExpenseValidationException::class.java) {
            controller.createExpense(dto)
        }
    }

    @Test
    fun createExpense_userServiceThrowsException() {
        val dto = expense(id = "", userId = "", familyId = null)
        `when`(userService.findById(baseUser.id)).thenThrow(RuntimeException("Database connection failed"))
        assertThrows(RuntimeException::class.java) {
            controller.createExpense(dto)
        }
    }

    @Test
    fun createExpense_familyRepositoryThrowsException() {
        val dto = expense(id = "", userId = "", familyId = "fam1")
        `when`(familyRepository.findById("fam1")).thenThrow(RuntimeException("DB error"))
        assertThrows(RuntimeException::class.java) {
            controller.createExpense(dto)
        }
    }

    @Test
    fun getExpenseById_emptyExpenseId() {
        `when`(expenseService.getExpenseById("")).thenReturn(null)
        assertThrows(ExpenseNotFoundException::class.java) {
            controller.getExpenseById("")
        }
    }

    @Test
    fun getExpenseById_malformedExpenseId() {
        val malformedId = "'; DROP TABLE expenses; --"
        `when`(expenseService.getExpenseById(malformedId)).thenReturn(null)
        assertThrows(ExpenseNotFoundException::class.java) {
            controller.getExpenseById(malformedId)
        }
    }

    @Test
    fun getExpenseById_expenseServiceThrowsException() {
        val expenseId = "test-id"
        `when`(expenseService.getExpenseById(expenseId)).thenThrow(RuntimeException("Service unavailable"))
        assertThrows(RuntimeException::class.java) {
            controller.getExpenseById(expenseId)
        }
    }

    @Test
    fun updateExpense_mismatchedIds() {
        val existing = expense(id = "original-id")
        val updated = existing.copy(expenseId = "different-id")
        `when`(expenseService.getExpenseById("original-id")).thenReturn(existing)
        `when`(expenseService.updateExpense(eq("original-id"), any())).thenReturn(updated)

        val response = controller.updateExpense("original-id", updated)
        // Should still work as controller uses path parameter ID
        verify(expenseService).updateExpense(eq("original-id"), any())
        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun updateExpense_serviceReturnsNull() {
        val existing = expense()
        `when`(expenseService.getExpenseById(existing.expenseId)).thenReturn(existing)
        `when`(expenseService.updateExpense(eq(existing.expenseId), any())).thenReturn(null)

        val response = controller.updateExpense(existing.expenseId, existing)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
    }

    @Test
    fun deleteExpense_serviceThrowsException() {
        val existing = expense()
        `when`(expenseService.getExpenseById(existing.expenseId)).thenReturn(existing)
        `when`(expenseService.deleteExpense(existing.expenseId)).thenThrow(RuntimeException("Delete failed"))

        val response = controller.deleteExpense(existing.expenseId)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
    }

    @Test
    fun getExpenses_invalidPaginationParameters() {
        // Negative page
        val res1 = controller.getExpenses(-1, 10, null, "date", true)
        assertEquals(1, res1.content.size) // Should handle gracefully

        // Zero size
        val res2 = controller.getExpenses(0, 0, null, "date", true)
        assertEquals(1, res2.content.size) // Should handle gracefully

        // Extremely large size
        val res3 = controller.getExpenses(0, 10000, null, "date", true)
        assertEquals(1, res3.content.size) // Should handle gracefully
    }

    @Test
    fun getExpenses_invalidSortParameters() {
        // Empty sort field
        val res1 = controller.getExpenses(0, 10, null, "", true)
        assertEquals(1, res1.content.size)

        // SQL injection in sort field
        val res2 = controller.getExpenses(0, 10, null, "date'; DROP TABLE expenses; --", true)
        assertEquals(1, res2.content.size)
    }

    @Test
    fun getExpensesByCategory_emptyCategory() {
        val res = controller.getExpensesByCategory("", 0, 10)
        assertEquals(1, res.content.size)
    }

    @Test
    fun getExpensesByCategory_invalidCategory() {
        val res = controller.getExpensesByCategory("INVALID_CATEGORY_XYZ", 0, 10)
        assertEquals(1, res.content.size)
    }

    @Test
    fun getExpensesBetweenDates_invalidDateFormats() {
        // Invalid date format
        assertThrows(Exception::class.java) {
            controller.getExpensesBetweenDates("invalid-date", "2024-01-01", 0, 10)
        }

        // End date before start date
        val start = LocalDate.now().toString()
        val end = LocalDate.now().minusDays(10).toString()
        val res = controller.getExpensesBetweenDates(start, end, 0, 10)
        assertEquals(1, res.content.size) // Should handle gracefully
    }

    @Test
    fun notifyExpense_emptyExpenseId() {
        val request = ExpenseController.ExpenseNotificationRequest("")
        `when`(expenseService.getExpenseById("")).thenReturn(null)
        assertThrows(ExpenseNotFoundException::class.java) {
            controller.notifyExpense(request)
        }
    }

    @Test
    fun notifyExpense_expenseNotFound() {
        val request = ExpenseController.ExpenseNotificationRequest("missing-id")
        `when`(expenseService.getExpenseById("missing-id")).thenReturn(null)
        assertThrows(ExpenseNotFoundException::class.java) {
             controller.notifyExpense(request)
        }
    }

    @Test
    fun getMonthlyExpenseSum_extremeValues() {
        // Year 1900 (minimum)
        val res1 = controller.getMonthlyExpenseSum(1900, 1)
        assertEquals(HttpStatus.BAD_REQUEST, res1.statusCode)

        // Year 3000 (future)
        val res2 = controller.getMonthlyExpenseSum(3000, 1)
        assertEquals(HttpStatus.BAD_REQUEST, res2.statusCode)

        // Month 0
        val res3 = controller.getMonthlyExpenseSum(2024, 0)
        assertEquals(HttpStatus.BAD_REQUEST, res3.statusCode)

        // Month 13
        val res4 = controller.getMonthlyExpenseSum(2024, 13)
        assertEquals(HttpStatus.BAD_REQUEST, res4.statusCode)
    }

    @Test
    fun userService_returnsNullUser() {
        `when`(userService.findById(baseUser.id)).thenReturn(null)
        assertThrows(Exception::class.java) {
            controller.getExpenses(0, 10, null, "date", true)
        }
    }

    @Test
    fun notificationRepository_saveThrowsException() {
        val fam = Family(
            "fam1",
            headId = baseUser.id,
            name = "Family",
            aliasName = "Fam",
            membersIds = mutableListOf(baseUser.id)
        )
        val userWithFamily = baseUser.copy(familyId = fam.familyId)
        `when`(userService.findById(baseUser.id)).thenReturn(userWithFamily)
        `when`(familyRepository.findById(fam.familyId)).thenReturn(Optional.of(fam))
        `when`(userService.getFamilyMembersFcmTokens(fam.familyId)).thenReturn(listOf(userWithFamily))
        `when`(
            pushNotificationService.sendExpenseNotificationToMultiple(
                anyString(),
                anyString(),
                any(),
                anyList(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
            )
        )
            .thenReturn(emptyList())
        `when`(notificationRepository.save(any())).thenThrow(RuntimeException("Database save failed"))

        val dto = expense(id = "", userId = "", familyId = fam.familyId)
        `when`(expenseService.createExpense(any())).thenAnswer { (it.arguments[0] as ExpenseDto).copy(expenseId = "test") }

        // Should still succeed even if notification save fails
        val response = controller.createExpense(dto)
        assertEquals(HttpStatus.CREATED, response.statusCode)
    }

    @Test
    fun createExpense_concurrentModificationScenario() {
        val dto = expense(id = "", userId = "", familyId = null)
        `when`(expenseService.createExpense(any())).thenThrow(RuntimeException("Optimistic locking failed"))

        assertThrows(ExpenseCreationException::class.java) {
            controller.createExpense(dto)
        }
    }

    @Test
    fun getFamilyExpensesSinceDate_emptyDate() {
        val fam = Family(
            "fam1",
            headId = baseUser.id,
            name = "Family",
            aliasName = "Fam",
            membersIds = mutableListOf(baseUser.id)
        )
        val userWithFamily = baseUser.copy(familyId = fam.familyId)
        `when`(userService.findById(baseUser.id)).thenReturn(userWithFamily)
        `when`(familyRepository.findById(fam.familyId)).thenReturn(Optional.of(fam))

        assertThrows(ExpenseValidationException::class.java) {
            controller.getFamilyExpensesSinceDate("", 10, null, "date", true)
        }
    }

    @Test
    fun createExpense_familyWithNoMembers() {
        val fam = Family(
            "fam1",
            headId = baseUser.id,
            name = "Empty Family",
            aliasName = "Empty",
            membersIds = mutableListOf()
        )
        val userWithFamily = baseUser.copy(familyId = fam.familyId)
        `when`(userService.findById(baseUser.id)).thenReturn(userWithFamily)
        `when`(familyRepository.findById(fam.familyId)).thenReturn(Optional.of(fam))

        val dto = expense(id = "", userId = "", familyId = fam.familyId)

        // User is head but not in members list - should still work as head has access
        `when`(expenseService.createExpense(any())).thenAnswer {
            (it.arguments[0] as ExpenseDto).copy(expenseId = "head_expense")
        }
        val response = controller.createExpense(dto)
        assertEquals(HttpStatus.CREATED, response.statusCode)
    }

    @Test
    fun createExpense_familyMembershipValidation() {
        // Test family head access (should work)
        val fam = Family(
            "fam1",
            headId = baseUser.id,
            name = "Family",
            aliasName = "F",
            membersIds = mutableListOf("other-user")
        )
        val userWithFamily = baseUser.copy(familyId = fam.familyId)
        `when`(userService.findById(baseUser.id)).thenReturn(userWithFamily)
        `when`(familyRepository.findById(fam.familyId)).thenReturn(Optional.of(fam))

        val dto = expense(id = "", userId = "", familyId = fam.familyId)
        `when`(expenseService.createExpense(any())).thenAnswer {
            (it.arguments[0] as ExpenseDto).copy(expenseId = "head_expense")
        }

        val response = controller.createExpense(dto)
        assertEquals(HttpStatus.CREATED, response.statusCode)
    }

    @Test
    fun getExpensesByDateRange_futureDates() {
        val start = LocalDate.now().plusDays(1).toString()
        val end = LocalDate.now().plusDays(30).toString()
        val res = controller.getExpensesBetweenDates(start, end, 0, 10)
        assertEquals(1, res.content.size)
    }

    @Test
    fun createExpense_specialCharactersInDescription() {
        val dto = expense(id = "", userId = "", description = "Special chars: @#$%^&*()[]{}|\\;:'\",.<>?/~`")
        `when`(expenseService.createExpense(any())).thenAnswer {
            (it.arguments[0] as ExpenseDto).copy(expenseId = "special_chars_id")
        }
        val response = controller.createExpense(dto)
        assertEquals(HttpStatus.CREATED, response.statusCode)
    }

    @Test
    fun updateExpense_changeOwnership() {
        val existing = expense(userId = baseUser.id)
        val updated = existing.copy(userId = "different-user")

        // Mock the service calls properly
        `when`(expenseService.getExpenseById(existing.expenseId)).thenReturn(existing)
        `when`(expenseService.updateExpense(eq(existing.expenseId), any())).thenReturn(updated)

        // Execute the test
        val response = controller.updateExpense(existing.expenseId, updated)

        // Verify the response
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(updated, response.body)

        // Verify service interactions
        verify(expenseService).getExpenseById(existing.expenseId)
        verify(expenseService).updateExpense(eq(existing.expenseId), any())
    }

    @Test
    fun createExpense_multilineDescription() {
        val multilineDescription = """
            Line 1
            Line 2
            Line 3
        """.trimIndent()
        val dto = expense(id = "", userId = "", description = multilineDescription)
        `when`(expenseService.createExpense(any())).thenAnswer {
            (it.arguments[0] as ExpenseDto).copy(expenseId = "multiline_id")
        }
        val response = controller.createExpense(dto)
        assertEquals(HttpStatus.CREATED, response.statusCode)
    }
}
