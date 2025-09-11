package com.lavish.expensetracker.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Currency information model containing all details about a currency
 */
@Serializable
data class CurrencyInfo(
    @SerialName("code")
    val code: String, // ISO currency code (USD, EUR, etc.)

    @SerialName("name")
    val name: String, // Full currency name

    @SerialName("symbol")
    val symbol: String, // Currency symbol (₹, $, €, etc.)

    @SerialName("countryCode")
    val countryCode: String, // ISO country code (US, IN, GB, etc.)

    @SerialName("countryName")
    val countryName: String, // Full country name

    @SerialName("flag")
    val flag: String, // Unicode flag emoji or flag URL

    @SerialName("isSupported")
    val isSupported: Boolean = true // Whether this currency is supported in our system
)

/**
 * Response model for currency list API
 */
@Serializable
data class CurrencyListResponse(
    @SerialName("currencies")
    val currencies: List<CurrencyInfo>,

    @SerialName("totalCount")
    val totalCount: Int,

    @SerialName("supportedCount")
    val supportedCount: Int
)

/**
 * Comprehensive currency data with country information and flags
 */
object CurrencyData {

    val currencyInfoList = listOf(
        // Major currencies
        CurrencyInfo("USD", "US Dollar", "$", "US", "United States", "🇺🇸"),
        CurrencyInfo("EUR", "Euro", "€", "EU", "European Union", "🇪🇺"),
        CurrencyInfo("GBP", "British Pound Sterling", "£", "GB", "United Kingdom", "🇬🇧"),
        CurrencyInfo("JPY", "Japanese Yen", "¥", "JP", "Japan", "🇯🇵"),
        CurrencyInfo("INR", "Indian Rupee", "₹", "IN", "India", "🇮🇳"),
        CurrencyInfo("CAD", "Canadian Dollar", "C$", "CA", "Canada", "🇨🇦"),
        CurrencyInfo("AUD", "Australian Dollar", "A$", "AU", "Australia", "🇦🇺"),
        CurrencyInfo("CHF", "Swiss Franc", "Fr", "CH", "Switzerland", "🇨🇭"),
        CurrencyInfo("CNY", "Chinese Yuan", "¥", "CN", "China", "🇨🇳"),
        CurrencyInfo("SEK", "Swedish Krona", "kr", "SE", "Sweden", "🇸🇪"),

        // European currencies
        CurrencyInfo("NOK", "Norwegian Krone", "kr", "NO", "Norway", "🇳🇴"),
        CurrencyInfo("DKK", "Danish Krone", "kr", "DK", "Denmark", "🇩🇰"),
        CurrencyInfo("PLN", "Polish Zloty", "zł", "PL", "Poland", "🇵🇱"),
        CurrencyInfo("CZK", "Czech Koruna", "Kč", "CZ", "Czech Republic", "🇨🇿"),
        CurrencyInfo("HUF", "Hungarian Forint", "Ft", "HU", "Hungary", "🇭🇺"),

        // Asian currencies
        CurrencyInfo("KRW", "South Korean Won", "₩", "KR", "South Korea", "🇰🇷"),
        CurrencyInfo("SGD", "Singapore Dollar", "S$", "SG", "Singapore", "🇸🇬"),
        CurrencyInfo("HKD", "Hong Kong Dollar", "HK$", "HK", "Hong Kong", "🇭🇰"),
        CurrencyInfo("MYR", "Malaysian Ringgit", "RM", "MY", "Malaysia", "🇲🇾"),
        CurrencyInfo("THB", "Thai Baht", "฿", "TH", "Thailand", "🇹🇭"),
        CurrencyInfo("IDR", "Indonesian Rupiah", "Rp", "ID", "Indonesia", "🇮🇩"),
        CurrencyInfo("PHP", "Philippine Peso", "₱", "PH", "Philippines", "🇵🇭"),
        CurrencyInfo("VND", "Vietnamese Dong", "₫", "VN", "Vietnam", "🇻🇳"),

        // American currencies
        CurrencyInfo("BRL", "Brazilian Real", "R$", "BR", "Brazil", "🇧🇷"),
        CurrencyInfo("MXN", "Mexican Peso", "$", "MX", "Mexico", "🇲🇽"),
        CurrencyInfo("ARS", "Argentine Peso", "$", "AR", "Argentina", "🇦🇷"),
        CurrencyInfo("CLP", "Chilean Peso", "$", "CL", "Chile", "🇨🇱"),
        CurrencyInfo("COP", "Colombian Peso", "$", "CO", "Colombia", "🇨🇴"),
        CurrencyInfo("PEN", "Peruvian Sol", "S/", "PE", "Peru", "🇵🇪"),

        // Oceania
        CurrencyInfo("NZD", "New Zealand Dollar", "NZ$", "NZ", "New Zealand", "🇳🇿"),

        // Africa
        CurrencyInfo("ZAR", "South African Rand", "R", "ZA", "South Africa", "🇿🇦"),
        CurrencyInfo("EGP", "Egyptian Pound", "E£", "EG", "Egypt", "🇪🇬"),
        CurrencyInfo("NGN", "Nigerian Naira", "₦", "NG", "Nigeria", "🇳🇬"),
        CurrencyInfo("KES", "Kenyan Shilling", "KSh", "KE", "Kenya", "🇰🇪"),

        // Middle East
        CurrencyInfo("AED", "UAE Dirham", "د.إ", "AE", "United Arab Emirates", "🇦🇪"),
        CurrencyInfo("SAR", "Saudi Riyal", "ر.س", "SA", "Saudi Arabia", "🇸🇦"),
        CurrencyInfo("QAR", "Qatari Riyal", "ر.ق", "QA", "Qatar", "🇶🇦"),
        CurrencyInfo("KWD", "Kuwaiti Dinar", "د.ك", "KW", "Kuwait", "🇰🇼"),
        CurrencyInfo("BHD", "Bahraini Dinar", "ب.د", "BH", "Bahrain", "🇧🇭"),
        CurrencyInfo("OMR", "Omani Rial", "ر.ع.", "OM", "Oman", "🇴🇲"),
        CurrencyInfo("JOD", "Jordanian Dinar", "د.ا", "JO", "Jordan", "🇯🇴"),
        CurrencyInfo("LBP", "Lebanese Pound", "ل.ل", "LB", "Lebanon", "🇱🇧"),
        CurrencyInfo("ILS", "Israeli Shekel", "₪", "IL", "Israel", "🇮🇱"),
        CurrencyInfo("TRY", "Turkish Lira", "₺", "TR", "Turkey", "🇹🇷"),

        // Eastern Europe & Russia
        CurrencyInfo("RUB", "Russian Ruble", "₽", "RU", "Russia", "🇷🇺"),
        CurrencyInfo("UAH", "Ukrainian Hryvnia", "₴", "UA", "Ukraine", "🇺🇦"),
        CurrencyInfo("RON", "Romanian Leu", "lei", "RO", "Romania", "🇷🇴"),
        CurrencyInfo("BGN", "Bulgarian Lev", "лв", "BG", "Bulgaria", "🇧🇬"),
        CurrencyInfo("HRK", "Croatian Kuna", "kn", "HR", "Croatia", "🇭🇷"),
        CurrencyInfo("RSD", "Serbian Dinar", "дин", "RS", "Serbia", "🇷🇸"),

        // Additional currencies
        CurrencyInfo("ISK", "Icelandic Krona", "kr", "IS", "Iceland", "🇮🇸"),
        CurrencyInfo("TWD", "Taiwan Dollar", "NT$", "TW", "Taiwan", "🇹🇼"),
        CurrencyInfo("BDT", "Bangladeshi Taka", "৳", "BD", "Bangladesh", "🇧🇩"),
        CurrencyInfo("PKR", "Pakistani Rupee", "₨", "PK", "Pakistan", "🇵🇰"),
        CurrencyInfo("LKR", "Sri Lankan Rupee", "Rs", "LK", "Sri Lanka", "🇱🇰"),
        CurrencyInfo("NPR", "Nepalese Rupee", "₨", "NP", "Nepal", "🇳🇵"),
        CurrencyInfo("MMK", "Myanmar Kyat", "K", "MM", "Myanmar", "🇲🇲"),
        CurrencyInfo("KHR", "Cambodian Riel", "៛", "KH", "Cambodia", "🇰🇭"),
        CurrencyInfo("LAK", "Lao Kip", "₭", "LA", "Laos", "🇱🇦")
    )

    /**
     * Get currency info by currency code
     */
    fun getCurrencyByCode(code: String): CurrencyInfo? {
        return currencyInfoList.find { it.code.equals(code, ignoreCase = true) }
    }

    /**
     * Get all supported currencies
     */
    fun getSupportedCurrencies(): List<CurrencyInfo> {
        return currencyInfoList.filter { it.isSupported }
    }

    /**
     * Get currencies by region/continent
     */
    fun getCurrenciesByRegion(region: String): List<CurrencyInfo> {
        val regionCountries = when (region.lowercase()) {
            "europe" -> listOf("EU", "GB", "CH", "SE", "NO", "DK", "PL", "CZ", "HU", "RO", "BG", "HR", "RS", "IS")
            "asia" -> listOf("JP", "IN", "CN", "KR", "SG", "HK", "MY", "TH", "ID", "PH", "VN", "TW", "BD", "PK", "LK", "NP", "MM", "KH", "LA")
            "americas" -> listOf("US", "CA", "BR", "MX", "AR", "CL", "CO", "PE")
            "middle_east" -> listOf("AE", "SA", "QA", "KW", "BH", "OM", "JO", "LB", "IL", "TR")
            "africa" -> listOf("ZA", "EG", "NG", "KE")
            "oceania" -> listOf("AU", "NZ")
            else -> emptyList()
        }

        return currencyInfoList.filter { it.countryCode in regionCountries }
    }

    /**
     * Search currencies by name or code
     */
    fun searchCurrencies(query: String): List<CurrencyInfo> {
        val searchQuery = query.lowercase()
        return currencyInfoList.filter { currency ->
            currency.code.lowercase().contains(searchQuery) ||
            currency.name.lowercase().contains(searchQuery) ||
            currency.countryName.lowercase().contains(searchQuery)
        }
    }
}
