package com.lavish.expensetracker.model

import jakarta.persistence.*

@Entity
@Table(name = "expenses")
data class Expense(
    @Id
    @Column(name = "expense_id")
    val expenseId: String,

    @Column(name = "user_id")
    val userId: String,

    val amount: Double,

    val category: String,

    val description: String,

    val date: Long, // Use epoch timestamp

    @Column(name = "family_id")
    val familyId: String?,

    @Column(name = "expense_created_on")
    val expenseCreatedOn: Long,

    @Column(name = "created_by")
    val createdBy: String,

    @Column(name = "modified_by")
    val modifiedBy: String,

    @Column(name = "last_modified_on")
    val lastModifiedOn: Long,

    @Column(name = "updated_user_name")
    val updatedUserName: String,

    @Column(name = "synced")
    val synced: Boolean,

    @Column(name = "deleted")
    val deleted: Boolean = false,

    @Column(name = "deleted_on")
    val deletedOn: Long? = null,

    @Column(name = "deleted_by")
    val deletedBy: String? = null,

    @Column(name = "currency_prefix")
    val currencyPrefix: String = "" // Default to empty string if not provided
)

// Mapper functions to convert between Entity and DTO
fun Expense.toDto() = ExpenseDto(
    expenseId = this.expenseId,
    userId = this.userId,
    amount = this.amount,
    category = this.category,
    description = this.description,
    date = this.date,
    familyId = this.familyId ?: "",
    expenseCreatedOn = this.expenseCreatedOn,
    createdBy = this.createdBy,
    modifiedBy = this.modifiedBy,
    lastModifiedOn = this.lastModifiedOn,
    synced = this.synced,
    updatedUserName = this.updatedUserName,
    deleted = this.deleted,
    deletedOn = this.deletedOn,
    deletedBy = this.deletedBy,
    currencyPrefix = this.currencyPrefix
)

fun ExpenseDto.toEntity() = Expense(
    expenseId = this.expenseId,
    userId = this.userId,
    amount = this.amount,
    category = this.category,
    description = this.description,
    date = this.date,
    familyId = this.familyId.takeIf { it?.isNotEmpty() == true },
    expenseCreatedOn = this.expenseCreatedOn,
    createdBy = this.createdBy,
    modifiedBy = this.modifiedBy,
    lastModifiedOn = this.lastModifiedOn,
    synced = this.synced,
    updatedUserName = this.updatedUserName,
    deleted = this.deleted,
    deletedOn = this.deletedOn,
    deletedBy = this.deletedBy,
    currencyPrefix = this.currencyPrefix
)
