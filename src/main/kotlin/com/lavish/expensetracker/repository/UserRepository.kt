package com.lavish.expensetracker.repository

import com.lavish.expensetracker.model.ExpenseUser
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ExpenseUserRepository : JpaRepository<ExpenseUser, String> {

    fun findByEmail(email: String): ExpenseUser?

    fun findByFirebaseUid(firebaseUid: String): ExpenseUser?

    fun findByAliasName(aliasName: String): ExpenseUser?

    @Query("SELECT u FROM ExpenseUser u WHERE u.familyId = :familyId")
    fun findByFamilyId(@Param("familyId") familyId: String): List<ExpenseUser>

    fun existsByEmail(email: String): Boolean

    fun existsByFirebaseUid(firebaseUid: String): Boolean
}
