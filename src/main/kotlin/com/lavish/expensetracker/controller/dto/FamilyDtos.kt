package com.lavish.expensetracker.controller.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// Shared DTOs formerly inside FamilyController
object FamilyDtoConstraints {
    const val FAMILY_ALIAS_LENGTH = 6
    const val FAMILY_NAME_MIN_LENGTH = 2
    const val FAMILY_NAME_MAX_LENGTH = 100
}

data class CreateFamilyRequest(
    @field:NotBlank(message = "Family name is required")
    @field:Size(
        min = FamilyDtoConstraints.FAMILY_NAME_MIN_LENGTH,
        max = FamilyDtoConstraints.FAMILY_NAME_MAX_LENGTH,
        message = "Family name must be between ${FamilyDtoConstraints.FAMILY_NAME_MIN_LENGTH} and ${FamilyDtoConstraints.FAMILY_NAME_MAX_LENGTH} characters"
    )
    val familyName: String,
)

data class JoinFamilyRequest(
    @field:NotBlank(message = "Alias name is required")
    @field:Size(
        min = FamilyDtoConstraints.FAMILY_ALIAS_LENGTH,
        max = FamilyDtoConstraints.FAMILY_ALIAS_LENGTH,
        message = "Alias name must be exactly ${FamilyDtoConstraints.FAMILY_ALIAS_LENGTH} characters"
    )
    val aliasName: String,
    val notificationId: Long?
)

data class RejectFamilyRequest(
    @field:NotBlank(message = "Alias name is required")
    @field:Size(
        min = FamilyDtoConstraints.FAMILY_ALIAS_LENGTH,
        max = FamilyDtoConstraints.FAMILY_ALIAS_LENGTH,
        message = "Alias name must be exactly ${FamilyDtoConstraints.FAMILY_ALIAS_LENGTH} characters"
    )
    val aliasName: String,
    val notificationId: Long?
)

data class InviteMemberRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Valid email is required")
    val invitedMemberEmail: String,
    val notificationId: Long?
)

data class AcceptJoinRequestRequest(
    @field:NotBlank(message = "Email is required")
    @field:NotBlank(message = "requesterId is required")
    val requesterId: String,
    val notificationId: Long?
)

data class RemoveMemberRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Valid email is required")
    val memberEmail: String,
    val notificationId: Long?
)

data class RejectJoinRequestRequest(
    @field:NotBlank(message = "Email is required")
    @field:NotBlank(message = "requesterId is required")
    val requesterId: String,
    val notificationId: Long?
)

data class CancelInvitationRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Valid email is required")
    val invitedMemberEmail: String,
    val notificationId: Long?
)

data class UpdateFamilyNameRequest(
    @field:NotBlank(message = "Family name is required")
    @field:Size(
        min = FamilyDtoConstraints.FAMILY_NAME_MIN_LENGTH,
        max = FamilyDtoConstraints.FAMILY_NAME_MAX_LENGTH,
        message = "Family name must be between ${FamilyDtoConstraints.FAMILY_NAME_MIN_LENGTH} and ${FamilyDtoConstraints.FAMILY_NAME_MAX_LENGTH} characters"
    )
    val familyName: String
)

data class BasicFamilySuccessResponse(
    val message: String,
    val family: Map<String, Any>?
)

data class JoinRequestActionRequest(
    @field:NotBlank(message = "Alias name is required")
    @field:Size(
        min = FamilyDtoConstraints.FAMILY_ALIAS_LENGTH,
        max = FamilyDtoConstraints.FAMILY_ALIAS_LENGTH,
        message = "Alias name must be exactly ${FamilyDtoConstraints.FAMILY_ALIAS_LENGTH} characters"
    )
    val aliasName: String,
    val message: String? = null
)

data class JoinRequestByIdActionRequest(
    @field:NotBlank(message = "requestId is required")
    val requestId: String,
    val message: String? = null
)
