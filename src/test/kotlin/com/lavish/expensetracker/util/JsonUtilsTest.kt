package com.lavish.expensetracker.util

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class JsonUtilsTest {

    private val jsonUtils = JsonUtils()

    @Test
    fun `should remove trailing comma before closing brace`() {
        val jsonWithTrailingComma = """{"amount": 100.0,"currencyPrefix": "$",}"""
        val expected = """{"amount": 100.0,"currencyPrefix": "$"}"""

        val result = jsonUtils.removeTrailingCommas(jsonWithTrailingComma)
        assertEquals(expected, result)
    }

    @Test
    fun `should remove trailing comma before closing bracket`() {
        val jsonWithTrailingComma = """{"categories": ["FOOD", "TRANSPORT", "ENTERTAINMENT",]}"""
        val expected = """{"categories": ["FOOD", "TRANSPORT", "ENTERTAINMENT"]}"""

        val result = jsonUtils.removeTrailingCommas(jsonWithTrailingComma)
        assertEquals(expected, result)
    }

    @Test
    fun `should handle trailing commas with whitespace`() {
        val jsonWithTrailingComma = """{"amount": 100.0, "description": "test" ,  }"""
        val expected = """{"amount": 100.0, "description": "test"}"""

        val result = jsonUtils.removeTrailingCommas(jsonWithTrailingComma)
        assertEquals(expected, result)
    }

    @Test
    fun `should handle nested objects with trailing commas`() {
        val jsonWithNestedTrailingCommas = """{"expense": {"amount": 100.0,"currency": "USD",},"user": {"id": "123","name": "John",}}"""
        val expected = """{"expense": {"amount": 100.0,"currency": "USD"},"user": {"id": "123","name": "John"}}"""

        val result = jsonUtils.removeTrailingCommas(jsonWithNestedTrailingCommas)
        assertEquals(expected, result)
    }

    @Test
    fun `should not modify valid JSON without trailing commas`() {
        val validJson = """{"amount": 100.0,"currencyPrefix": "$","description": "Valid JSON"}"""

        val result = jsonUtils.removeTrailingCommas(validJson)
        assertEquals(validJson, result)
    }

    @Test
    fun `should handle mixed trailing commas in arrays and objects`() {
        val complexJson = """{"expenses": [{"amount": 100.0,"category": "FOOD",},{"amount": 50.0,"category": "TRANSPORT",},],"totalSum": 150.0,}"""
        val expected = """{"expenses": [{"amount": 100.0,"category": "FOOD"},{"amount": 50.0,"category": "TRANSPORT"}],"totalSum": 150.0}"""

        val result = jsonUtils.removeTrailingCommas(complexJson)
        assertEquals(expected, result)
    }

    @Test
    fun `cleanJson should trim whitespace and remove trailing commas`() {
        val jsonWithWhitespaceAndTrailingCommas = """  {"amount": 100.0,"description": "test",}  """
        val expected = """{"amount": 100.0,"description": "test"}"""

        val result = jsonUtils.cleanJson(jsonWithWhitespaceAndTrailingCommas)
        assertEquals(expected, result)
    }
}
