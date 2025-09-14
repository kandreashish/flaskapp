package com.lavish.expensetracker.config

import com.lavish.expensetracker.util.JsonUtils
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(1)
class TrailingCommaFilter(private val jsonUtils: JsonUtils) : Filter {

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        if (request is HttpServletRequest && isJsonRequest(request)) {
            val wrappedRequest = TrailingCommaRequestWrapper(request, jsonUtils)
            chain.doFilter(wrappedRequest, response)
        } else {
            chain.doFilter(request, response)
        }
    }

    private fun isJsonRequest(request: HttpServletRequest): Boolean {
        val contentType = request.contentType ?: return false
        return contentType.contains("application/json", ignoreCase = true) &&
               (request.method == "POST" || request.method == "PUT" || request.method == "PATCH")
    }
}
