package com.lavish.expensetracker.util

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
