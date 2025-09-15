package com.lavish.expensetracker.model

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class StatsDtoSerializationTest {

    private val objectMapper = ObjectMapper()

    @Test
    fun `UserStats should serialize to JSON correctly`() {
        // Given
        val userStats = UserStats(
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

        // When
        val json = objectMapper.writeValueAsString(userStats)
        
        // Then
        assertTrue(json.contains("\"totalExpenses\":15000.0"))
        assertTrue(json.contains("\"currencyPrefix\":\"₹\""))
        assertTrue(json.contains("\"expenseCount\":25"))
        assertTrue(json.contains("\"averageExpense\":600.0"))
        assertTrue(json.contains("\"categoryWiseExpenses\":["))
        assertTrue(json.contains("\"category\":\"FOOD\""))
        assertTrue(json.contains("\"amount\":8000.0"))
        assertTrue(json.contains("\"percentage\":53.33"))
        assertTrue(json.contains("\"monthlyTrend\":["))
        assertTrue(json.contains("\"month\":\"2024-12\""))
        assertTrue(json.contains("\"currencyWiseExpenses\":["))
        assertTrue(json.contains("\"totalAmount\":15000.0"))
        
        // Verify JSON can be deserialized back
        val deserializedStats = objectMapper.readValue(json, UserStats::class.java)
        assertEquals(userStats.totalExpenses, deserializedStats.totalExpenses)
        assertEquals(userStats.currencyPrefix, deserializedStats.currencyPrefix)
        assertEquals(userStats.expenseCount, deserializedStats.expenseCount)
        assertEquals(userStats.categoryWiseExpenses.size, deserializedStats.categoryWiseExpenses.size)
    }

    @Test
    fun `CategoryExpense should serialize correctly`() {
        // Given
        val categoryExpense = CategoryExpense(
            category = "FOOD",
            amount = 8000.0,
            currencyPrefix = "₹",
            count = 15,
            percentage = 53.33f
        )

        // When
        val json = objectMapper.writeValueAsString(categoryExpense)

        // Then
        assertTrue(json.contains("\"category\":\"FOOD\""))
        assertTrue(json.contains("\"amount\":8000.0"))
        assertTrue(json.contains("\"currencyPrefix\":\"₹\""))
        assertTrue(json.contains("\"count\":15"))
        assertTrue(json.contains("\"percentage\":53.33"))
    }

    @Test
    fun `MonthlyExpense should serialize correctly`() {
        // Given
        val monthlyExpense = MonthlyExpense(
            month = "2024-12",
            amount = 15000.0,
            currencyPrefix = "₹"
        )

        // When
        val json = objectMapper.writeValueAsString(monthlyExpense)

        // Then
        assertTrue(json.contains("\"month\":\"2024-12\""))
        assertTrue(json.contains("\"amount\":15000.0"))
        assertTrue(json.contains("\"currencyPrefix\":\"₹\""))
    }

    @Test
    fun `CurrencyExpense should serialize correctly`() {
        // Given
        val currencyExpense = CurrencyExpense(
            currencyPrefix = "₹",
            totalAmount = 15000.0,
            count = 25,
            averageAmount = 600.0
        )

        // When
        val json = objectMapper.writeValueAsString(currencyExpense)

        // Then
        assertTrue(json.contains("\"currencyPrefix\":\"₹\""))
        assertTrue(json.contains("\"totalAmount\":15000.0"))
        assertTrue(json.contains("\"count\":25"))
        assertTrue(json.contains("\"averageAmount\":600.0"))
    }
}