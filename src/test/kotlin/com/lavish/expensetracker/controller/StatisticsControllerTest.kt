package com.lavish.expensetracker.controller

import com.lavish.expensetracker.model.*
import com.lavish.expensetracker.service.StatisticsService
import com.lavish.expensetracker.util.AuthUtil
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

@ExtendWith(MockitoExtension::class)
class StatisticsControllerTest {

    @Mock
    private lateinit var statisticsService: StatisticsService

    @Mock
    private lateinit var authUtil: AuthUtil

    @InjectMocks
    private lateinit var statisticsController: StatisticsController

    @Test
    fun `getPersonalStats should return user stats when authorized`() {
        // Given
        val userId = "test-user-id"
        val period = "current_month"
        val mockStats = UserStats(
            totalExpenses = 1000.0,
            currencyPrefix = "₹",
            expenseCount = 10,
            averageExpense = 100.0,
            categoryWiseExpenses = emptyList(),
            monthlyTrend = emptyList(),
            currencyWiseExpenses = emptyList()
        )

        whenever(authUtil.getCurrentUserId()).thenReturn(userId)
        whenever(statisticsService.getPersonalStats(userId, period)).thenReturn(mockStats)

        // When
        val response = statisticsController.getPersonalStats(userId, period)

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(mockStats, response.body)
    }

    @Test
    fun `getPersonalStats should throw forbidden when accessing different user stats`() {
        // Given
        val requestedUserId = "other-user-id"
        val currentUserId = "current-user-id"
        val period = "current_month"

        whenever(authUtil.getCurrentUserId()).thenReturn(currentUserId)

        // When & Then
        val exception = assertThrows(ResponseStatusException::class.java) {
            statisticsController.getPersonalStats(requestedUserId, period)
        }
        assertEquals(HttpStatus.FORBIDDEN, exception.statusCode)
    }
}