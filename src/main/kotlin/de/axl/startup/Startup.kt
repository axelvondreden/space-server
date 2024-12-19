package de.axl.startup

import de.axl.dataPath
import de.axl.files.FileManager
import io.ktor.server.application.*

fun Application.configureStartup() {
    log.info("Looking for logback config in ${System.getProperty("logback.configurationFile")}")

    val fileManager = FileManager(dataPath)
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