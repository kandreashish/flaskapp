package com.lavish.expensetracker.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val familyId: String?,

    @SerialName("expenseCreatedOn")
    val expenseCreatedOn: Long,

    @SerialName("createdBy")
    val createdBy: String,

    @SerialName("modifiedBy")
    val modifiedBy: String,

    @SerialName("updatedUserName")
    val updatedUserName: String,

    @SerialName("lastModifiedOn")
    val lastModifiedOn: Long,

    @SerialName("synced")
    val synced: Boolean = false,

    @SerialName("deleted")
    val deleted: Boolean = false,

    @SerialName("deletedOn")
    val deletedOn: Long? = null,

    @SerialName("deletedBy")
    val deletedBy: String? = null
)
