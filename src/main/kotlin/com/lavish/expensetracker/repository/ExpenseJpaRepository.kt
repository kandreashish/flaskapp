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
    fun findByUserIdAndCategory(userId: String, category: String, pageable: Pageable): Page<Expense>
    fun findByUserIdAndDateBetween(userId: String, startDate: Long, endDate: Long, pageable: Pageable): Page<Expense>
    fun countByUserId(userId: String): Long

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
        WHERE e.familyId = :familyId AND e.deleted = false
    """
    )
    fun findByFamilyIdOrUserFamilyId(@Param("familyId") familyId: String, pageable: Pageable): Page<Expense>

    @Query(
        """
        SELECT COUNT(e) FROM Expense e 
        WHERE e.familyId = :familyId
        AND e.deleted = false
    """
    )
    fun countByFamilyIdOrUserFamilyId(@Param("familyId") familyId: String): Long

    // Cursor-based pagination for combined family queries
    @Query(
        """
        SELECT e FROM Expense e 
        WHERE e.familyId = :familyId
        AND e.date > :cursorDate
        AND e.deleted = false
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
        AND e.deleted = false
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
        AND e.deleted = false
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
        AND e.deleted = false
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
        AND e.deleted = false
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
        AND e.deleted = false
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
        AND e.deleted = false
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
        AND e.deleted = false
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
    fun findByUserIdAndFamilyIdIsNullAndDateGreaterThanOrderByDateAsc(
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
        amount: Double,
        pageable: Pageable
    ): Page<Expense>

    fun findByUserIdAndFamilyIdIsNullAndAmountLessThanOrderByAmountDesc(
        userId: String,
        amount: Double,
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

    // Multi-currency support methods

    /**
     * Get total expenses grouped by currency for a user
     */
    @Query("""
        SELECT e.currency, SUM(e.amount), COUNT(e) 
        FROM Expense e 
        WHERE e.userId = :userId AND e.deleted = false 
        GROUP BY e.currency
    """)
    fun getTotalsByCurrencyForUser(@Param("userId") userId: String): List<Array<Any>>

    /**
     * Get total expenses grouped by currency for a user within date range
     */
    @Query("""
        SELECT e.currency, SUM(e.amount), COUNT(e) 
        FROM Expense e 
        WHERE e.userId = :userId AND e.deleted = false 
        AND e.date BETWEEN :startDate AND :endDate
        GROUP BY e.currency
    """)
    fun getTotalsByCurrencyForUserAndDateRange(
        @Param("userId") userId: String,
        @Param("startDate") startDate: Long,
        @Param("endDate") endDate: Long
    ): List<Array<Any>>

    /**
     * Get total expenses grouped by currency for a family
     */
    @Query("""
        SELECT e.currency, SUM(e.amount), COUNT(e) 
        FROM Expense e 
        WHERE e.familyId = :familyId AND e.deleted = false 
        GROUP BY e.currency
    """)
    fun getTotalsByCurrencyForFamily(@Param("familyId") familyId: String): List<Array<Any>>

    /**
     * Get total expenses grouped by currency for a family within date range
     */
    @Query("""
        SELECT e.currency, SUM(e.amount), COUNT(e) 
        FROM Expense e 
        WHERE e.familyId = :familyId AND e.deleted = false 
        AND e.date BETWEEN :startDate AND :endDate
        GROUP BY e.currency
    """)
    fun getTotalsByCurrencyForFamilyAndDateRange(
        @Param("familyId") familyId: String,
        @Param("startDate") startDate: Long,
        @Param("endDate") endDate: Long
    ): List<Array<Any>>

    /**
     * Get expenses by user and currency
     */
    fun findByUserIdAndCurrency(userId: String, currency: String, pageable: Pageable): Page<Expense>

    /**
     * Get expenses by user, currency and date range
     */
    fun findByUserIdAndCurrencyAndDateBetween(
        userId: String,
        currency: String,
        startDate: Long,
        endDate: Long,
        pageable: Pageable
    ): Page<Expense>

    /**
     * Get total amount for user in specific currency
     */
    @Query("""
        SELECT COALESCE(SUM(e.amount), 0) 
        FROM Expense e 
        WHERE e.userId = :userId AND e.currency = :currency AND e.deleted = false
    """)
    fun getTotalForUserAndCurrency(@Param("userId") userId: String, @Param("currency") currency: String): Double

    /**
     * Get all unique currencies used by a user
     */
    @Query("""
        SELECT DISTINCT e.currency 
        FROM Expense e 
        WHERE e.userId = :userId AND e.deleted = false
    """)
    fun getDistinctCurrenciesForUser(@Param("userId") userId: String): List<String>

    /**
     * Get all unique currencies used in a family
     */
    @Query("""
        SELECT DISTINCT e.currency 
        FROM Expense e 
        WHERE e.familyId = :familyId AND e.deleted = false
    """)
    fun getDistinctCurrenciesForFamily(@Param("familyId") familyId: String): List<String>

    // Currency-based aggregation methods
    @Query("SELECT e.currency, COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.userId = :userId AND e.deleted = false AND (:familyId IS NULL AND e.familyId IS NULL OR e.familyId = :familyId) AND e.date >= :startDate AND e.date <= :endDate GROUP BY e.currency")
    fun sumExpensesByUserIdAndFamilyIdAndDateRangeGroupByCurrency(
        @Param("userId") userId: String,
        @Param("familyId") familyId: String?,
        @Param("startDate") startDate: Long,
        @Param("endDate") endDate: Long
    ): List<Array<Any>>

    @Query("SELECT e.currency, COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.familyId = :familyId AND e.deleted = false AND e.date >= :startDate AND e.date <= :endDate GROUP BY e.currency")
    fun sumExpensesByFamilyIdAndDateRangeGroupByCurrency(
        @Param("familyId") familyId: String,
        @Param("startDate") startDate: Long,
        @Param("endDate") endDate: Long
    ): List<Array<Any>>

    // Statistics methods
    
    /**
     * Get total expenses and count for a user in a date range
     */
    @Query("""
        SELECT COALESCE(SUM(e.amount), 0) as totalAmount, COUNT(e) as expenseCount
        FROM Expense e 
        WHERE e.userId = :userId AND e.deleted = false AND e.familyId IS NULL
        AND e.date >= :startDate AND e.date <= :endDate
    """)
    fun getTotalAndCountForUserInDateRange(
        @Param("userId") userId: String,
        @Param("startDate") startDate: Long,
        @Param("endDate") endDate: Long
    ): TotalAndCountProjection

    interface TotalAndCountProjection {
        fun getTotalAmount(): Double
        fun getExpenseCount(): Long
    }

    /**
     * Get category-wise expenses for a user in a date range
     */
    @Query("""
        SELECT e.category, COALESCE(SUM(e.amount), 0), COUNT(e), e.currencyPrefix
        FROM Expense e 
        WHERE e.userId = :userId AND e.deleted = false AND e.familyId IS NULL
        AND e.date >= :startDate AND e.date <= :endDate
        GROUP BY e.category, e.currencyPrefix
    """)
    fun getCategoryWiseExpensesForUser(
        @Param("userId") userId: String,
        @Param("startDate") startDate: Long,
        @Param("endDate") endDate: Long
    ): List<Array<Any>>

    /**
     * Get currency-wise expenses for a user in a date range
     */
    @Query("""
        SELECT e.currencyPrefix, COALESCE(SUM(e.amount), 0), COUNT(e)
        FROM Expense e 
        WHERE e.userId = :userId AND e.deleted = false AND e.familyId IS NULL
        AND e.date >= :startDate AND e.date <= :endDate
        GROUP BY e.currencyPrefix
    """)
    fun getCurrencyWiseExpensesForUser(
        @Param("userId") userId: String,
        @Param("startDate") startDate: Long,
        @Param("endDate") endDate: Long
    ): List<Array<Any>>

    /**
     * Get monthly expenses for a user
     */
    @Query(value = """
        SELECT 
            FORMATDATETIME(DATEADD('MILLISECOND', e.date, DATE '1970-01-01'), 'yyyy-MM') as expense_month,
            COALESCE(SUM(e.amount), 0),
            e.currency_prefix
        FROM expenses e 
        WHERE e.user_id = :userId AND e.deleted = false AND e.family_id IS NULL
        AND e.date >= :startDate AND e.date <= :endDate
        GROUP BY FORMATDATETIME(DATEADD('MILLISECOND', e.date, DATE '1970-01-01'), 'yyyy-MM'), e.currency_prefix
        ORDER BY FORMATDATETIME(DATEADD('MILLISECOND', e.date, DATE '1970-01-01'), 'yyyy-MM')
    """, nativeQuery = true)
    fun getMonthlyExpensesForUser(
        @Param("userId") userId: String,
        @Param("startDate") startDate: Long,
        @Param("endDate") endDate: Long
    ): List<Array<Any>>

    /**
     * Get family member stats for a family in a date range
     */
    @Query("""
        SELECT e.userId, COALESCE(SUM(e.amount), 0), COUNT(e), e.currencyPrefix
        FROM Expense e 
        WHERE e.familyId = :familyId AND e.deleted = false 
        AND e.date >= :startDate AND e.date <= :endDate
        GROUP BY e.userId, e.currencyPrefix
    """)
    fun getFamilyMemberStats(
        @Param("familyId") familyId: String,
        @Param("startDate") startDate: Long,
        @Param("endDate") endDate: Long
    ): List<Array<Any>>

    /**
     * Get total family expenses and count in a date range
     */
    @Query("""
        SELECT COALESCE(SUM(e.amount), 0) as totalAmount, COUNT(e) as expenseCount
        FROM Expense e 
        WHERE e.familyId = :familyId AND e.deleted = false 
        AND e.date >= :startDate AND e.date <= :endDate
    """)
    fun getTotalAndCountForFamilyInDateRange(
        @Param("familyId") familyId: String,
        @Param("startDate") startDate: Long,
        @Param("endDate") endDate: Long
    ): TotalAndCountProjection

    /**
     * Get category-wise expenses for a family in a date range
     */
    @Query("""
        SELECT e.category, COALESCE(SUM(e.amount), 0), COUNT(e), e.currencyPrefix
        FROM Expense e 
        WHERE e.familyId = :familyId AND e.deleted = false 
        AND e.date >= :startDate AND e.date <= :endDate
        GROUP BY e.category, e.currencyPrefix
    """)
    fun getCategoryWiseExpensesForFamily(
        @Param("familyId") familyId: String,
        @Param("startDate") startDate: Long,
        @Param("endDate") endDate: Long
    ): List<Array<Any>>

    /**
     * Get currency-wise expenses for a family in a date range
     */
    @Query("""
        SELECT e.currencyPrefix, COALESCE(SUM(e.amount), 0), COUNT(e)
        FROM Expense e 
        WHERE e.familyId = :familyId AND e.deleted = false 
        AND e.date >= :startDate AND e.date <= :endDate
        GROUP BY e.currencyPrefix
    """)
    fun getCurrencyWiseExpensesForFamily(
        @Param("familyId") familyId: String,
        @Param("startDate") startDate: Long,
        @Param("endDate") endDate: Long
    ): List<Array<Any>>

    /**
     * Get monthly expenses for a family
     */
    @Query(value = """
        SELECT 
            FORMATDATETIME(DATEADD('MILLISECOND', e.date, DATE '1970-01-01'), 'yyyy-MM') as expense_month,
            COALESCE(SUM(e.amount), 0),
            e.currency_prefix
        FROM expenses e 
        WHERE e.family_id = :familyId AND e.deleted = false 
        AND e.date >= :startDate AND e.date <= :endDate
        GROUP BY FORMATDATETIME(DATEADD('MILLISECOND', e.date, DATE '1970-01-01'), 'yyyy-MM'), e.currency_prefix
        ORDER BY FORMATDATETIME(DATEADD('MILLISECOND', e.date, DATE '1970-01-01'), 'yyyy-MM')
    """, nativeQuery = true)
    fun getMonthlyExpensesForFamily(
        @Param("familyId") familyId: String,
        @Param("startDate") startDate: Long,
        @Param("endDate") endDate: Long
    ): List<Array<Any>>

    /**
     * Get total and count for user including both personal and family expenses
     * Personal expenses: familyId IS NULL
     * Family expenses: familyId matches user's familyId (user must be member of that family)
     */
    @Query("""
        SELECT COALESCE(SUM(e.amount), 0) as totalAmount, COUNT(e) as expenseCount
        FROM Expense e 
        WHERE e.userId = :userId AND e.deleted = false
        AND e.date >= :startDate AND e.date <= :endDate
        AND (e.familyId IS NULL OR e.familyId = :userFamilyId)
    """)
    fun getTotalAndCountForUserIncludingFamilyInDateRange(
        @Param("userId") userId: String,
        @Param("userFamilyId") userFamilyId: String?,
        @Param("startDate") startDate: Long,
        @Param("endDate") endDate: Long
    ): TotalAndCountProjection

    /**
     * Get category-wise expenses for a user including both personal and family expenses
     * Personal expenses: familyId IS NULL
     * Family expenses: familyId matches user's familyId (user must be member of that family)
     */
    @Query("""
        SELECT e.category, COALESCE(SUM(e.amount), 0), COUNT(e), e.currencyPrefix
        FROM Expense e 
        WHERE e.userId = :userId AND e.deleted = false
        AND e.date >= :startDate AND e.date <= :endDate
        AND (e.familyId IS NULL OR e.familyId = :userFamilyId)
        GROUP BY e.category, e.currencyPrefix
    """)
    fun getCategoryWiseExpensesForUserIncludingFamily(
        @Param("userId") userId: String,
        @Param("userFamilyId") userFamilyId: String?,
        @Param("startDate") startDate: Long,
        @Param("endDate") endDate: Long
    ): List<Array<Any>>

    /**
     * Get currency-wise expenses for a user including both personal and family expenses
     * Personal expenses: familyId IS NULL
     * Family expenses: familyId matches user's familyId (user must be member of that family)
     */
    @Query("""
        SELECT e.currencyPrefix, COALESCE(SUM(e.amount), 0), COUNT(e)
        FROM Expense e 
        WHERE e.userId = :userId AND e.deleted = false
        AND e.date >= :startDate AND e.date <= :endDate
        AND (e.familyId IS NULL OR e.familyId = :userFamilyId)
        GROUP BY e.currencyPrefix
    """)
    fun getCurrencyWiseExpensesForUserIncludingFamily(
        @Param("userId") userId: String,
        @Param("userFamilyId") userFamilyId: String?,
        @Param("startDate") startDate: Long,
        @Param("endDate") endDate: Long
    ): List<Array<Any>>

    /**
     * Get monthly expenses for a user including both personal and family expenses
     * Personal expenses: familyId IS NULL
     * Family expenses: familyId matches user's familyId (user must be member of that family)
     */
    @Query(value = """
        SELECT 
            FORMATDATETIME(DATEADD('MILLISECOND', e.date, DATE '1970-01-01'), 'yyyy-MM') as expense_month,
            COALESCE(SUM(e.amount), 0),
            e.currency_prefix
        FROM expenses e 
        WHERE e.user_id = :userId AND e.deleted = false
        AND e.date >= :startDate AND e.date <= :endDate
        AND (e.family_id IS NULL OR e.family_id = :userFamilyId)
        GROUP BY FORMATDATETIME(DATEADD('MILLISECOND', e.date, DATE '1970-01-01'), 'yyyy-MM'), e.currency_prefix
        ORDER BY FORMATDATETIME(DATEADD('MILLISECOND', e.date, DATE '1970-01-01'), 'yyyy-MM')
    """, nativeQuery = true)
    fun getMonthlyExpensesForUserIncludingFamily(
        @Param("userId") userId: String,
        @Param("userFamilyId") userFamilyId: String?,
        @Param("startDate") startDate: Long,
        @Param("endDate") endDate: Long
    ): List<Array<Any>>
}
