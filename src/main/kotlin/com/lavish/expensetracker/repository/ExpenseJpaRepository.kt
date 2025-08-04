package com.lavish.expensetracker.repository

import com.lavish.expensetracker.model.Expense
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
interface ExpenseJpaRepository : JpaRepository<Expense, String> {
    fun findByUserId(userId: String, pageable: Pageable): Page<Expense>
    fun findByCategory(category: String, pageable: Pageable): Page<Expense>
    fun findByDateBetween(startDate: Long, endDate: Long, pageable: Pageable): Page<Expense>
    fun findByFamilyId(familyId: String, pageable: Pageable): Page<Expense>
    fun findByUserIdAndCategory(userId: String, category: String, pageable: Pageable): Page<Expense>
    fun findByUserIdAndDateBetween(userId: String, startDate: Long, endDate: Long, pageable: Pageable): Page<Expense>
    fun countByUserId(userId: String): Long
    fun countByFamilyId(familyId: String): Long

    // Cursor-based pagination methods for date sorting
    fun findByUserIdAndDateGreaterThanOrderByDateAsc(userId: String, date: Long, pageable: Pageable): Page<Expense>
    fun findByUserIdAndDateLessThanOrderByDateDesc(userId: String, date: Long, pageable: Pageable): Page<Expense>
    fun findByFamilyIdAndDateGreaterThanOrderByDateAsc(familyId: String, date: Long, pageable: Pageable): Page<Expense>
    fun findByFamilyIdAndDateLessThanOrderByDateDesc(familyId: String, date: Long, pageable: Pageable): Page<Expense>

    // Cursor-based pagination methods for amount sorting
    fun findByUserIdAndAmountGreaterThanOrderByAmountAsc(userId: String, amount: Int, pageable: Pageable): Page<Expense>
    fun findByUserIdAndAmountLessThanOrderByAmountDesc(userId: String, amount: Int, pageable: Pageable): Page<Expense>
    fun findByFamilyIdAndAmountGreaterThanOrderByAmountAsc(
        familyId: String,
        amount: Int,
        pageable: Pageable
    ): Page<Expense>

    fun findByFamilyIdAndAmountLessThanOrderByAmountDesc(
        familyId: String,
        amount: Int,
        pageable: Pageable
    ): Page<Expense>

    // Cursor-based pagination methods for creation date sorting
    fun findByUserIdAndDeletedFalseAndExpenseCreatedOnGreaterThanOrderByExpenseCreatedOnAsc(
        userId: String,
        createdOn: Long,
        pageable: Pageable
    ): Page<Expense>

    fun findByUserIdAndDeletedFalseAndExpenseCreatedOnLessThanOrderByExpenseCreatedOnDesc(
        userId: String,
        createdOn: Long,
        pageable: Pageable
    ): Page<Expense>

    fun findByFamilyIdAndDeletedFalseAndExpenseCreatedOnGreaterThanOrderByExpenseCreatedOnAsc(
        familyId: String,
        createdOn: Long,
        pageable: Pageable
    ): Page<Expense>

    fun findByFamilyIdAndDeletedFalseAndExpenseCreatedOnLessThanOrderByExpenseCreatedOnDesc(
        familyId: String,
        createdOn: Long,
        pageable: Pageable
    ): Page<Expense>

    // Cursor-based pagination methods for last modified date sorting
    fun findByUserIdAndDeletedFalseAndLastModifiedOnGreaterThanOrderByLastModifiedOnAsc(
        userId: String,
        lastModified: Long,
        pageable: Pageable
    ): Page<Expense>

    fun findByUserIdAndDeletedFalseAndLastModifiedOnLessThanOrderByLastModifiedOnDesc(
        userId: String,
        lastModified: Long,
        pageable: Pageable
    ): Page<Expense>

    fun findByFamilyIdAndDeletedFalseAndLastModifiedOnGreaterThanOrderByLastModifiedOnAsc(
        familyId: String,
        lastModified: Long,
        pageable: Pageable
    ): Page<Expense>

    fun findByFamilyIdAndDeletedFalseAndLastModifiedOnLessThanOrderByLastModifiedOnDesc(
        familyId: String,
        lastModified: Long,
        pageable: Pageable
    ): Page<Expense>

    // Methods for getting expenses since a timestamp (for sync operations)
    fun findByUserIdAndLastModifiedOnGreaterThan(userId: String, lastModified: Long, pageable: Pageable): Page<Expense>
    fun findByUserIdAndExpenseCreatedOnGreaterThan(userId: String, createdOn: Long, pageable: Pageable): Page<Expense>
    fun findByUserIdAndDateGreaterThan(userId: String, date: Long, pageable: Pageable): Page<Expense>

    // Methods for getting expenses since a date (inclusive)
    fun findByUserIdAndDateGreaterThanEqual(userId: String, date: Long, pageable: Pageable): Page<Expense>
    fun findByUserIdAndLastModifiedOnGreaterThanEqual(
        userId: String,
        lastModified: Long,
        pageable: Pageable
    ): Page<Expense>

    fun findByUserIdAndExpenseCreatedOnGreaterThanEqual(
        userId: String,
        createdOn: Long,
        pageable: Pageable
    ): Page<Expense>

    fun findByUserIdAndDateGreaterThanEqualOrderByAmount(userId: String, date: Long, pageable: Pageable): Page<Expense>

    // Combined methods for cursor-based pagination with timestamp filtering
    fun findByUserIdAndLastModifiedOnGreaterThanAndLastModifiedOnGreaterThan(
        userId: String,
        minTimestamp: Long,
        cursorTimestamp: Long,
        pageable: Pageable
    ): Page<Expense>

    fun findByUserIdAndLastModifiedOnGreaterThanAndLastModifiedOnLessThan(
        userId: String,
        minTimestamp: Long,
        cursorTimestamp: Long,
        pageable: Pageable
    ): Page<Expense>

    fun findByUserIdAndExpenseCreatedOnGreaterThanAndExpenseCreatedOnGreaterThan(
        userId: String,
        minTimestamp: Long,
        cursorTimestamp: Long,
        pageable: Pageable
    ): Page<Expense>

    fun findByUserIdAndExpenseCreatedOnGreaterThanAndExpenseCreatedOnLessThan(
        userId: String,
        minTimestamp: Long,
        cursorTimestamp: Long,
        pageable: Pageable
    ): Page<Expense>

    fun findByUserIdAndDateGreaterThanAndDateGreaterThan(
        userId: String,
        minTimestamp: Long,
        cursorTimestamp: Long,
        pageable: Pageable
    ): Page<Expense>

    fun findByUserIdAndDateGreaterThanAndDateLessThan(
        userId: String,
        minTimestamp: Long,
        cursorTimestamp: Long,
        pageable: Pageable
    ): Page<Expense>

    // Combined methods for cursor-based pagination with date filtering
    fun findByUserIdAndDateGreaterThanEqualAndDateGreaterThan(
        userId: String,
        minDate: Long,
        cursorDate: Long,
        pageable: Pageable
    ): Page<Expense>

    fun findByUserIdAndDateGreaterThanEqualAndDateLessThan(
        userId: String,
        minDate: Long,
        cursorDate: Long,
        pageable: Pageable
    ): Page<Expense>

    fun findByUserIdAndDateGreaterThanEqualAndLastModifiedOnGreaterThan(
        userId: String,
        minDate: Long,
        cursorModified: Long,
        pageable: Pageable
    ): Page<Expense>

    fun findByUserIdAndDateGreaterThanEqualAndLastModifiedOnLessThan(
        userId: String,
        minDate: Long,
        cursorModified: Long,
        pageable: Pageable
    ): Page<Expense>

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.userId = :userId AND e.deleted = false AND (:familyId IS NULL AND e.familyId IS NULL OR e.familyId = :familyId) AND e.date >= :startDate AND e.date <= :endDate")
    fun sumExpensesByUserIdAndFamilyIdAndDateRange(
        @Param("userId") userId: String,
        @Param("familyId") familyId: String?,
        @Param("startDate") startDate: Long,
        @Param("endDate") endDate: Long
    ): BigDecimal


    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.familyId = :familyId AND e.deleted = false AND e.date >= :startDate AND e.date <= :endDate")
    fun sumExpensesByFamilyIdAndDateRange(
        @Param("familyId") familyId: String,
        @Param("startDate") startDate: Long,
        @Param("endDate") endDate: Long
    ): BigDecimal

    // New methods for family expenses since timestamp
    fun findByFamilyIdAndLastModifiedOnGreaterThan(familyId: String, lastModified: Long, pageable: Pageable): Page<Expense>
    fun findByFamilyIdAndExpenseCreatedOnGreaterThan(familyId: String, expenseCreatedOn: Long, pageable: Pageable): Page<Expense>
    fun findByFamilyIdAndDateGreaterThanEqual(familyId: String, date: Long, pageable: Pageable): Page<Expense>

    @Query("SELECT COUNT(e) FROM Expense e WHERE e.userId = :userId AND (:familyId IS NULL AND e.familyId IS NULL OR e.familyId = :familyId) AND e.date >= :startDate AND e.date <= :endDate")
    fun countExpensesByUserIdAndDateRange(
        @Param("userId") userId: String,
        @Param("startDate") startDate: Long,
        @Param("endDate") endDate: Long,
        @Param("familyId") familyId: String?
    ): Long

    @Query("SELECT COUNT(e) FROM Expense e WHERE e.familyId = :familyId AND e.date >= :startDate AND e.date <= :endDate")
    fun countExpensesByFamilyIdAndDateRange(
        @Param("familyId") familyId: String,
        @Param("startDate") startDate: Long,
        @Param("endDate") endDate: Long
    ): Long

    // Combined family and user family queries using custom SQL
    @Query(
        """
        SELECT e FROM Expense e 
        WHERE e.familyId = :familyId
    """
    )
    fun findByFamilyIdOrUserFamilyId(@Param("familyId") familyId: String, pageable: Pageable): Page<Expense>

    @Query(
        """
        SELECT COUNT(e) FROM Expense e 
        WHERE e.familyId = :familyId
    """
    )
    fun countByFamilyIdOrUserFamilyId(@Param("familyId") familyId: String): Long

    // Cursor-based pagination for combined family queries
    @Query(
        """
        SELECT e FROM Expense e 
        WHERE e.familyId = :familyId
        AND e.date > :cursorDate
        ORDER BY e.date ASC
    """
    )
    fun findByFamilyIdOrUserFamilyIdAndDateGreaterThanOrderByDateAsc(
        @Param("familyId") familyId: String,
        @Param("cursorDate") cursorDate: Long,
        pageable: Pageable
    ): Page<Expense>

    @Query(
        """
        SELECT e FROM Expense e 
        WHERE e.familyId = :familyId
        AND e.date < :cursorDate
        ORDER BY e.date DESC
    """
    )
    fun findByFamilyIdOrUserFamilyIdAndDateLessThanOrderByDateDesc(
        @Param("familyId") familyId: String,
        @Param("cursorDate") cursorDate: Long,
        pageable: Pageable
    ): Page<Expense>

    @Query(
        """
        SELECT e FROM Expense e 
        WHERE e.familyId = :familyId
        AND e.amount > :cursorAmount
        ORDER BY e.amount ASC
    """
    )
    fun findByFamilyIdOrUserFamilyIdAndAmountGreaterThanOrderByAmountAsc(
        @Param("familyId") familyId: String,
        @Param("cursorAmount") cursorAmount: Int,
        pageable: Pageable
    ): Page<Expense>

    @Query(
        """
        SELECT e FROM Expense e 
        WHERE e.familyId = :familyId
        AND e.amount < :cursorAmount
        ORDER BY e.amount DESC
    """
    )
    fun findByFamilyIdOrUserFamilyIdAndAmountLessThanOrderByAmountDesc(
        @Param("familyId") familyId: String,
        @Param("cursorAmount") cursorAmount: Int,
        pageable: Pageable
    ): Page<Expense>

    @Query(
        """
        SELECT e FROM Expense e 
        WHERE e.familyId = :familyId
        AND e.expenseCreatedOn > :cursorCreated
        ORDER BY e.expenseCreatedOn ASC
    """
    )
    fun findByFamilyIdOrUserFamilyIdAndExpenseCreatedOnGreaterThanOrderByExpenseCreatedOnAsc(
        @Param("familyId") familyId: String,
        @Param("cursorCreated") cursorCreated: Long,
        pageable: Pageable
    ): Page<Expense>

    @Query(
        """
        SELECT e FROM Expense e 
        WHERE e.familyId = :familyId
        AND e.expenseCreatedOn < :cursorCreated
        ORDER BY e.expenseCreatedOn DESC
    """
    )
    fun findByFamilyIdOrUserFamilyIdAndExpenseCreatedOnLessThanOrderByExpenseCreatedOnDesc(
        @Param("familyId") familyId: String,
        @Param("cursorCreated") cursorCreated: Long,
        pageable: Pageable
    ): Page<Expense>

    @Query(
        """
        SELECT e FROM Expense e 
        WHERE e.familyId = :familyId
        AND e.lastModifiedOn > :cursorModified
        ORDER BY e.lastModifiedOn ASC
    """
    )
    fun findByFamilyIdOrUserFamilyIdAndLastModifiedOnGreaterThanOrderByLastModifiedOnAsc(
        @Param("familyId") familyId: String,
        @Param("cursorModified") cursorModified: Long,
        pageable: Pageable
    ): Page<Expense>

    @Query(
        """
        SELECT e FROM Expense e 
        WHERE e.familyId = :familyId
        AND e.lastModifiedOn < :cursorModified
        ORDER BY e.lastModifiedOn DESC
    """
    )
    fun findByFamilyIdOrUserFamilyIdAndLastModifiedOnLessThanOrderByLastModifiedOnDesc(
        @Param("familyId") familyId: String,
        @Param("cursorModified") cursorModified: Long,
        pageable: Pageable
    ): Page<Expense>

    // Personal expenses methods (where familyId is null or empty)
    fun findByUserIdAndFamilyIdIsNull(userId: String, pageable: Pageable): Page<Expense>

    // Cursor-based pagination methods for personal expenses (familyId is null/empty)
    fun findByUserIdAndFamilyIdIsNullAndDeletedFalseAndDateGreaterThanOrderByDateAsc(
        userId: String,
        date: Long,
        pageable: Pageable
    ): Page<Expense>

    fun findByUserIdAndFamilyIdIsNullAndDateLessThanOrderByDateDesc(
        userId: String,
        date: Long,
        pageable: Pageable
    ): Page<Expense>

    fun findByUserIdAndFamilyIdIsNullAndAmountGreaterThanOrderByAmountAsc(
        userId: String,
        amount: Int,
        pageable: Pageable
    ): Page<Expense>

    fun findByUserIdAndFamilyIdIsNullAndAmountLessThanOrderByAmountDesc(
        userId: String,
        amount: Int,
        pageable: Pageable
    ): Page<Expense>

    fun findByUserIdAndFamilyIdIsNullAndExpenseCreatedOnGreaterThanOrderByExpenseCreatedOnAsc(
        userId: String,
        createdOn: Long,
        pageable: Pageable
    ): Page<Expense>

    fun findByUserIdAndFamilyIdIsNullAndExpenseCreatedOnLessThanOrderByExpenseCreatedOnDesc(
        userId: String,
        createdOn: Long,
        pageable: Pageable
    ): Page<Expense>

    fun findByUserIdAndFamilyIdIsNullAndLastModifiedOnGreaterThanOrderByLastModifiedOnAsc(
        userId: String,
        lastModified: Long,
        pageable: Pageable
    ): Page<Expense>

    fun findByUserIdAndFamilyIdIsNullAndLastModifiedOnLessThanOrderByLastModifiedOnDesc(
        userId: String,
        lastModified: Long,
        pageable: Pageable
    ): Page<Expense>
}
