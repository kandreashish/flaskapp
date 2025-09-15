package com.lavish.expensetracker.model

import com.fasterxml.jackson.annotation.JsonProperty

data class UserStats(
    @JsonProperty("totalExpenses")
    val totalExpenses: Double,
    @JsonProperty("currencyPrefix")
    val currencyPrefix: String, // Primary currency for backwards compatibility
    @JsonProperty("expenseCount")
    val expenseCount: Int,
    @JsonProperty("averageExpense")
    val averageExpense: Double,
    @JsonProperty("categoryWiseExpenses")
    val categoryWiseExpenses: List<CategoryExpense>,
    @JsonProperty("monthlyTrend")
    val monthlyTrend: List<MonthlyExpense>,
    @JsonProperty("currencyWiseExpenses")
    val currencyWiseExpenses: List<CurrencyExpense> // New field for multiple currencies
)

data class FamilyStats(
    @JsonProperty("totalFamilyExpenses")
    val totalFamilyExpenses: Double,
    @JsonProperty("currencyPrefix")
    val currencyPrefix: String, // Primary currency for backwards compatibility
    @JsonProperty("memberStats")
    val memberStats: List<FamilyMemberStats>,
    @JsonProperty("categoryWiseExpenses")
    val categoryWiseExpenses: List<CategoryExpense>,
    @JsonProperty("monthlyTrend")
    val monthlyTrend: List<MonthlyExpense>,
    @JsonProperty("currencyWiseExpenses")
    val currencyWiseExpenses: List<CurrencyExpense> // New field for multiple currencies
)

data class FamilyMemberStats(
    @JsonProperty("userId")
    val userId: String,
    @JsonProperty("userName")
    val userName: String,
    @JsonProperty("totalExpenses")
    val totalExpenses: Double,
    @JsonProperty("currencyPrefix")
    val currencyPrefix: String, // Primary currency for backwards compatibility
    @JsonProperty("expenseCount")
    val expenseCount: Int,
    @JsonProperty("percentage")
    val percentage: Float,
    @JsonProperty("currencyWiseExpenses")
    val currencyWiseExpenses: List<CurrencyExpense> // New field for multiple currencies
)

data class CategoryExpense(
    @JsonProperty("category")
    val category: String, // Using String instead of ExpenseCategory enum for simplicity
    @JsonProperty("amount")
    val amount: Double,
    @JsonProperty("currencyPrefix")
    val currencyPrefix: String, // Currency for this category's expenses
    @JsonProperty("count")
    val count: Int,
    @JsonProperty("percentage")
    val percentage: Float
)

data class MonthlyExpense(
    @JsonProperty("month")
    val month: String,
    @JsonProperty("amount")
    val amount: Double,
    @JsonProperty("currencyPrefix")
    val currencyPrefix: String // Currency for this month's expenses
)

data class CurrencyExpense(
    @JsonProperty("currencyPrefix")
    val currencyPrefix: String,
    @JsonProperty("totalAmount")
    val totalAmount: Double,
    @JsonProperty("count")
    val count: Int,
    @JsonProperty("averageAmount")
    val averageAmount: Double
)