package de.axl

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import de.axl.db.DocumentService
import de.axl.db.ImportService
import de.axl.db.UserService
import de.axl.files.FileManager
import de.axl.startup.configureStartup
import de.axl.web.configureRouting
import de.axl.web.configureSecurity
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.thymeleaf.*
import org.jetbrains.exposed.sql.Database
import org.slf4j.event.Level
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.*


fun main(args: Array<String>) {
    io.ktor.server.tomcat.jakarta.EngineMain.main(args)
}

fun Application.module() {
    install(IgnoreTrailingSlash)
    install(StatusPages) {
        status(HttpStatusCode.NotFound) { call, status ->
            call.respondText(text = "404: Page Not Found", status = status)
        }
    }
    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
            registerModule(JavaTimeModule())
        }
    }
    install(CallLogging) {
        level = Level.INFO
    }

    install(Thymeleaf) {
        setTemplateResolver(ClassLoaderTemplateResolver().apply {
            prefix = "templates/"
            suffix = ".html"
            characterEncoding = "utf-8"
        })
    }

    val properties = Properties()
    javaClass.getResourceAsStream("/de/axl/version.properties").use { stream ->
        checkNotNull(stream) { "Version properties file does not exist" }
        properties.load(InputStreamReader(stream, StandardCharsets.UTF_8))
    }
    val version = "Version ${properties.getProperty("version")}"
    val missing = 47 - version.length
    log.info("""

┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
┃ ▄▀▀ █▀▄ ▄▀▄ ▄▀▀ ██▀   ▄▀▀ ██▀ █▀▄ █ █ ██▀ █▀▄ ┃
┃ ▄██ █▀  █▀█ ▀▄▄ █▄▄   ▄██ █▄▄ █▀▄ ▀▄▀ █▄▄ █▀▄ ┃
${"┗" + "━".repeat(if (missing % 2 == 0) missing / 2 else (missing / 2) + 1) + version + "━".repeat(missing / 2) + "┛"}

    """.trimIndent())

    configureSecurity()

    val database = Database.connect(
        url = "jdbc:h2:./space",
        user = "root",
        driver = "org.h2.Driver",
        password = "",
    )
    val userService = UserService(database, property("space.admin.user.username"))
    val documentService = DocumentService(database)
    val importService = ImportService(database)
    val fileManager = FileManager(dataPath, importService)

    configureRouting(userService, documentService, importService, fileManager)
    configureStartup(userService, fileManager)
}
