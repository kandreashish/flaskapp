package com.lavish.expensetracker.controller

import com.lavish.expensetracker.config.PushNotificationService
import com.lavish.expensetracker.exception.ExpenseAccessDeniedException
import com.lavish.expensetracker.exception.ExpenseCreationException
import com.lavish.expensetracker.exception.ExpenseNotFoundException
import com.lavish.expensetracker.exception.ExpenseValidationException
import com.lavish.expensetracker.model.*
import com.lavish.expensetracker.repository.FamilyRepository
import com.lavish.expensetracker.repository.NotificationRepository
import com.lavish.expensetracker.service.ExpenseNotificationService
import com.lavish.expensetracker.service.ExpenseService
import com.lavish.expensetracker.service.UserDeviceService
import com.lavish.expensetracker.service.UserService
import com.lavish.expensetracker.util.AuthUtil
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.ZoneOffset

@RestController
@RequestMapping("/api/expenses")
class ExpenseController(
    private val expenseService: ExpenseService,
    private val authUtil: AuthUtil,
    private val userService: UserService,
    private val userDeviceService: UserDeviceService,
    private val familyRepository: FamilyRepository,
    private val notificationRepository: NotificationRepository,
    @Autowired private val expenseNotificationService: ExpenseNotificationService
) {
    companion object {
        const val MAX_AMOUNT = 1000000.0
    }

    private val handler = ExpenseControllerHandler(
        expenseService,
        authUtil,
        userService,
        userDeviceService,
        familyRepository,
        notificationRepository,
        expenseNotificationService
    )

    data class ExpenseNotificationRequest(val expenseId: String)

    @GetMapping
    fun getExpenses(@RequestParam(defaultValue = "0") page: Int,
                    @RequestParam(defaultValue = "10") size: Int,
                    @RequestParam(required = false) lastExpenseId: String?,
                    @RequestParam(defaultValue = "date") sortBy: String,
                    @RequestParam(defaultValue = "false") isAsc: Boolean) =
        handler.getExpenses(page, size, lastExpenseId, sortBy, isAsc)

    @GetMapping("/family")
    fun getExpensesForFamily(@RequestParam(defaultValue = "0") page: Int,
                              @RequestParam(defaultValue = "10") size: Int,
                              @RequestParam(required = false) lastExpenseId: String?,
                              @RequestParam(defaultValue = "date") sortBy: String,
                              @RequestParam(defaultValue = "false") isAsc: Boolean) =
        handler.getFamilyExpenses(page, size, lastExpenseId, sortBy, isAsc)

    @PostMapping
    fun createExpense(@RequestBody expense: ExpenseDto) = handler.createExpense(expense)

    @GetMapping("/detail/{id}")
    fun getExpenseById(@PathVariable id: String) = handler.getExpenseById(id)

    @PutMapping("/{id}")
    fun updateExpense(@PathVariable id: String, @RequestBody expense: ExpenseDto) = handler.updateExpense(id, expense)

    @DeleteMapping("/{id}")
    fun deleteExpense(@PathVariable id: String) = handler.deleteExpense(id)

    @GetMapping("/category/{category}")
    fun getExpensesByCategory(@PathVariable category: String,
                               @RequestParam(defaultValue = "0") page: Int,
                               @RequestParam(defaultValue = "10") size: Int) =
        handler.getExpensesByCategory(category, page, size)

    @GetMapping("/between-dates")
    fun getExpensesBetweenDates(@RequestParam startDate: String,
                                 @RequestParam endDate: String,
                                 @RequestParam(defaultValue = "0") page: Int,
                                 @RequestParam(defaultValue = "10") size: Int) =
        handler.getExpensesBetweenDates(startDate, endDate, page, size)

    @PostMapping("/notify")
    fun notifyExpense(@RequestBody request: ExpenseNotificationRequest) = handler.notifyExpense(request)

    @GetMapping("/monthly-sum")
    fun getMonthlyExpenseSum(@RequestParam year: Int, @RequestParam month: Int) = handler.getMonthlyExpenseSum(year, month)

    @GetMapping("/family-monthly-sum")
    fun getFamilyMonthlyExpenseSum(@RequestParam year: Int, @RequestParam month: Int) = handler.getFamilyMonthlyExpenseSum(year, month)

    @GetMapping("/since")
    fun getExpensesSince(@RequestParam lastModified: Long,
                          @RequestParam(defaultValue = "10") size: Int,
                          @RequestParam(required = false) lastExpenseId: String?,
                          @RequestParam(defaultValue = "lastModifiedOn") sortBy: String,
                          @RequestParam(defaultValue = "true") isAsc: Boolean) =
        handler.getExpensesSince(lastModified, size, lastExpenseId, sortBy, isAsc)

    @GetMapping("/since-date")
    fun getExpensesSinceDate(@RequestParam date: String,
                              @RequestParam(defaultValue = "10") size: Int,
                              @RequestParam(required = false) lastExpenseId: String?,
                              @RequestParam(defaultValue = "date") sortBy: String,
                              @RequestParam(defaultValue = "true") isAsc: Boolean) =
        handler.getExpensesSinceDate(date, size, lastExpenseId, sortBy, isAsc)

    @GetMapping("/family/since")
    fun getFamilyExpensesSince(@RequestParam lastModified: Long,
                                @RequestParam(defaultValue = "10") size: Int,
                                @RequestParam(required = false) lastExpenseId: String?,
                                @RequestParam(defaultValue = "lastModifiedOn") sortBy: String,
                                @RequestParam(defaultValue = "true") isAsc: Boolean) =
        handler.getFamilyExpensesSince(lastModified, size, lastExpenseId, sortBy, isAsc)

    @GetMapping("/family/since-date")
    fun getFamilyExpensesSinceDate(@RequestParam date: String,
                                    @RequestParam(defaultValue = "10") size: Int,
                                    @RequestParam(required = false) lastExpenseId: String?,
                                    @RequestParam(defaultValue = "date") sortBy: String,
                                    @RequestParam(defaultValue = "true") isAsc: Boolean) =
        handler.getFamilyExpensesSinceDate(date, size, lastExpenseId, sortBy, isAsc)
}
