package de.axl.startup

import de.axl.createFolder
import de.axl.db.ExposedUser
import de.axl.db.UserDbService
import de.axl.files.ImportFileManager
import de.axl.property
import io.ktor.server.application.*
import kotlinx.coroutines.runBlocking

fun Application.configureStartup(userDbService: UserDbService, importFileManager: ImportFileManager) {
    log.info("Looking for logback config in ${System.getProperty("logback.configurationFile")}")

    runBlocking {
        val adminUsername = property("space.admin.user.username")
        if (userDbService.findByUsername(adminUsername) == null) {
            log.info("Creating admin user: $adminUsername")
            userDbService.create(ExposedUser(adminUsername, "Admin", true), property("space.admin.user.defaultPassword"))
        }
    }

    importFileManager.apply {
        createFolder(dataPath)
        createFolder(dataPath, "upload")
        createFolder(dataPath, "import")
        createFolder(dataPath, "import", "pdf")
        createFolder(dataPath, "import", "pages")
    }

    log.info("Startup checks completed!")
}