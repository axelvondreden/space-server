package de.axl

import com.fasterxml.jackson.databind.SerializationFeature
import de.axl.db.DocumentService
import de.axl.db.ImportService
import de.axl.db.UserService
import de.axl.files.FileManager
import de.axl.rest.configureRouting
import de.axl.rest.configureSecurity
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

    configureSecurity()

    val database = Database.connect(
        url = "jdbc:h2:./space",
        user = "root",
        driver = "org.h2.Driver",
        password = "",
    )
    val userService = UserService(database)
    val documentService = DocumentService(database)
    val importService = ImportService(database)
    val fileManager = FileManager(dataPath)

    configureRouting(userService, documentService, importService, fileManager)
    configureStartup(fileManager)
}
