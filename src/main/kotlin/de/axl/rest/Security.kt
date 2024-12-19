package de.axl.rest

import de.axl.property
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*

fun Application.configureSecurity() {
    val encryptKey = property("session.cookie.encrypt.key")
    if (encryptKey.isBlank()) {
        throw IllegalStateException("Session cookie encryption key is not configured")
    }
    val signKey = property("session.cookie.signing.key")
    if (signKey.isBlank()) {
        throw IllegalStateException("Session cookie signing key is not configured")
    }

    val encryptBytes = encryptKey.repeat((16 / encryptKey.length) + 1).toByteArray().take(16).toByteArray()
    val signBytes = signKey.repeat((16 / signKey.length) + 1).toByteArray().take(16).toByteArray()

    val cookieAge = property("session.cookie.age").toLongOrNull() ?: 60
    val secure = property("session.cookie.secure").toBoolean()
    install(Sessions) {
        cookie<UserSession>("user_session") {
            cookie.path = "/"
            cookie.maxAgeInSeconds = cookieAge
            cookie.secure = secure
            transform(SessionTransportTransformerEncrypt(encryptBytes, signBytes))
        }
    }
    install(Authentication) {
        session<UserSession>("auth-session") {
            validate { session ->
                if (session.username.isNotBlank()) {
                    UserSession(session.username)
                } else {
                    null
                }
            }
            challenge {
                //call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
                call.respondRedirect("/login")
            }
        }
    }
}