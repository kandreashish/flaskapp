package com.lavish.expensetracker.controller

import com.lavish.expensetracker.model.*
import com.lavish.expensetracker.service.StatisticsService
import com.lavish.expensetracker.util.AuthUtil
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(StatisticsController::class)
@ActiveProfiles("test")
class StatisticsControllerWebTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var statisticsService: StatisticsService

    @MockBean
    private lateinit var authUtil: AuthUtil

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `should return user stats for authenticated user`() {
        // Given
        val userId = "test-user-123"
        val mockStats = UserStats(
            totalExpenses = 15000.0,
            currencyPrefix = "₹",
            expenseCount = 25,
            averageExpense = 600.0,
            categoryWiseExpenses = listOf(
                CategoryExpense(
                    category = "FOOD",
                    amount = 8000.0,
                    currencyPrefix = "₹",
                    count = 15,
                    percentage = 53.33f
                ),
                CategoryExpense(
                    category = "TRANSPORT",
                    amount = 4000.0,
                    currencyPrefix = "₹",
                    count = 8,
                    percentage = 26.67f
                ),
                CategoryExpense(
                    category = "OTHERS",
                    amount = 3000.0,
                    currencyPrefix = "₹",
                    count = 2,
                    percentage = 20.0f
                )
            ),
            monthlyTrend = listOf(
                MonthlyExpense(
                    month = "2024-12",
                    amount = 15000.0,
                    currencyPrefix = "₹"
                )
            ),
            currencyWiseExpenses = listOf(
                CurrencyExpense(
                    currencyPrefix = "₹",
                    totalAmount = 15000.0,
                    count = 25,
                    averageAmount = 600.0
                )
            )
        )

        whenever(authUtil.getCurrentUserId()).thenReturn(userId)
        whenever(statisticsService.getPersonalStats(userId, "current_month")).thenReturn(mockStats)

        // When & Then
        mockMvc.perform(
            get("/stats/personal/$userId")
                .param("period", "current_month")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.totalExpenses").value(15000.0))
            .andExpect(jsonPath("$.currencyPrefix").value("₹"))
            .andExpect(jsonPath("$.expenseCount").value(25))
            .andExpect(jsonPath("$.averageExpense").value(600.0))
            .andExpect(jsonPath("$.categoryWiseExpenses").isArray)
            .andExpect(jsonPath("$.categoryWiseExpenses.length()").value(3))
            .andExpect(jsonPath("$.categoryWiseExpenses[0].category").value("FOOD"))
            .andExpect(jsonPath("$.categoryWiseExpenses[0].amount").value(8000.0))
            .andExpect(jsonPath("$.categoryWiseExpenses[0].percentage").value(53.33))
            .andExpect(jsonPath("$.monthlyTrend").isArray)
            .andExpect(jsonPath("$.monthlyTrend.length()").value(1))
            .andExpect(jsonPath("$.monthlyTrend[0].month").value("2024-12"))
            .andExpect(jsonPath("$.monthlyTrend[0].amount").value(15000.0))
            .andExpect(jsonPath("$.currencyWiseExpenses").isArray)
            .andExpect(jsonPath("$.currencyWiseExpenses.length()").value(1))
            .andExpect(jsonPath("$.currencyWiseExpenses[0].currencyPrefix").value("₹"))
            .andExpect(jsonPath("$.currencyWiseExpenses[0].totalAmount").value(15000.0))
    }

    @Test
    fun `should return 403 when user tries to access other user stats`() {
        // Given
        val currentUserId = "current-user-123"
        val requestedUserId = "other-user-456"

        whenever(authUtil.getCurrentUserId()).thenReturn(currentUserId)

        // When & Then
        mockMvc.perform(
            get("/stats/personal/$requestedUserId")
                .param("period", "current_month")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isForbidden)
    }
}