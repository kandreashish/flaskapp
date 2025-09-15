package com.lavish.expensetracker.config

import com.lavish.expensetracker.util.JsonUtils
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import java.io.ByteArrayInputStream
import java.io.BufferedReader
import java.io.InputStreamReader

class TrailingCommaRequestWrapper(
    request: HttpServletRequest,
    jsonUtils: JsonUtils
) : HttpServletRequestWrapper(request) {

    private val cachedBody: ByteArray

    init {
        // Read and process the original request body
        val originalBody = request.inputStream.readAllBytes()
        val bodyString = String(originalBody)

        // Clean trailing commas if it's JSON content
        val cleanedBody = if (isJsonContent()) {
            jsonUtils.cleanJson(bodyString)
        } else {
            bodyString
        }

        cachedBody = cleanedBody.toByteArray()
    }

    override fun getInputStream(): ServletInputStream {
        return object : ServletInputStream() {
            private val inputStream = ByteArrayInputStream(cachedBody)

            override fun read(): Int = inputStream.read()

            override fun isFinished(): Boolean = inputStream.available() == 0

            override fun isReady(): Boolean = true

            override fun setReadListener(listener: ReadListener?) {
                // Not implemented for this use case
            }
        }
    }

    override fun getReader(): BufferedReader {
        return BufferedReader(InputStreamReader(inputStream))
    }

    private fun isJsonContent(): Boolean {
        val contentType = contentType ?: return false
        return contentType.contains("application/json", ignoreCase = true)
    }
}
