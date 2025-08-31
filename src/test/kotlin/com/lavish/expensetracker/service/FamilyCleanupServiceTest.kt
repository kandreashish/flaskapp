package com.lavish.expensetracker.service

import com.lavish.expensetracker.model.ExpenseUser
import com.lavish.expensetracker.repository.ExpenseRepository
import com.lavish.expensetracker.repository.ExpenseUserRepository
import com.lavish.expensetracker.repository.FamilyRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*

class FamilyCleanupServiceTest {

    private lateinit var userRepository: ExpenseUserRepository
    private lateinit var expenseRepository: ExpenseRepository
    private lateinit var familyRepository: FamilyRepository
    private lateinit var service: FamilyCleanupService

    @BeforeEach
    fun setUp() {
        userRepository = mock(ExpenseUserRepository::class.java)
        expenseRepository = mock(ExpenseRepository::class.java)
        familyRepository = mock(FamilyRepository::class.java)
        service = FamilyCleanupService(userRepository, expenseRepository, familyRepository)
    }

    @Test
    @DisplayName("cleanup removes orphaned family references and deletes expenses")
    fun cleanupOrphanedFamilies() {
        val orphanFamilyId = "fam-orphan"
        val orphanUser = ExpenseUser(
            id = "user-1", name = "Orphan", email = "o@example.com", aliasName = "ORPHAN1", firebaseUid = "firebase-o", familyId = orphanFamilyId
        )
        `when`(userRepository.findAll()).thenReturn(listOf(orphanUser))
        `when`(familyRepository.existsById(orphanFamilyId)).thenReturn(false)
        `when`(expenseRepository.deleteByFamilyId(orphanFamilyId)).thenReturn(5)

        service.cleanupOrphanedFamilyReferences()

        val captor = ArgumentCaptor.forClass(ExpenseUser::class.java)
        verify(userRepository).save(captor.capture())
        assertNull(captor.value.familyId)
        verify(expenseRepository).deleteByFamilyId(orphanFamilyId)
    }

    @Test
    @DisplayName("cleanup preserves valid family references and does NOT delete their expenses")
    fun cleanupValidFamiliesNoDeletion() {
        val familyId = "fam-valid"
        val validUser = ExpenseUser(
            id = "user-2", name = "Valid", email = "v@example.com", aliasName = "VALID1", firebaseUid = "firebase-v", familyId = familyId
        )
        `when`(userRepository.findAll()).thenReturn(listOf(validUser))
        `when`(familyRepository.existsById(familyId)).thenReturn(true)

        service.cleanupOrphanedFamilyReferences()

        verify(userRepository, never()).save(any())
        verify(expenseRepository, never()).deleteByFamilyId(anyString())
    }

    @Test
    @DisplayName("cleanup mixed users: only orphan updated & only orphan expenses deleted")
    fun cleanupMixedUsers() {
        val orphanId = "fam-orphan"
        val validId = "fam-valid"
        val orphanUser = ExpenseUser(
            id = "user-3", name = "Orphan", email = "o@example.com", aliasName = "ORP2", firebaseUid = "firebase-o2", familyId = orphanId
        )
        val validUser = ExpenseUser(
            id = "user-4", name = "Valid", email = "v@example.com", aliasName = "VAL2", firebaseUid = "firebase-v2", familyId = validId
        )
        `when`(userRepository.findAll()).thenReturn(listOf(orphanUser, validUser))
        `when`(familyRepository.existsById(orphanId)).thenReturn(false)
        `when`(familyRepository.existsById(validId)).thenReturn(true)
        `when`(expenseRepository.deleteByFamilyId(orphanId)).thenReturn(2)

        service.cleanupOrphanedFamilyReferences()

        val captor = ArgumentCaptor.forClass(ExpenseUser::class.java)
        verify(userRepository, times(1)).save(captor.capture())
        assertEquals(orphanUser.id, captor.value.id)
        assertNull(captor.value.familyId)
        verify(expenseRepository, times(1)).deleteByFamilyId(orphanId)
        verify(expenseRepository, never()).deleteByFamilyId(validId)
    }

    @Test
    @DisplayName("manual cleanup updates metrics and only cleans orphans")
    fun manualCleanupMetrics() {
        val orphanId = "fam-manual-orphan"
        val validId = "fam-manual-valid"
        val orphanUser = ExpenseUser(
            id = "user-m1", name = "Manual Orphan", email = "mo@example.com", aliasName = "MOR1", firebaseUid = "firebase-mo", familyId = orphanId
        )
        val validUser = ExpenseUser(
            id = "user-m2", name = "Manual Valid", email = "mv@example.com", aliasName = "MVA1", firebaseUid = "firebase-mv", familyId = validId
        )
        `when`(userRepository.findAll()).thenReturn(listOf(orphanUser, validUser))
        `when`(familyRepository.existsById(orphanId)).thenReturn(false)
        `when`(familyRepository.existsById(validId)).thenReturn(true)
        `when`(expenseRepository.deleteByFamilyId(orphanId)).thenReturn(9)

        val cleaned = service.performManualCleanup()
        assertEquals(1, cleaned)

        val captor = ArgumentCaptor.forClass(ExpenseUser::class.java)
        verify(userRepository).save(captor.capture())
        assertNull(captor.value.familyId)
        verify(expenseRepository).deleteByFamilyId(orphanId)
        verify(expenseRepository, never()).deleteByFamilyId(validId)

        val status = service.getLastRunStatus()
        assertEquals(2, status["lastRunProcessedUsers"])
        assertEquals(1, status["lastRunOrphansCleaned"])
        assertTrue((status["lastRunDurationMs"] as Long) >= 0)
    }
}

