package com.lavish.expensetracker.model

import kotlinx.serialization.Serializable

@Serializable
data class UserStats(
    val totalExpenses: Double,
    val currencyPrefix: String, // Primary currency for backwards compatibility
    val expenseCount: Int,
    val averageExpense: Map<String, Double>, // Map of currency to average expense amount
    val categoryWiseExpenses: List<CategoryExpense>,
    val currencyWiseExpenses: List<CurrencyExpense>, // Multiple currencies
)

@Serializable
data class FamilyStats(
    val totalFamilyExpenses: Double,
    val currencyPrefix: String, // Primary currency for backwards compatibility
    val expenseCount: Int, // Total number of non-deleted family expenses
    val averageExpense: Map<String, Double>, // Map of currency to average expense amount for the family
    val memberStats: List<FamilyMemberStats>,
    val categoryWiseExpenses: List<CategoryExpense>,
    val currencyWiseExpenses: List<CurrencyExpense>, // Multiple currencies
)

@Serializable
data class FamilyMemberStats(
    val userId: String,
    val userName: String,
    val totalExpenses: Double,
    val currencyPrefix: String, // Primary currency for backwards compatibility
    val expenseCount: Int,
    val percentage: Float,
    val currencyWiseExpenses: List<CurrencyExpense>, // Multiple currencies
)

@Serializable
data class CategoryExpense(
    val category: ExpenseCategory,
    val amount: Double,
    val currencyPrefix: String, // Currency for this category's expenses
    val count: Int,
    val percentage: Float,
)

@Serializable
data class MonthlyExpense(
    val month: String,
    val amountsByCurrency: Map<String, Double>, // Map of currencyPrefix -> total amount for that month
)

@Serializable
data class CurrencyExpense(
    val currencyPrefix: String,
    val totalAmount: Double,
    val count: Int,
    val averageAmount: Double,
)
