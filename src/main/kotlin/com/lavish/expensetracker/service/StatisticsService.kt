package com.lavish.expensetracker.service

import com.lavish.expensetracker.model.*
import com.lavish.expensetracker.repository.ExpenseJpaRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Service
class StatisticsService(
    private val expenseRepository: ExpenseJpaRepository,
    private val userService: UserService
) {

    fun getPersonalStats(userId: String, period: String): UserStats {
        val dateRange = parsePeriod(period)
        val expenses = expenseRepository.findByUserIdAndDateBetween(
            userId, dateRange.first, dateRange.second, 
            org.springframework.data.domain.Pageable.unpaged()
        ).content.filter { !it.deleted }

        // Primary currency (most used currency or user's preference)
        val primaryCurrency = getUserPrimaryCurrency(userId)
        
        // Calculate total expenses in primary currency
        val totalExpenses = expenses.sumOf { it.amount }
        val expenseCount = expenses.size
        val averageExpense = if (expenseCount > 0) totalExpenses / expenseCount else 0.0

        // Category-wise expenses
        val categoryWiseExpenses = expenses
            .groupBy { it.category }
            .map { (category, categoryExpenses) ->
                val categoryTotal = categoryExpenses.sumOf { it.amount }
                val categoryCount = categoryExpenses.size
                val percentage = if (totalExpenses > 0) (categoryTotal / totalExpenses * 100).toFloat() else 0f
                CategoryExpense(
                    category = category,
                    amount = categoryTotal,
                    currencyPrefix = primaryCurrency,
                    count = categoryCount,
                    percentage = percentage
                )
            }

        // Monthly trend
        val monthlyTrend = calculateMonthlyTrend(expenses, primaryCurrency)

        // Currency-wise expenses
        val currencyWiseExpenses = expenses
            .groupBy { it.currencyPrefix.ifEmpty { it.currency } }
            .map { (currency, currencyExpenses) ->
                val currencyTotal = currencyExpenses.sumOf { it.amount }
                val currencyCount = currencyExpenses.size
                val currencyAverage = if (currencyCount > 0) currencyTotal / currencyCount else 0.0
                CurrencyExpense(
                    currencyPrefix = currency,
                    totalAmount = currencyTotal,
                    count = currencyCount,
                    averageAmount = currencyAverage
                )
            }

        return UserStats(
            totalExpenses = totalExpenses,
            currencyPrefix = primaryCurrency,
            expenseCount = expenseCount,
            averageExpense = averageExpense,
            categoryWiseExpenses = categoryWiseExpenses,
            monthlyTrend = monthlyTrend,
            currencyWiseExpenses = currencyWiseExpenses
        )
    }

    private fun parsePeriod(period: String): Pair<Long, Long> {
        return when (period) {
            "current_month" -> {
                val now = LocalDate.now()
                val startOfMonth = now.withDayOfMonth(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000
                val endOfMonth = now.withDayOfMonth(now.lengthOfMonth()).atTime(23, 59, 59).toEpochSecond(ZoneOffset.UTC) * 1000
                Pair(startOfMonth, endOfMonth)
            }
            "last_month" -> {
                val lastMonth = LocalDate.now().minusMonths(1)
                val startOfMonth = lastMonth.withDayOfMonth(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000
                val endOfMonth = lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()).atTime(23, 59, 59).toEpochSecond(ZoneOffset.UTC) * 1000
                Pair(startOfMonth, endOfMonth)
            }
            "current_year" -> {
                val now = LocalDate.now()
                val startOfYear = now.withDayOfYear(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000
                val endOfYear = now.withDayOfYear(now.lengthOfYear()).atTime(23, 59, 59).toEpochSecond(ZoneOffset.UTC) * 1000
                Pair(startOfYear, endOfYear)
            }
            else -> {
                // Default to current month
                val now = LocalDate.now()
                val startOfMonth = now.withDayOfMonth(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000
                val endOfMonth = now.withDayOfMonth(now.lengthOfMonth()).atTime(23, 59, 59).toEpochSecond(ZoneOffset.UTC) * 1000
                Pair(startOfMonth, endOfMonth)
            }
        }
    }

    private fun getUserPrimaryCurrency(userId: String): String {
        return try {
            val user = userService.findById(userId)
            user?.currencyPreference ?: "₹"
        } catch (e: Exception) {
            "₹" // Default to INR
        }
    }

    private fun calculateMonthlyTrend(expenses: List<Expense>, primaryCurrency: String): List<MonthlyExpense> {
        val monthlyData = expenses
            .groupBy { expense ->
                val date = LocalDate.ofEpochDay(expense.date / (24 * 60 * 60 * 1000))
                YearMonth.from(date)
            }
            .map { (yearMonth, monthExpenses) ->
                val monthTotal = monthExpenses.sumOf { it.amount }
                MonthlyExpense(
                    month = yearMonth.format(DateTimeFormatter.ofPattern("yyyy-MM")),
                    amount = monthTotal,
                    currencyPrefix = primaryCurrency
                )
            }
            .sortedBy { it.month }

        return monthlyData
    }
}