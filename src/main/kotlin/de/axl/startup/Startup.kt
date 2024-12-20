package de.axl.startup

import de.axl.db.ExposedUser
import de.axl.db.UserService
import de.axl.files.FileManager
import de.axl.now
import de.axl.property
import io.ktor.server.application.*
import kotlinx.coroutines.runBlocking

fun Application.configureStartup(userService: UserService, fileManager: FileManager) {
    log.info("Looking for logback config in ${System.getProperty("logback.configurationFile")}")

    runBlocking {
        val adminUsername = property("space.admin.user.username")
        if (userService.findByUsername(adminUsername) == null) {
            log.info("Creating admin user: $adminUsername")
            userService.create(ExposedUser(adminUsername, "Admin", now(), null), property("space.admin.user.defaultPassword"))
        }
    }

    fileManager.apply {
        createFolder(dataPath)
        createFolder(dataPath, "upload")
        createFolder(dataPath, "docs")
        createFolder(dataPath, "docs", "pdf")
        createFolder(dataPath, "docs", "img")
        createFolder(dataPath, "docs", "thumb")
        createFolder(dataPath, "docs", "import")
    }

    log.info("Startup checks completed!")
}