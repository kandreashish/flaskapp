package com.lavish.expensetracker.repository

import com.lavish.expensetracker.model.Family
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface FamilyRepository : JpaRepository<Family, String> {
    fun findByHeadId(headId: String): Family?
    fun findByMembersIdsContains(memberId: String): Family?
}

