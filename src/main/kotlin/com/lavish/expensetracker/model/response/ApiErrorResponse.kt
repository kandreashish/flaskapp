package com.lavish.expensetracker.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiErrorResponse(
    @SerialName("error")
    val error: String,
    @SerialName("message")
    val message: String,
    @SerialName("validationErrors")
    val validationErrors: List<String>? = null
)
