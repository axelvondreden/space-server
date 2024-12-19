package de.axl

import com.fasterxml.jackson.databind.SerializationFeature
import de.axl.db.DocumentService
import de.axl.db.UserService
import de.axl.files.FileManager
import de.axl.startup.configureStartup
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import org.jetbrains.exposed.sql.Database
import org.slf4j.event.Level

fun main(args: Array<String>) {
    io.ktor.server.tomcat.jakarta.EngineMain.main(args)
}

fun Application.module() {
    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }
    install(CallLogging) {
        level = Level.INFO
    }

    val database = Database.connect(
        url = "jdbc:h2:./space",
        user = "root",
        driver = "org.h2.Driver",
        password = "",
    )
    val userService = UserService(database)
    val documentService = DocumentService(database)
    val fileManager = FileManager(dataPath)

    configureSecurity()
    configureRouting(userService, documentService, fileManager)
    configureStartup(fileManager)
}
