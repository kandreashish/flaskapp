package com.example.expensetracker.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class ExpenseDto(
    @SerialName("expenseId")
    val expenseId: String,

    @SerialName("userId")
    val userId: String,

    @SerialName("amount")
    val amount: Int = 0,

    @SerialName("category")
    val category: String = "OTHERS", // Use string to decouple from domain enum

    @SerialName("description")
    val description: String = "",

    @SerialName("date")
    val date: Long = System.currentTimeMillis(), // Use epoch timestamp

    @SerialName("family_id")
    val familyId: String = "",

    @SerialName("expenseCreatedOn")
    val expenseCreatedOn: Long,

    @SerialName("createdBy")
    val createdBy: String,

    @SerialName("modifiedBy")
    val modifiedBy: String,

    @SerialName("lastModifiedOn")
    val lastModifiedOn: Long,

    @SerialName("synced")
    val synced: Boolean = false
)

