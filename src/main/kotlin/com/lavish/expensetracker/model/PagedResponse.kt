package com.lavish.expensetracker.model

import kotlinx.serialization.Serializable

@Serializable
data class PagedResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val isFirst: Boolean,
    val isLast: Boolean,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
    val offset: Int? = null, // For offset-based pagination
    val lastExpenseId: String? = null // For cursor-based pagination
)
