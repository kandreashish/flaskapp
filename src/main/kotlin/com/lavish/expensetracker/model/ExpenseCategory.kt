package com.lavish.expensetracker.model

import kotlinx.serialization.Serializable

@Serializable
enum class ExpenseCategory {
    FOOD,
    ENTERTAINMENT,
    FUN,
    BILLS,
    TRAVEL,
    UTILITIES,
    HEALTH,
    SHOPPING,
    EDUCATION,
    OTHERS;

    companion object {
        fun fromString(category: String): ExpenseCategory {
            return valueOf(category.uppercase())
        }

        fun getAllCategories(): List<String> {
            return values().map { it.name }
        }
    }
}
