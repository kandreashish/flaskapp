package com.example.expensetracker.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "expenses")
data class Expense(
    @Id
    @Column(name = "expense_id")
    val expenseId: String,

    @Column(name = "user_id")
    val userId: String,

    val amount: Int,

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

    val synced: Boolean
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
    synced = this.synced
)

fun ExpenseDto.toEntity() = Expense(
    expenseId = this.expenseId,
    userId = this.userId,
    amount = this.amount,
    category = this.category,
    description = this.description,
    date = this.date,
    familyId = this.familyId.takeIf { it.isNotEmpty() },
    expenseCreatedOn = this.expenseCreatedOn,
    createdBy = this.createdBy,
    modifiedBy = this.modifiedBy,
    lastModifiedOn = this.lastModifiedOn,
    synced = this.synced,
)
