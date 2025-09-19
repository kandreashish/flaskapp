package com.lavish.expensetracker.service

import com.lavish.expensetracker.model.*
import com.lavish.expensetracker.repository.ExpenseJpaRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset

@Service
class StatisticsService(
    private val expenseRepository: ExpenseJpaRepository,
    private val userService: UserService
) {

    enum class Period {
        CURRENT_MONTH,
        LAST_MONTH,
        CURRENT_YEAR,
        LAST_YEAR,
        ALL_TIME
    }

    fun getUserStats(userId: String, period: Period): UserStats {
        val dateRange = getDateRange(period)
        val startDate = dateRange.first
        val endDate = dateRange.second

        // Get user's family ID to properly filter family expenses
        val user = userService.findById(userId)
        val userFamilyId = user?.familyId
        val primaryCurrency = user?.currencyPreference ?: "₹"

        // Get total and count including both personal and family expenses
        val totalAndCount = expenseRepository.getTotalAndCountForUserIncludingFamilyInDateRange(userId, userFamilyId, startDate, endDate)
        val totalExpenses = totalAndCount.getTotalAmount()
        val expenseCount = totalAndCount.getExpenseCount().toDouble()
        val averageExpense = if (expenseCount > 0) totalExpenses / expenseCount else 0.0

        // Get category-wise expenses including both personal and family expenses
        val categoryData = expenseRepository.getCategoryWiseExpensesForUserIncludingFamily(userId, userFamilyId, startDate, endDate)
        val categoryWiseExpenses = processCategoryData(categoryData, totalExpenses)

        // Get currency-wise expenses including both personal and family expenses
        val currencyData = expenseRepository.getCurrencyWiseExpensesForUserIncludingFamily(userId, userFamilyId, startDate, endDate)
        val currencyWiseExpenses = processCurrencyData(currencyData)

        // Get monthly trend including both personal and family expenses
        val monthlyData = expenseRepository.getMonthlyExpensesForUserIncludingFamily(userId, userFamilyId, startDate, endDate)
        val monthlyTrend = processMonthlyData(monthlyData)

        return UserStats(
            totalExpenses = totalExpenses,
            currencyPrefix = primaryCurrency,
            expenseCount = expenseCount.toInt(),
            averageExpense = averageExpense,
            categoryWiseExpenses = categoryWiseExpenses,
            monthlyTrend = monthlyTrend,
            currencyWiseExpenses = currencyWiseExpenses
        )
    }

    fun getFamilyStats(familyId: String, period: Period): FamilyStats {
        val dateRange = getDateRange(period)
        val startDate = dateRange.first
        val endDate = dateRange.second

        // Get total family expenses and count
        val totalAndCount = expenseRepository.getTotalAndCountForFamilyInDateRange(familyId, startDate, endDate)
        val totalFamilyExpenses = totalAndCount.getTotalAmount()
        val expenseCount = totalAndCount.getExpenseCount().toDouble()
        val averageExpense = if (expenseCount > 0) totalFamilyExpenses / expenseCount else 0.0

        // Get primary currency (we'll use the first member's preference or default)
        val primaryCurrency = "₹" // Default currency for family stats

        // Get family member stats
        val memberData = expenseRepository.getFamilyMemberStats(familyId, startDate, endDate)
        val memberStats = processFamilyMemberData(memberData, totalFamilyExpenses)

        // Get category-wise expenses for family
        val categoryData = expenseRepository.getCategoryWiseExpensesForFamily(familyId, startDate, endDate)
        val categoryWiseExpenses = processCategoryData(categoryData, totalFamilyExpenses)

        // Get currency-wise expenses for family
        val currencyData = expenseRepository.getCurrencyWiseExpensesForFamily(familyId, startDate, endDate)
        val currencyWiseExpenses = processCurrencyData(currencyData)

        // Get monthly trend for family
        val monthlyData = expenseRepository.getMonthlyExpensesForFamily(familyId, startDate, endDate)
        val monthlyTrend = processMonthlyData(monthlyData)

        return FamilyStats(
            totalFamilyExpenses = totalFamilyExpenses,
            currencyPrefix = primaryCurrency,
            expenseCount = expenseCount.toInt(),
            averageExpense = averageExpense,
            memberStats = memberStats,
            categoryWiseExpenses = categoryWiseExpenses,
            monthlyTrend = monthlyTrend,
            currencyWiseExpenses = currencyWiseExpenses
        )
    }

    private fun getDateRange(period: Period): Pair<Long, Long> {
        val now = LocalDate.now()
        return when (period) {
            Period.CURRENT_MONTH -> {
                val startOfMonth = now.withDayOfMonth(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000
                val endOfMonth = now.withDayOfMonth(now.lengthOfMonth()).atTime(23, 59, 59).toEpochSecond(ZoneOffset.UTC) * 1000
                Pair(startOfMonth, endOfMonth)
            }
            Period.LAST_MONTH -> {
                val lastMonth = now.minusMonths(1)
                val startOfLastMonth = lastMonth.withDayOfMonth(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000
                val endOfLastMonth = lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()).atTime(23, 59, 59).toEpochSecond(ZoneOffset.UTC) * 1000
                Pair(startOfLastMonth, endOfLastMonth)
            }
            Period.CURRENT_YEAR -> {
                val startOfYear = now.withDayOfYear(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000
                val endOfYear = now.withDayOfYear(now.lengthOfYear()).atTime(23, 59, 59).toEpochSecond(ZoneOffset.UTC) * 1000
                Pair(startOfYear, endOfYear)
            }
            Period.LAST_YEAR -> {
                val lastYear = now.minusYears(1)
                val startOfLastYear = lastYear.withDayOfYear(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000
                val endOfLastYear = lastYear.withDayOfYear(lastYear.lengthOfYear()).atTime(23, 59, 59).toEpochSecond(ZoneOffset.UTC) * 1000
                Pair(startOfLastYear, endOfLastYear)
            }
            Period.ALL_TIME -> {
                val startOfTime = 0L
                val endOfTime = System.currentTimeMillis()
                Pair(startOfTime, endOfTime)
            }
        }
    }

    private fun processCategoryData(categoryData: List<Array<Any>>, totalExpenses: Double): List<CategoryExpense> {
        return categoryData.map { row ->
            val category = ExpenseCategory.fromString(row[0] as String)
            val amount = when (val amountValue = row[1]) {
                is BigDecimal -> amountValue.toDouble()
                is Double -> amountValue
                is Number -> amountValue.toDouble()
                else -> 0.0
            }
            val count = (row[2] as Long).toInt()
            val currencyPrefix = row[3] as String
            val percentage = if (totalExpenses > 0) ((amount / totalExpenses) * 100).toFloat() else 0f

            CategoryExpense(
                category = category,
                amount = amount,
                currencyPrefix = currencyPrefix,
                count = count,
                percentage = percentage
            )
        }
    }

    private fun processCurrencyData(currencyData: List<Array<Any>>): List<CurrencyExpense> {
        return currencyData.map { row ->
            val currencyPrefix = row[0] as String
            val totalAmount = when (val amountValue = row[1]) {
                is BigDecimal -> amountValue.toDouble()
                is Double -> amountValue
                is Number -> amountValue.toDouble()
                else -> 0.0
            }
            val count = (row[2] as Long).toInt()
            val averageAmount = if (count > 0) totalAmount / count else 0.0

            CurrencyExpense(
                currencyPrefix = currencyPrefix,
                totalAmount = totalAmount,
                count = count,
                averageAmount = averageAmount
            )
        }
    }

    private fun processMonthlyData(monthlyData: List<Array<Any>>): List<MonthlyExpense> {
        return monthlyData.map { row ->
            val month = row[0] as String
            val amount = when (val amountValue = row[1]) {
                is BigDecimal -> amountValue.toDouble()
                is Double -> amountValue
                is Number -> amountValue.toDouble()
                else -> 0.0
            }
            val currencyPrefix = row[2] as String

            MonthlyExpense(
                month = month,
                amount = amount,
                currencyPrefix = currencyPrefix
            )
        }
    }

    private fun processFamilyMemberData(memberData: List<Array<Any>>, totalFamilyExpenses: Double): List<FamilyMemberStats> {
        // Group by user ID and aggregate across currencies
        val userMap = memberData.groupBy { it[0] as String }
        
        return userMap.map { (userId, rows) ->
            val user = userService.findById(userId)
            val userName = user?.name ?: user?.email ?: "Unknown User"
            val userCurrency = user?.currencyPreference ?: "₹"
            
            val totalUserExpenses = rows.sumOf {
                when (val amountValue = it[1]) {
                    is BigDecimal -> amountValue.toDouble()
                    is Double -> amountValue
                    is Number -> amountValue.toDouble()
                    else -> 0.0
                }
            }
            val totalUserCount = rows.sumOf { (it[2] as Long).toInt() }
            val percentage = if (totalFamilyExpenses > 0) ((totalUserExpenses / totalFamilyExpenses) * 100).toFloat() else 0f
            
            val currencyWiseExpenses = rows.map { row ->
                val currencyPrefix = row[3] as String
                val amount = when (val amountValue = row[1]) {
                    is BigDecimal -> amountValue.toDouble()
                    is Double -> amountValue
                    is Number -> amountValue.toDouble()
                    else -> 0.0
                }
                val count = (row[2] as Long).toInt()
                val averageAmount = if (count > 0) amount / count else 0.0
                
                CurrencyExpense(
                    currencyPrefix = currencyPrefix,
                    totalAmount = amount,
                    count = count,
                    averageAmount = averageAmount
                )
            }

            FamilyMemberStats(
                userId = userId,
                userName = userName,
                totalExpenses = totalUserExpenses,
                currencyPrefix = userCurrency,
                expenseCount = totalUserCount,
                percentage = percentage,
                currencyWiseExpenses = currencyWiseExpenses
            )
        }
    }

    companion object {
        fun parsePeriod(periodString: String): Period {
            return when (periodString.lowercase()) {
                "current_month" -> Period.CURRENT_MONTH
                "last_month" -> Period.LAST_MONTH
                "current_year" -> Period.CURRENT_YEAR
                "last_year" -> Period.LAST_YEAR
                "all_time" -> Period.ALL_TIME
                else -> Period.CURRENT_MONTH // Default
            }
        }
    }
}
