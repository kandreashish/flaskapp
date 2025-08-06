package com.lavish.expensetracker.controller

import com.lavish.expensetracker.service.JoinRequestService
import com.lavish.expensetracker.util.ApiResponseUtil
import com.lavish.expensetracker.util.AuthUtil
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/join-requests")
@Tag(name = "Join Requests", description = "APIs for managing family join requests")
@SecurityRequirement(name = "Firebase JWT")
class JoinRequestController @Autowired constructor(
    private val joinRequestService: JoinRequestService,
    private val authUtil: AuthUtil
) {

    private val logger = LoggerFactory.getLogger(JoinRequestController::class.java)

    @PostMapping("/send")
    @Operation(summary = "Send a join request to a family")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Join request sent successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "401", description = "Unauthorized")
        ]
    )
    fun sendJoinRequest(
        @Valid @RequestBody request: SendJoinRequestDto
    ): ResponseEntity<Any> {
        return try {
            val userId = authUtil.getCurrentUserId()
            val joinRequest = joinRequestService.sendJoinRequest(
                requesterId = userId,
                familyId = request.familyId,
                message = request.message
            )
            ResponseEntity.ok(mapOf(
                "message" to "Join request sent successfully",
                "data" to joinRequest
            ))
        } catch (e: IllegalStateException) {
            logger.error("Invalid join request: ${e.message}")
            ApiResponseUtil.badRequest(e.message ?: "Invalid join request") as ResponseEntity<Any>
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid argument: ${e.message}")
            ApiResponseUtil.badRequest(e.message ?: "Invalid argument") as ResponseEntity<Any>
        } catch (e: Exception) {
            logger.error("Error sending join request", e)
            ResponseEntity.internalServerError().body(mapOf(
                "error" to "INTERNAL_ERROR",
                "message" to "Failed to send join request"
            ))
        }
    }

    @GetMapping("/sent")
    @Operation(summary = "Get all join requests sent by the current user")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Join requests retrieved successfully"),
            ApiResponse(responseCode = "401", description = "Unauthorized")
        ]
    )
    fun getSentJoinRequests(): ResponseEntity<Any> {
        return try {
            val userId = authUtil.getCurrentUserId()
            val joinRequests = joinRequestService.getSentJoinRequests(userId)
            ResponseEntity.ok(mapOf(
                "message" to "Join requests retrieved successfully",
                "data" to joinRequests
            ))
        } catch (e: Exception) {
            logger.error("Error retrieving sent join requests", e)
            ResponseEntity.internalServerError().body(mapOf(
                "error" to "INTERNAL_ERROR",
                "message" to "Failed to retrieve join requests"
            ))
        }
    }

    @GetMapping("/sent/pending")
    @Operation(summary = "Get pending join requests sent by the current user")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Pending join requests retrieved successfully"),
            ApiResponse(responseCode = "401", description = "Unauthorized")
        ]
    )
    fun getPendingSentJoinRequests(): ResponseEntity<Any> {
        return try {
            val userId = authUtil.getCurrentUserId()
            val joinRequests = joinRequestService.getPendingSentJoinRequests(userId)
            ResponseEntity.ok(mapOf(
                "message" to "Pending join requests retrieved successfully",
                "data" to joinRequests
            ))
        } catch (e: Exception) {
            logger.error("Error retrieving pending sent join requests", e)
            ResponseEntity.internalServerError().body(mapOf(
                "error" to "INTERNAL_ERROR",
                "message" to "Failed to retrieve pending join requests"
            ))
        }
    }

    @PostMapping("/cancel")
    @Operation(summary = "Cancel a pending join request")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Join request cancelled successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "401", description = "Unauthorized")
        ]
    )
    fun cancelJoinRequest(
        @Valid @RequestBody request: CancelJoinRequestDto
    ): ResponseEntity<Any> {
        return try {
            val userId = authUtil.getCurrentUserId()
            val joinRequest = joinRequestService.cancelJoinRequest(userId, request.familyId)
            ResponseEntity.ok(mapOf(
                "message" to "Join request cancelled successfully",
                "data" to joinRequest
            ))
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid argument: ${e.message}")
            ApiResponseUtil.badRequest(e.message ?: "Invalid argument") as ResponseEntity<Any>
        } catch (e: Exception) {
            logger.error("Error cancelling join request", e)
            ResponseEntity.internalServerError().body(mapOf(
                "error" to "INTERNAL_ERROR",
                "message" to "Failed to cancel join request"
            ))
        }
    }

    @GetMapping("/received/{familyId}")
    @Operation(summary = "Get pending join requests for a family")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Received join requests retrieved successfully"),
            ApiResponse(responseCode = "401", description = "Unauthorized")
        ]
    )
    fun getReceivedJoinRequests(
        @PathVariable familyId: String
    ): ResponseEntity<Any> {
        return try {
            val joinRequests = joinRequestService.getReceivedJoinRequests(familyId)
            ResponseEntity.ok(mapOf(
                "message" to "Received join requests retrieved successfully",
                "data" to joinRequests
            ))
        } catch (e: Exception) {
            logger.error("Error retrieving received join requests", e)
            ResponseEntity.internalServerError().body(mapOf(
                "error" to "INTERNAL_ERROR",
                "message" to "Failed to retrieve received join requests"
            ))
        }
    }

    @PostMapping("/accept")
    @Operation(summary = "Accept a join request")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Join request accepted successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "401", description = "Unauthorized")
        ]
    )
    fun acceptJoinRequest(
        @Valid @RequestBody request: ProcessJoinRequestDto
    ): ResponseEntity<Any> {
        return try {
            val userId = authUtil.getCurrentUserId()
            val joinRequest = joinRequestService.acceptJoinRequest(request.requestId, userId)
            ResponseEntity.ok(mapOf(
                "message" to "Join request accepted successfully",
                "data" to joinRequest
            ))
        } catch (e: IllegalStateException) {
            logger.error("Invalid join request state: ${e.message}")
            ApiResponseUtil.badRequest(e.message ?: "Invalid join request state") as ResponseEntity<Any>
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid argument: ${e.message}")
            ApiResponseUtil.badRequest(e.message ?: "Invalid argument") as ResponseEntity<Any>
        } catch (e: Exception) {
            logger.error("Error accepting join request", e)
            ResponseEntity.internalServerError().body(mapOf(
                "error" to "INTERNAL_ERROR",
                "message" to "Failed to accept join request"
            ))
        }
    }

    @PostMapping("/reject")
    @Operation(summary = "Reject a join request")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Join request rejected successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "401", description = "Unauthorized")
        ]
    )
    fun rejectJoinRequest(
        @Valid @RequestBody request: ProcessJoinRequestDto
    ): ResponseEntity<Any> {
        return try {
            val userId = authUtil.getCurrentUserId()
            val joinRequest = joinRequestService.rejectJoinRequest(request.requestId, userId)
            ResponseEntity.ok(mapOf(
                "message" to "Join request rejected successfully",
                "data" to joinRequest
            ))
        } catch (e: IllegalStateException) {
            logger.error("Invalid join request state: ${e.message}")
            ApiResponseUtil.badRequest(e.message ?: "Invalid join request state") as ResponseEntity<Any>
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid argument: ${e.message}")
            ApiResponseUtil.badRequest(e.message ?: "Invalid argument") as ResponseEntity<Any>
        } catch (e: Exception) {
            logger.error("Error rejecting join request", e)
            ResponseEntity.internalServerError().body(mapOf(
                "error" to "INTERNAL_ERROR",
                "message" to "Failed to reject join request"
            ))
        }
    }
}

// DTO classes for request/response
data class SendJoinRequestDto(
    @field:NotBlank(message = "Family ID is required")
    val familyId: String,
    val message: String? = null
)

data class CancelJoinRequestDto(
    @field:NotBlank(message = "Family ID is required")
    val familyId: String
)

data class ProcessJoinRequestDto(
    @field:NotBlank(message = "Request ID is required")
    val requestId: String
)
