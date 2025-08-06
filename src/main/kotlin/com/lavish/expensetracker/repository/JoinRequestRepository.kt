package com.lavish.expensetracker.repository

import com.lavish.expensetracker.model.JoinRequest
import com.lavish.expensetracker.model.JoinRequestStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface JoinRequestRepository : JpaRepository<JoinRequest, String> {
    fun findByRequesterId(requesterId: String): List<JoinRequest>
    fun findByFamilyId(familyId: String): List<JoinRequest>
    fun findByRequesterIdAndFamilyId(requesterId: String, familyId: String): JoinRequest?
    fun findByRequesterIdAndStatus(requesterId: String, status: JoinRequestStatus): List<JoinRequest>
    fun findByFamilyIdAndStatus(familyId: String, status: JoinRequestStatus): List<JoinRequest>
    fun findByRequesterIdAndFamilyIdAndStatus(requesterId: String, familyId: String, status: JoinRequestStatus): JoinRequest?
}
