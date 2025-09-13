package com.lavish.expensetracker.controller

import com.lavish.expensetracker.model.ExpenseDto
import com.lavish.expensetracker.repository.FamilyRepository
import com.lavish.expensetracker.repository.NotificationRepository
import com.lavish.expensetracker.service.*
import com.lavish.expensetracker.util.AuthUtil
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/expenses")
class ExpenseController(
    expenseService: ExpenseService,
    authUtil: AuthUtil,
    userService: UserService,
    userDeviceService: UserDeviceService,
    familyRepository: FamilyRepository,
    notificationRepository: NotificationRepository,
    @Autowired private val expenseNotificationService: ExpenseNotificationService,
    private val currencyService: CurrencyService // Add currency service injection
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
        expenseNotificationService,
        currencyService // Pass currency service to handler
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
