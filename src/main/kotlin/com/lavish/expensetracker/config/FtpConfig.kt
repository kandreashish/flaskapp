package com.lavish.expensetracker.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "app.ftp")
data class FtpConfig(
    var host: String = "192.168.1.21",
    var port: Int = 6969,
    var username: String = "android",
    var password: String = "android",
    var baseDirectory: String = "/server/uploads",
    var profilePicsDirectory: String = "/server/uploads/profile_pics",
    var connectTimeout: Int = 10000,
    var dataTimeout: Int = 10000,
    var passiveMode: Boolean = true
)
