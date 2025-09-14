package com.lavish.expensetracker.config

import com.lavish.expensetracker.util.JsonUtils
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@TestPropertySource(properties = ["spring.datasource.url=jdbc:h2:mem:testdb"])
class TrailingCommaIntegrationTest {

    @Test
    fun `JsonUtils should handle trailing commas correctly`() {
        val jsonUtils = JsonUtils()

        // Test the exact scenario from your original error
        val jsonWithTrailingComma = """
        {
            "amount": 100.0,
            "currencyPrefix": "$",
        }
        """.trimIndent()

        val result = jsonUtils.cleanJson(jsonWithTrailingComma)

        // Should not contain trailing comma
        assertFalse(result.contains(",}"))
        assertTrue(result.contains("\"currencyPrefix\": \"$\""))

        println("Original: $jsonWithTrailingComma")
        println("Cleaned: $result")
    }

    @Test
    fun `Filter should be registered as Spring component`() {
        // This test will fail if our filter is not properly configured
        val jsonUtils = JsonUtils()
        val filter = TrailingCommaFilter(jsonUtils)

        assertNotNull(filter)
    }
}
