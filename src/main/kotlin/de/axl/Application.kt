package de.axl

import com.fasterxml.jackson.databind.SerializationFeature
import de.axl.startup.configureStartup
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
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
    //configureSockets()
    configureRouting()
    configureStartup()
}
