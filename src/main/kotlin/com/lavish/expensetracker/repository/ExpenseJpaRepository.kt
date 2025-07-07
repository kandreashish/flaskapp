package com.lavish.expensetracker.repository

import com.lavish.expensetracker.model.Expense
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ExpenseJpaRepository : JpaRepository<Expense, String> {
    fun findByUserId(userId: String, pageable: Pageable): Page<Expense>
    fun findByCategory(category: String, pageable: Pageable): Page<Expense>
    fun findByDateBetween(startDate: Long, endDate: Long, pageable: Pageable): Page<Expense>
    fun findByFamilyId(familyId: String, pageable: Pageable): Page<Expense>
    fun findByUserIdAndCategory(userId: String, category: String, pageable: Pageable): Page<Expense>
    fun findByUserIdAndDateBetween(userId: String, startDate: Long, endDate: Long, pageable: Pageable): Page<Expense>
    fun countByUserId(userId: String): Long

    // Cursor-based pagination methods for date sorting
    fun findByUserIdAndDateGreaterThanOrderByDateAsc(userId: String, date: Long, pageable: Pageable): Page<Expense>
    fun findByUserIdAndDateLessThanOrderByDateDesc(userId: String, date: Long, pageable: Pageable): Page<Expense>

    // Cursor-based pagination methods for amount sorting
    fun findByUserIdAndAmountGreaterThanOrderByAmountAsc(userId: String, amount: Int, pageable: Pageable): Page<Expense>
    fun findByUserIdAndAmountLessThanOrderByAmountDesc(userId: String, amount: Int, pageable: Pageable): Page<Expense>

    // Cursor-based pagination methods for creation date sorting
    fun findByUserIdAndExpenseCreatedOnGreaterThanOrderByExpenseCreatedOnAsc(userId: String, createdOn: Long, pageable: Pageable): Page<Expense>
    fun findByUserIdAndExpenseCreatedOnLessThanOrderByExpenseCreatedOnDesc(userId: String, createdOn: Long, pageable: Pageable): Page<Expense>

    // Methods for getting expenses since a timestamp (for sync operations)
    fun findByUserIdAndLastModifiedOnGreaterThan(userId: String, lastModified: Long, pageable: Pageable): Page<Expense>
    fun findByUserIdAndExpenseCreatedOnGreaterThan(userId: String, createdOn: Long, pageable: Pageable): Page<Expense>
    fun findByUserIdAndDateGreaterThan(userId: String, date: Long, pageable: Pageable): Page<Expense>

    // Methods for getting expenses since a date (inclusive)
    fun findByUserIdAndDateGreaterThanEqual(userId: String, date: Long, pageable: Pageable): Page<Expense>
    fun findByUserIdAndLastModifiedOnGreaterThanEqual(userId: String, lastModified: Long, pageable: Pageable): Page<Expense>
    fun findByUserIdAndExpenseCreatedOnGreaterThanEqual(userId: String, createdOn: Long, pageable: Pageable): Page<Expense>
    fun findByUserIdAndDateGreaterThanEqualOrderByAmount(userId: String, date: Long, pageable: Pageable): Page<Expense>

    // Combined methods for cursor-based pagination with timestamp filtering
    fun findByUserIdAndLastModifiedOnGreaterThanAndLastModifiedOnGreaterThan(userId: String, minTimestamp: Long, cursorTimestamp: Long, pageable: Pageable): Page<Expense>
    fun findByUserIdAndLastModifiedOnGreaterThanAndLastModifiedOnLessThan(userId: String, minTimestamp: Long, cursorTimestamp: Long, pageable: Pageable): Page<Expense>
    fun findByUserIdAndExpenseCreatedOnGreaterThanAndExpenseCreatedOnGreaterThan(userId: String, minTimestamp: Long, cursorTimestamp: Long, pageable: Pageable): Page<Expense>
    fun findByUserIdAndExpenseCreatedOnGreaterThanAndExpenseCreatedOnLessThan(userId: String, minTimestamp: Long, cursorTimestamp: Long, pageable: Pageable): Page<Expense>
    fun findByUserIdAndDateGreaterThanAndDateGreaterThan(userId: String, minTimestamp: Long, cursorTimestamp: Long, pageable: Pageable): Page<Expense>
    fun findByUserIdAndDateGreaterThanAndDateLessThan(userId: String, minTimestamp: Long, cursorTimestamp: Long, pageable: Pageable): Page<Expense>

    // Combined methods for cursor-based pagination with date filtering
    fun findByUserIdAndDateGreaterThanEqualAndDateGreaterThan(userId: String, minDate: Long, cursorDate: Long, pageable: Pageable): Page<Expense>
    fun findByUserIdAndDateGreaterThanEqualAndDateLessThan(userId: String, minDate: Long, cursorDate: Long, pageable: Pageable): Page<Expense>
    fun findByUserIdAndDateGreaterThanEqualAndLastModifiedOnGreaterThan(userId: String, minDate: Long, cursorModified: Long, pageable: Pageable): Page<Expense>
    fun findByUserIdAndDateGreaterThanEqualAndLastModifiedOnLessThan(userId: String, minDate: Long, cursorModified: Long, pageable: Pageable): Page<Expense>

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.userId = :userId AND e.date >= :startDate AND e.date <= :endDate")
    fun sumExpensesByUserIdAndDateRange(@Param("userId") userId: String, @Param("startDate") startDate: Long, @Param("endDate") endDate: Long): Long
}
