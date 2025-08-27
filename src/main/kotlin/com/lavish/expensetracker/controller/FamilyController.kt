package com.lavish.expensetracker.controller

import com.lavish.expensetracker.controller.dto.*
import com.lavish.expensetracker.service.FamilyApplicationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/family")
@Tag(name = "Family Management", description = "Family management endpoints (thin controller delegating to service)")
@SecurityRequirement(name = "Bearer Authentication")
class FamilyController(
    private val familyService: FamilyApplicationService
) {

    @PostMapping("/create")
    @Operation(summary = "Create a new family")
    fun createFamily(@Valid @RequestBody request: CreateFamilyRequest): ResponseEntity<*> =
        familyService.createFamily(request)

    @GetMapping("/details")
    @Operation(summary = "Get current family details")
    fun getFamilyDetails(): ResponseEntity<*> = familyService.getFamilyDetails()

    @PostMapping("/join")
    @Operation(summary = "Join family by alias")
    fun joinFamily(@Valid @RequestBody request: JoinFamilyRequest): ResponseEntity<*> =
        familyService.joinFamily(request)

    @PostMapping("/request-to-join")
    @Operation(summary = "Request to join a family")
    fun requestToJoinFamily(@Valid @RequestBody request: JoinFamilyRequest): ResponseEntity<*> =
        familyService.requestToJoinFamily(request)

    @PostMapping("/leave")
    @Operation(summary = "Leave current family")
    fun leaveFamily(): ResponseEntity<*> = familyService.leaveFamily()

    @PostMapping("/invite")
    @Operation(summary = "Invite member by email")
    fun inviteMember(@Valid @RequestBody request: InviteMemberRequest): ResponseEntity<*> =
        familyService.inviteMember(request)

    @PostMapping("/resend-invitation")
    @Operation(summary = "Resend pending invitation")
    fun resendInvitation(@Valid @RequestBody request: InviteMemberRequest): ResponseEntity<*> =
        familyService.resendInvitation(request)

    @PostMapping("/cancel-invitation")
    @Operation(summary = "Cancel pending invitation")
    fun cancelInvitation(@Valid @RequestBody request: CancelInvitationRequest): ResponseEntity<*> =
        familyService.cancelInvitation(request)

    @PostMapping("/update-name")
    @Operation(summary = "Update family name (head only)")
    fun updateFamilyName(@Valid @RequestBody request: UpdateFamilyNameRequest): ResponseEntity<*> =
        familyService.updateFamilyName(request)

    @PostMapping("/accept-invitation")
    @Operation(summary = "Accept invitation to family")
    fun acceptInvitation(@Valid @RequestBody request: JoinFamilyRequest): ResponseEntity<*> =
        familyService.acceptInvitation(request)

    @PostMapping("/reject-join-request")
    @Operation(summary = "Reject a join request (head only)")
    fun rejectJoinRequest(@Valid @RequestBody request: RejectJoinRequestRequest): ResponseEntity<*> =
        familyService.rejectJoinRequest(request)

    @PostMapping("/accept-join-request")
    @Operation(summary = "Accept a join request (head only)")
    fun acceptJoinRequest(@Valid @RequestBody request: AcceptJoinRequestRequest): ResponseEntity<*> =
        familyService.acceptJoinRequest(request)

    @PostMapping("/remove-member")
    @Operation(summary = "Remove member from family (head only)")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Member removed", content = [Content(mediaType = "application/json", schema = Schema(implementation = BasicFamilySuccessResponse::class))])
    ])
    fun removeMember(@Valid @RequestBody request: RemoveMemberRequest): ResponseEntity<*> =
        familyService.removeMember(request)
}
