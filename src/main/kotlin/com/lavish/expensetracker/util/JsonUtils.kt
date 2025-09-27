package com.lavish.expensetracker.util

import org.springframework.stereotype.Component

@Component
class JsonUtils {

    /**
     * Removes trailing commas from JSON strings to make them valid JSON.
     * Now also trims any whitespace immediately before the dangling comma so
     * patterns like "value" ,   } become "value"} matching test expectations.
     */
    fun removeTrailingCommas(json: String): String {
        var result = json
        // Remove trailing commas (and any surrounding whitespace) before closing braces
        result = result.replace(Regex("\\s*,\\s*}"), "}")
        // Remove trailing commas (and any surrounding whitespace) before closing brackets
        result = result.replace(Regex("\\s*,\\s*]"), "]")
        return result
    }

    /**
     * Validates and cleans JSON string
     */
    fun cleanJson(json: String): String {
        return removeTrailingCommas(json.trim())
    }
}
