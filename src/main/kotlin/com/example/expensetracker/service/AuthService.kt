package com.example.expensetracker.service

import com.example.expensetracker.model.ExpenseUser
import com.example.expensetracker.model.auth.*
import com.example.expensetracker.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.util.*

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService
) {

    fun signup(signupRequest: SignupRequest): AuthResponse {
        // Check if user already exists
        if (userRepository.existsByEmail(signupRequest.email)) {
            throw RuntimeException("User with email ${signupRequest.email} already exists")
        }

        // Create new user
        val userId = UUID.randomUUID().toString()
        val encodedPassword = passwordEncoder.encode(signupRequest.password)

        val user = ExpenseUser(
            id = userId,
            name = signupRequest.name,
            email = signupRequest.email,
            password = encodedPassword,
            familyId = signupRequest.familyId.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            updatedAt = System.currentTimeMillis(),
            roles = listOf("USER")
        )

        userRepository.save(user)

        // Generate JWT token
        val token = jwtService.generateToken(userId, signupRequest.email)

        return AuthResponse(
            token = token,
            user = UserInfo(
                id = userId,
                name = signupRequest.name,
                email = signupRequest.email,
                familyId = user.familyId
            ),
            expiresIn = jwtService.getExpirationTime()
        )
    }

    fun login(loginRequest: LoginRequest): AuthResponse {
        // Find user by email
        val user = userRepository.findByEmail(loginRequest.email)
            ?: throw RuntimeException("Invalid email or password")

        // Verify password
        if (!passwordEncoder.matches(loginRequest.password, user.password)) {
            throw RuntimeException("Invalid email or password")
        }

        // Generate JWT token
        val token = jwtService.generateToken(user.id, user.email!!)

        return AuthResponse(
            token = token,
            user = UserInfo(
                id = user.id,
                name = user.name ?: "",
                email = user.email,
                familyId = user.familyId
            ),
            expiresIn = jwtService.getExpirationTime()
        )
    }

    fun getUserById(userId: String): ExpenseUser? {
        return userRepository.findById(userId)
    }
}
