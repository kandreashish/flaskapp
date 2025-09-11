package com.lavish.expensetracker.controller

import com.lavish.expensetracker.model.CurrencyInfo
import com.lavish.expensetracker.model.CurrencyListResponse
import com.lavish.expensetracker.service.CurrencyService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST Controller for currency-related endpoints
 */
@RestController
@RequestMapping("/api/currencies")
@CrossOrigin(origins = ["*"])
class CurrencyController(
    private val currencyService: CurrencyService
) {

    /**
     * Get all available currencies with country codes, flags, and symbols
     *
     * @return List of all currencies with detailed information
     */
    @GetMapping
    fun getAllCurrencies(): ResponseEntity<CurrencyListResponse> {
        val response = currencyService.getAllCurrencies()
        return ResponseEntity.ok(response)
    }

    /**
     * Get only supported currencies
     *
     * @return List of currencies that are supported in the system
     */
    @GetMapping("/supported")
    fun getSupportedCurrencies(): ResponseEntity<CurrencyListResponse> {
        val response = currencyService.getSupportedCurrencies()
        return ResponseEntity.ok(response)
    }

    /**
     * Get popular/most used currencies
     *
     * @return List of top 10 most popular currencies
     */
    @GetMapping("/popular")
    fun getPopularCurrencies(): ResponseEntity<CurrencyListResponse> {
        val response = currencyService.getPopularCurrencies()
        return ResponseEntity.ok(response)
    }

    /**
     * Get currency information by currency code
     *
     * @param code ISO currency code (e.g., USD, EUR, INR)
     * @return Currency information including flag, symbol, and country details
     */
    @GetMapping("/{code}")
    fun getCurrencyByCode(@PathVariable code: String): ResponseEntity<CurrencyInfo> {
        val currency = currencyService.getCurrencyByCode(code)
        return if (currency != null) {
            ResponseEntity.ok(currency)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    /**
     * Get currencies by region/continent
     *
     * @param region Region name (europe, asia, americas, middle_east, africa, oceania)
     * @return List of currencies in the specified region
     */
    @GetMapping("/region/{region}")
    fun getCurrenciesByRegion(@PathVariable region: String): ResponseEntity<CurrencyListResponse> {
        val response = currencyService.getCurrenciesByRegion(region)
        return ResponseEntity.ok(response)
    }

    /**
     * Search currencies by name, code, or country name
     *
     * @param query Search query string
     * @return List of currencies matching the search criteria
     */
    @GetMapping("/search")
    fun searchCurrencies(@RequestParam query: String): ResponseEntity<CurrencyListResponse> {
        val response = currencyService.searchCurrencies(query)
        return ResponseEntity.ok(response)
    }

    /**
     * Check if a currency code is supported
     *
     * @param code ISO currency code to validate
     * @return Boolean indicating if the currency is supported
     */
    @GetMapping("/{code}/supported")
    fun isCurrencySupported(@PathVariable code: String): ResponseEntity<Map<String, Any>> {
        val isSupported = currencyService.isCurrencySupported(code)
        val currency = currencyService.getCurrencyByCode(code)

        val response = mapOf(
            "code" to code.uppercase(),
            "isSupported" to isSupported,
            "exists" to (currency != null),
            "symbol" to (currency?.symbol ?: ""),
            "name" to (currency?.name ?: "")
        )

        return ResponseEntity.ok(response)
    }

    /**
     * Get currency symbol by code
     *
     * @param code ISO currency code
     * @return Currency symbol
     */
    @GetMapping("/{code}/symbol")
    fun getCurrencySymbol(@PathVariable code: String): ResponseEntity<Map<String, String>> {
        val symbol = currencyService.getCurrencySymbol(code)
        return if (symbol != null) {
            ResponseEntity.ok(mapOf(
                "code" to code.uppercase(),
                "symbol" to symbol
            ))
        } else {
            ResponseEntity.notFound().build()
        }
    }
}
