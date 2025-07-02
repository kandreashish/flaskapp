package com.example.expensetracker.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class ExpenseDto(
    @SerialName("expense_id")
    val expenseId: String = "",

    @SerialName("user_id")
    val userId: String = "",

    val amount: Int = 0,

    val category: String = "OTHERS", // Use string to decouple from domain enum

    val description: String = "",

    val date: Long = System.currentTimeMillis(), // Use epoch timestamp

    @SerialName("family_id")
    val familyId: String = "",

    @SerialName("is_date_expense")
    val dateExpense: Boolean = false,

    @SerialName("expense_created_on")
    val expenseCreatedOn: Long = System.currentTimeMillis(),

    @SerialName("created_by")
    val createdBy: String = "",

    @SerialName("modified_by")
    val modifiedBy: String = "",

    @SerialName("last_modified_on")
    val lastModifiedOn: Long = System.currentTimeMillis(),

    val synced: Boolean = false
)
