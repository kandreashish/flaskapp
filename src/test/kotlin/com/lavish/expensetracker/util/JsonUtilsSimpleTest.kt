package com.lavish.expensetracker.util

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class JsonUtilsSimpleTest {

    private val jsonUtils = JsonUtils()

    @Test
    fun `basic trailing comma removal test`() {
        val input = """{"name": "test",}"""
        val expected = """{"name": "test"}"""
        val result = jsonUtils.removeTrailingCommas(input)
        assertEquals(expected, result)
    }

    @Test
    fun `array trailing comma removal test`() {
        val input = """{"items": [1, 2, 3,]}"""
        val expected = """{"items": [1, 2, 3]}"""
        val result = jsonUtils.removeTrailingCommas(input)
        assertEquals(expected, result)
    }

    @Test
    fun `valid JSON should remain unchanged`() {
        val validJson = """{"name": "test", "value": 123}"""
        val result = jsonUtils.removeTrailingCommas(validJson)
        assertEquals(validJson, result)
    }
}
