package com.lavish.expensetracker.service

import com.lavish.expensetracker.config.FtpConfig
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.io.IOException
import java.io.InputStream
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.ReentrantLock
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

@Service
class FtpFileStorageService(
    private val ftpConfig: FtpConfig
) {

    private val logger = LoggerFactory.getLogger(FtpFileStorageService::class.java)

    // Connection pool for better performance
    private val connectionPool = ConcurrentLinkedQueue<FTPClient>()
    private val poolLock = ReentrantLock()
    private val maxPoolSize = 5
    private val minPoolSize = 2

    private val maxFileSize = 5 * 1024 * 1024L // 5MB in bytes
    private val allowedContentTypes = setOf(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/gif",
        "image/webp"
    )

    @PostConstruct
    fun initializeFtpDirectories() {
        logger.info("Initializing FTP directories and connection pool on server: {}:{}", ftpConfig.host, ftpConfig.port)

        // Initialize connection pool
        repeat(minPoolSize) {
            try {
                val ftpClient = createAndConnectFtpClient()
                connectionPool.offer(ftpClient)
            } catch (ex: Exception) {
                logger.warn("Failed to create initial FTP connection for pool", ex)
            }
        }

        val ftpClient = borrowConnection()
        try {
            // Create base upload directory
            createDirectoryIfNotExists(ftpClient, ftpConfig.baseDirectory)

            // Create profile-pics subdirectory
            createDirectoryIfNotExists(ftpClient, ftpConfig.profilePicsDirectory)

            logger.info("FTP directories initialization completed successfully")

        } catch (ex: Exception) {
            logger.error("Failed to initialize FTP directories", ex)
            throw RuntimeException("Failed to initialize FTP directories: ${ex.message}", ex)
        } finally {
            returnConnection(ftpClient)
        }
    }

    @PreDestroy
    fun cleanup() {
        logger.info("Cleaning up FTP connection pool")
        while (connectionPool.isNotEmpty()) {
            val client = connectionPool.poll()
            try {
                if (client?.isConnected == true) {
                    client.logout()
                    client.disconnect()
                }
            } catch (ex: Exception) {
                logger.warn("Error cleaning up FTP connection", ex)
            }
        }
    }

    fun uploadProfilePicture(file: MultipartFile, userId: String): String {
        logger.debug("Starting FTP profile picture upload for user: {}, file: {}, size: {}",
                    userId, file.originalFilename, file.size)

        val maxAttempts = 3
        var attempt = 0
        var lastException: Exception? = null

        while (attempt < maxAttempts) {
            attempt++
            logger.debug("Upload attempt {} of {} for user: {}", attempt, maxAttempts, userId)

            try {
                validateFile(file)

                val fileName = generateUniqueFileName(file.originalFilename ?: "image.jpg")
                val ftpClient = createFtpClient()

                try {
                    connectToFtp(ftpClient)

                    // Ensure directories exist before upload
                    createDirectoryIfNotExists(ftpClient, ftpConfig.baseDirectory)
                    createDirectoryIfNotExists(ftpClient, ftpConfig.profilePicsDirectory)

                    // Upload file to FTP server
                    val remotePath = "${ftpConfig.profilePicsDirectory}/$fileName"
                    logger.info("Uploading file to FTP path: {}", remotePath)

                    val success = ftpClient.storeFile(remotePath, file.inputStream)

                    if (!success) {
                        val replyCode = ftpClient.replyCode
                        val replyString = ftpClient.replyString
                        logger.error("FTP upload failed - Reply Code: {}, Reply: {}", replyCode, replyString)
                        throw IOException("Failed to upload file to FTP server. Reply Code: $replyCode, Reply: $replyString")
                    }

                    logger.info("Successfully uploaded profile picture for user: {} to FTP path: {}", userId, remotePath)
                    return "https://simpleexpense.ddns.net/api/files/profile-pics/$fileName"

                } finally {
                    disconnectFromFtp(ftpClient)
                }

            } catch (ex: Exception) {
                lastException = ex
                logger.warn("Upload attempt {} failed for user: {}: {}", attempt, userId, ex.message)

                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(1000L * attempt) // Exponential backoff
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
        }

        logger.error("All upload attempts failed for user: {}", userId, lastException)
        throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                    "Failed to upload file after $maxAttempts attempts")
    }

    // Optimized method for streaming files directly
    fun getFileInputStream(fileName: String): InputStream {
        val ftpClient = borrowConnection()

        try {
            val remotePath = "${ftpConfig.profilePicsDirectory}/$fileName"

            // Use direct streaming instead of buffering entire file
            val inputStream = ftpClient.retrieveFileStream(remotePath)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "File not found on FTP server")

            // Return a custom InputStream that handles connection cleanup
            return FtpStreamWrapper(inputStream, ftpClient, this)

        } catch (ex: Exception) {
            logger.error("Failed to retrieve file: {}", fileName, ex)
            returnConnection(ftpClient)
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving file")
        }
    }

    fun deleteFile(fileName: String): Boolean {
        val ftpClient = createFtpClient()

        try {
            connectToFtp(ftpClient)

            val remotePath = "${ftpConfig.profilePicsDirectory}/$fileName"
            val success = ftpClient.deleteFile(remotePath)

            if (success) {
                logger.info("Successfully deleted file from FTP: {}", remotePath)
            } else {
                logger.warn("Failed to delete file from FTP: {}", remotePath)
            }

            return success

        } catch (ex: Exception) {
            logger.error("Error deleting file from FTP: {}", fileName, ex)
            return false
        } finally {
            disconnectFromFtp(ftpClient)
        }
    }

    fun fileExists(fileName: String): Boolean {
        val ftpClient = createFtpClient()

        try {
            connectToFtp(ftpClient)

            val remotePath = "${ftpConfig.profilePicsDirectory}/$fileName"
            val files = ftpClient.listFiles(remotePath)

            return files.isNotEmpty()

        } catch (ex: Exception) {
            logger.error("Error checking if file exists on FTP: {}", fileName, ex)
            return false
        } finally {
            disconnectFromFtp(ftpClient)
        }
    }

    private fun createFtpClient(): FTPClient {
        val ftpClient = FTPClient()
        ftpClient.connectTimeout = ftpConfig.connectTimeout
        ftpClient.dataTimeout = Duration.ofMillis(ftpConfig.dataTimeout.toLong())
        return ftpClient
    }

    private fun connectToFtp(ftpClient: FTPClient) {
        try {
            ftpClient.connect(ftpConfig.host, ftpConfig.port)

            val replyCode = ftpClient.replyCode
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                ftpClient.disconnect()
                throw IOException("FTP server refused connection. Reply code: $replyCode")
            }

            val loginSuccess = ftpClient.login(ftpConfig.username, ftpConfig.password)
            if (!loginSuccess) {
                throw IOException("FTP login failed. Reply: ${ftpClient.replyString}")
            }

            if (ftpConfig.passiveMode) {
                ftpClient.enterLocalPassiveMode()
            }

            ftpClient.setFileType(FTP.BINARY_FILE_TYPE)

            logger.debug("Successfully connected to FTP server: {}:{}", ftpConfig.host, ftpConfig.port)

        } catch (ex: Exception) {
            logger.error("Failed to connect to FTP server: {}:{}", ftpConfig.host, ftpConfig.port, ex)
            throw ex
        }
    }

    private fun disconnectFromFtp(ftpClient: FTPClient) {
        try {
            if (ftpClient.isConnected) {
                ftpClient.logout()
                ftpClient.disconnect()
            }
        } catch (ex: Exception) {
            logger.warn("Error disconnecting from FTP server", ex)
        }
    }

    private fun createDirectoryIfNotExists(ftpClient: FTPClient, directory: String) {
        try {
            // First, check what the current working directory is
            val currentDir = ftpClient.printWorkingDirectory()
            logger.info("Current FTP working directory: {}", currentDir)

            // Try to navigate to the directory first to see if it exists
            val canNavigate = ftpClient.changeWorkingDirectory(directory)
            if (canNavigate) {
                logger.info("FTP directory already exists: {}", directory)
                ftpClient.changeWorkingDirectory(currentDir) // Go back to original directory
                return
            }

            // Directory doesn't exist, try to create it
            // Split the directory path into parts, but skip leading slash for relative paths
            val cleanDirectory = if (directory.startsWith("/")) {
                directory.substring(1) // Remove leading slash to make it relative
            } else {
                directory
            }

            val parts = cleanDirectory.split("/").filter { it.isNotEmpty() }
            var currentPath = ""

            for (part in parts) {
                currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"

                // Try to change to the directory first
                val changed = ftpClient.changeWorkingDirectory(currentPath)
                if (!changed) {
                    // Directory doesn't exist, create it
                    val created = ftpClient.makeDirectory(currentPath)
                    if (created) {
                        logger.info("Created FTP directory: {}", currentPath)
                    } else {
                        val replyCode = ftpClient.replyCode
                        val replyString = ftpClient.replyString
                        logger.warn("Failed to create FTP directory: {}. Reply Code: {}, Reply: {}",
                                   currentPath, replyCode, replyString)

                        // If we can't create the directory, try to continue with existing structure
                        if (replyCode == 550) { // Permission denied or directory exists
                            logger.info("Attempting to continue with existing directory structure")
                            break
                        }
                    }
                } else {
                    logger.debug("FTP directory already exists: {}", currentPath)
                }

                // Go back to root for next iteration
                ftpClient.changeWorkingDirectory(currentDir)
            }

        } catch (ex: Exception) {
            logger.error("Error creating FTP directory: {}", directory, ex)
        }
    }

    private fun validateFile(file: MultipartFile) {
        if (file.isEmpty) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty")
        }

        if (file.size > maxFileSize) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "File size exceeds maximum allowed size of ${maxFileSize / (1024 * 1024)}MB")
        }

        val contentType = file.contentType
        if (contentType == null || !allowedContentTypes.contains(contentType.lowercase())) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Invalid file type. Allowed types: ${allowedContentTypes.joinToString()}")
        }
    }

    private fun generateUniqueFileName(originalFilename: String): String {
        val extension = originalFilename.substringAfterLast('.', "jpg")
        val timestamp = System.currentTimeMillis()
        val random = UUID.randomUUID().toString().substring(0, 8)
        return "profile_${timestamp}_$random.$extension"
    }

    // Connection pool management
    private fun borrowConnection(): FTPClient {
        poolLock.lock()
        try {
            var client = connectionPool.poll()

            // Validate connection and create new one if needed
            if (client == null || !client.isConnected || !isConnectionHealthy(client)) {
                client?.let { safeDisconnect(it) }
                client = createAndConnectFtpClient()
            }

            return client
        } finally {
            poolLock.unlock()
        }
    }

    fun returnConnection(client: FTPClient) {
        poolLock.lock()
        try {
            if (client.isConnected && isConnectionHealthy(client) && connectionPool.size < maxPoolSize) {
                connectionPool.offer(client)
            } else {
                safeDisconnect(client)
            }
        } finally {
            poolLock.unlock()
        }
    }

    private fun isConnectionHealthy(client: FTPClient): Boolean {
        return try {
            client.sendNoOp()
            true
        } catch (ex: Exception) {
            false
        }
    }

    private fun safeDisconnect(client: FTPClient) {
        try {
            if (client.isConnected) {
                client.logout()
                client.disconnect()
            }
        } catch (ex: Exception) {
            logger.warn("Error disconnecting FTP client", ex)
        }
    }

    private fun createAndConnectFtpClient(): FTPClient {
        val ftpClient = createFtpClient()
        connectToFtp(ftpClient)
        return ftpClient
    }
}

// Custom InputStream wrapper that handles FTP connection cleanup
class FtpStreamWrapper(
    private val inputStream: InputStream,
    private val ftpClient: FTPClient,
    private val ftpService: FtpFileStorageService
) : InputStream() {

    private var closed = false

    override fun read(): Int = inputStream.read()

    override fun read(b: ByteArray): Int = inputStream.read(b)

    override fun read(b: ByteArray, off: Int, len: Int): Int = inputStream.read(b, off, len)

    override fun available(): Int = inputStream.available()

    override fun close() {
        if (!closed) {
            try {
                inputStream.close()
                ftpClient.completePendingCommand() // Important for FTP streams
            } finally {
                ftpService.returnConnection(ftpClient)
                closed = true
            }
        }
    }
}
