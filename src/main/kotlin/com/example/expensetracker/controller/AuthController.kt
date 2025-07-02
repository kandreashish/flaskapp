package com.example.expensetracker.controller

import com.example.expensetracker.model.auth.*
import com.example.expensetracker.service.AuthService
import com.example.expensetracker.util.AuthUtil
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val authUtil: AuthUtil
) {

    @PostMapping("/signup")
    fun signup(@Valid @RequestBody signupRequest: SignupRequest): ResponseEntity<Any> {
        return try {
            val response = authService.signup(signupRequest)
            ResponseEntity.status(HttpStatus.CREATED).body(response)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Signup failed")))
        }
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody loginRequest: LoginRequest): ResponseEntity<Any> {
        return try {
            val response = authService.login(loginRequest)
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to (e.message ?: "Login failed")))
        }
    }

    @GetMapping("/me")
    fun getCurrentUser(): ResponseEntity<UserInfo> {
        return try {
            val currentUserId = authUtil.getCurrentUserId()
            val user = authService.getUserById(currentUserId)
            if (user != null) {
                ResponseEntity.ok(UserInfo(
                    id = user.id,
                    name = user.name ?: "",
                    email = user.email ?: "",
                    familyId = user.familyId
                ))
            } else {
                ResponseEntity.notFound().build()
            }
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
    }
}
