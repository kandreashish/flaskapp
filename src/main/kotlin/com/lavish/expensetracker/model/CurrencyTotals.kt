package com.lavish.expensetracker.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * Response model for currency-wise expense totals
 */
@Serializable
data class CurrencyTotals(
    @SerialName("totalsByCurrency")
    val totalsByCurrency: Map<String, Double>,

    @SerialName("totalExpenseCount")
    val totalExpenseCount: Int = 0,

    @SerialName("period")
    val period: String? = null // e.g., "2024-01", "2024", "all-time"
)

/**
 * Individual currency summary
 */
@Serializable
data class CurrencySummary(
    @SerialName("currency")
    val currency: String,

    @SerialName("total")
    val total: Double,

    @SerialName("expenseCount")
    val expenseCount: Int,

    @SerialName("currencySymbol")
    val currencySymbol: String? = null
)

/**
 * Detailed currency totals response
 */
@Serializable
data class DetailedCurrencyTotals(
    @SerialName("summaries")
    val summaries: List<CurrencySummary>,

    @SerialName("totalExpenseCount")
    val totalExpenseCount: Int,

    @SerialName("period")
    val period: String? = null,

    @SerialName("dateRange")
    val dateRange: DateRange? = null
)

@Serializable
data class DateRange(
    @SerialName("startDate")
    val startDate: Long,

    @SerialName("endDate")
    val endDate: Long
)

/**
 * Currency validation and utility functions
 */
object CurrencyUtils {

    // Common currency codes and their symbols
    private val currencySymbols = mapOf(
        "USD" to "$",
        "EUR" to "€",
        "INR" to "₹",
        "GBP" to "£",
        "JPY" to "¥",
        "CAD" to "C$",
        "AUD" to "A$",
        "CHF" to "Fr",
        "CNY" to "¥",
        "SEK" to "kr",
        "NOK" to "kr",
        "DKK" to "kr",
        "PLN" to "zł",
        "CZK" to "Kč",
        "HUF" to "Ft"
    )

    private val validCurrencyCodes = setOf(
        "USD", "EUR", "INR", "GBP", "JPY", "CAD", "AUD", "CHF", "CNY",
        "SEK", "NOK", "DKK", "PLN", "CZK", "HUF", "BRL", "MXN", "KRW",
        "SGD", "HKD", "NZD", "ZAR", "TRY", "RUB"
    )

    fun isValidCurrency(currency: String): Boolean {
        return validCurrencyCodes.contains(currency.uppercase())
    }

    fun getCurrencySymbol(currency: String): String {
        return currencySymbols[currency.uppercase()] ?: currency
    }

    fun normalizeCurrency(currency: String): String {
        return currency.uppercase().trim()
    }
}
