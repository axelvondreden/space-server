package de.axl.rest

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import de.axl.db.DocumentService
import de.axl.db.UserService
import de.axl.files.FileManager
import de.axl.property
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class LoginRequest(val username: String, val password: String)

fun Application.configureRouting(userService: UserService, documentService: DocumentService, fileManager: FileManager) {
    val swaggerEnabled = property("space.swagger.enabled").toBoolean()

    routing {
        post("/login") {
            val secret = environment.config.property("jwt.secret").getString()
            val issuer = environment.config.property("jwt.issuer").getString()
            val audience = environment.config.property("jwt.audience").getString()
            val user = call.receive<LoginRequest>()

            val dbUser = userService.findByUsername(user.username)

            if (dbUser == null || !userService.testPassword(dbUser, user.password)) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                val token = JWT.create()
                    .withAudience(audience)
                    .withIssuer(issuer)
                    .withClaim("username", user.username)
                    .withExpiresAt(Date(System.currentTimeMillis() + (60 * 60 * 1000)))
                    .sign(Algorithm.HMAC256(secret))
                call.respond(hashMapOf("token" to token))
            }
        }

        if (swaggerEnabled) swaggerUI(path = "swagger")

        authenticate("auth-jwt") {
            get("/hello") {
                val principal = call.principal<JWTPrincipal>()
                val username = principal!!.payload.getClaim("username").asString()
                val expiresIn = principal.expiresAt?.time?.minus(System.currentTimeMillis())
                call.respondText("Hello, $username! Token expires in $expiresIn ms.")
            }

            usersRoute(userService)
            documentsRoute(documentService)
            importsRoute(fileManager)
        }
    }
}