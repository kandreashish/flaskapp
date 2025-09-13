package com.lavish.expensetracker.service

import com.lavish.expensetracker.model.CurrencyData
import com.lavish.expensetracker.model.CurrencyInfo
import com.lavish.expensetracker.model.CurrencyListResponse
import org.springframework.stereotype.Service

/**
 * Service to handle currency-related operations
 */
@Service
class CurrencyService {

    /**
     * Get all available currencies
     */
    fun getAllCurrencies(): CurrencyListResponse {
        val currencies = CurrencyData.currencyInfoList
        return CurrencyListResponse(
            currencies = currencies,
            totalCount = currencies.size,
            supportedCount = currencies.count { it.isSupported }
        )
    }

    /**
     * Get only supported currencies
     */
    fun getSupportedCurrencies(): CurrencyListResponse {
        val currencies = CurrencyData.getSupportedCurrencies()
        return CurrencyListResponse(
            currencies = currencies,
            totalCount = currencies.size,
            supportedCount = currencies.size
        )
    }

    /**
     * Get currency by code
     */
    fun getCurrencyByCode(code: String): CurrencyInfo? {
        return CurrencyData.getCurrencyByCode(code)
    }

    /**
     * Get currencies by region
     */
    fun getCurrenciesByRegion(region: String): CurrencyListResponse {
        val currencies = CurrencyData.getCurrenciesByRegion(region)
        return CurrencyListResponse(
            currencies = currencies,
            totalCount = currencies.size,
            supportedCount = currencies.count { it.isSupported }
        )
    }

    /**
     * Search currencies by query
     */
    fun searchCurrencies(query: String): CurrencyListResponse {
        val currencies = CurrencyData.searchCurrencies(query)
        return CurrencyListResponse(
            currencies = currencies,
            totalCount = currencies.size,
            supportedCount = currencies.count { it.isSupported }
        )
    }

    /**
     * Get popular currencies (top 10 most used)
     */
    fun getPopularCurrencies(): CurrencyListResponse {
        val popularCodes = listOf("USD", "EUR", "GBP", "JPY", "INR", "CAD", "AUD", "CHF", "CNY", "BRL")
        val currencies = popularCodes.mapNotNull { CurrencyData.getCurrencyByCode(it) }

        return CurrencyListResponse(
            currencies = currencies,
            totalCount = currencies.size,
            supportedCount = currencies.count { it.isSupported }
        )
    }

    /**
     * Validate if currency code is supported
     */
    fun isCurrencySupported(code: String): Boolean {
        return CurrencyData.getCurrencyByCode(code)?.isSupported == true
    }

    /**
     * Get currency symbol by code
     */
    fun getCurrencySymbol(code: String): String? {
        return CurrencyData.getCurrencyByCode(code)?.symbol
    }

    /**
     * Get currency code by symbol (reverse lookup)
     * For example: "$" -> "USD", "₹" -> "INR", "€" -> "EUR"
     */
    fun getCurrencyCodeBySymbol(symbol: String): String? {
        return CurrencyData.currencyInfoList.find { it.symbol == symbol }?.code
    }

    /**
     * Validate if a currency symbol is supported
     */
    fun isCurrencySymbolSupported(symbol: String): Boolean {
        return CurrencyData.currencyInfoList.any { it.symbol == symbol && it.isSupported }
    }

    /**
     * Get currency info by symbol
     */
    fun getCurrencyBySymbol(symbol: String): CurrencyInfo? {
        return CurrencyData.currencyInfoList.find { it.symbol == symbol }
    }
}
