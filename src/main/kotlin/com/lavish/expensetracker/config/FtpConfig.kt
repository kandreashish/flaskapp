package com.lavish.expensetracker.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "app.ftp")
data class FtpConfig(
    var host: String = "192.168.1.254",
    var port: Int = 21,
    var username: String = "ftpAdmin",
    var password: String = "ashish123",
    var baseDirectory: String = "uploads",  // Changed from "/uploads" to "uploads"
    var profilePicsDirectory: String = "uploads/profile-pics",  // Changed from "/uploads/profile-pics"
    var connectTimeout: Int = 30000,
    var dataTimeout: Int = 30000,
    var passiveMode: Boolean = true
)
