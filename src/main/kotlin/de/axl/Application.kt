package de.axl

import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.tomcat.jakarta.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureSecurity()
    configureHTTP()
    configureMonitoring()
    //configureSockets()
    //configureDatabases()
    configureRouting()
}
