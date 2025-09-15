package com.lavish.expensetracker.model

import kotlinx.serialization.Serializable

@Serializable
data class UserStats(
    val totalExpenses: Double,
    val currencyPrefix: String, // Primary currency for backwards compatibility
    val expenseCount: Int,
    val averageExpense: Double,
    val categoryWiseExpenses: List<CategoryExpense>,
    val monthlyTrend: List<MonthlyExpense>,
    val currencyWiseExpenses: List<CurrencyExpense> // New field for multiple currencies
)

@Serializable
data class FamilyStats(
    val totalFamilyExpenses: Double,
    val currencyPrefix: String, // Primary currency for backwards compatibility
    val memberStats: List<FamilyMemberStats>,
    val categoryWiseExpenses: List<CategoryExpense>,
    val monthlyTrend: List<MonthlyExpense>,
    val currencyWiseExpenses: List<CurrencyExpense> // New field for multiple currencies
)

@Serializable
data class FamilyMemberStats(
    val userId: String,
    val userName: String,
    val totalExpenses: Double,
    val currencyPrefix: String, // Primary currency for backwards compatibility
    val expenseCount: Int,
    val percentage: Float,
    val currencyWiseExpenses: List<CurrencyExpense> // New field for multiple currencies
)

@Serializable
data class CategoryExpense(
    val category: String, // Using String instead of ExpenseCategory enum for simplicity
    val amount: Double,
    val currencyPrefix: String, // Currency for this category's expenses
    val count: Int,
    val percentage: Float
)

@Serializable
data class MonthlyExpense(
    val month: String,
    val amount: Double,
    val currencyPrefix: String // Currency for this month's expenses
)

@Serializable
data class CurrencyExpense(
    val currencyPrefix: String,
    val totalAmount: Double,
    val count: Int,
    val averageAmount: Double
)