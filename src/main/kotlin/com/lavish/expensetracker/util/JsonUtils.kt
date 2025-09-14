package com.lavish.expensetracker.util

import org.springframework.stereotype.Component

@Component
class JsonUtils {

    /**
     * Removes trailing commas from JSON strings to make them valid JSON.
     * This handles common cases with proper whitespace preservation.
     */
    fun removeTrailingCommas(json: String): String {
        var result = json

        // Remove trailing commas before closing braces (with any whitespace)
        result = result.replace(Regex(",\\s*}"), "}")

        // Remove trailing commas before closing brackets (with any whitespace)
        result = result.replace(Regex(",\\s*]"), "]")

        return result
    }

    /**
     * Validates and cleans JSON string
     */
    fun cleanJson(json: String): String {
        return removeTrailingCommas(json.trim())
    }
}
