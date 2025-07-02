package com.example.expensetracker.model

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "expenses")
data class Expense(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(nullable = false)
    val description: String,
    
    @Column(nullable = false)
    val amount: Double,
    
    @Column(nullable = false)
    val category: String,
    
    @Column(nullable = false)
    val date: LocalDate = LocalDate.now(),
    
    @Column(nullable = true)
    val notes: String? = null
)
