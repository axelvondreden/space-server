package de.axl.startup

import de.axl.files.FileManager
import io.ktor.server.application.*

fun Application.configureStartup(fileManager: FileManager) {
    log.info("Looking for logback config in ${System.getProperty("logback.configurationFile")}")

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