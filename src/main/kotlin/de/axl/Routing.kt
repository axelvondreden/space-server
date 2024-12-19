package de.axl

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import de.axl.db.DocumentService
import de.axl.db.ExposedDocument
import de.axl.db.ExposedUser
import de.axl.db.UserService
import de.axl.files.FileManager
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Database
import java.io.File
import java.util.*

@Serializable
data class LoginRequest(val username: String, val password: String)

fun Application.configureRouting() {
    val swaggerEnabled = property("space.swagger.enabled").toBoolean()

    val database = Database.connect(
        url = "jdbc:h2:./space",
        user = "root",
        driver = "org.h2.Driver",
        password = "",
    )
    val userService = UserService(database)
    val documentService = DocumentService(database)
    val fileManager = FileManager(dataPath)

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

            // Create user
            /*post("/users") {
                val user = call.receive<ExposedUser>()
                val id = userService.create(user)
                call.respond(HttpStatusCode.Created, id)
            }*/

            get("/users") {
                call.respond(HttpStatusCode.OK, userService.findAll())
            }

            get("/users/{username}") {
                val username = call.parameters["username"]
                if (username.isNullOrBlank()) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    val user = userService.findByUsername(username)
                    if (user != null) {
                        call.respond(HttpStatusCode.OK, user)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }

            put("/users/{username}") {
                val username = call.parameters["username"]
                if (username.isNullOrBlank()) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    val dbUser = userService.findByUsername(username)
                    if (dbUser == null) {
                        call.respond(HttpStatusCode.NotFound)
                    } else {
                        val user = call.receive<ExposedUser>()
                        userService.update(user)
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }

            delete("/users/{username}") {
                val username = call.parameters["username"]
                if (username.isNullOrBlank()) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    userService.delete(username)
                    call.respond(HttpStatusCode.OK)
                }
            }

            get("/documents") {
                call.respond(HttpStatusCode.OK, documentService.findAll())
            }

            get("/documents/{guid}") {
                val guid = call.parameters["guid"]
                if (guid.isNullOrBlank()) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    val doc = documentService.findByGuid(guid)
                    if (doc != null) {
                        call.respond(HttpStatusCode.OK, doc)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }

            post("/documents") {
                val guid = documentService.createEmpty()
                call.respond(HttpStatusCode.Created, guid)
            }

            put("/documents/{guid}") {
                val guid = call.parameters["guid"]
                if (guid.isNullOrBlank()) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    val dbDoc = documentService.findByGuid(guid)
                    if (dbDoc == null) {
                        call.respond(HttpStatusCode.NotFound)
                    } else {
                        val doc = call.receive<ExposedDocument>()
                        documentService.update(doc)
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }

            delete("/documents/{guid}") {
                val guid = call.parameters["guid"]
                if (guid.isNullOrBlank()) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    documentService.delete(guid)
                    call.respond(HttpStatusCode.OK)
                }
            }

            post("/upload") {
                var fileName = ""
                val multipartData = call.receiveMultipart()
                multipartData.forEachPart { part ->
                    when (part) {
                        /*is PartData.FormItem -> {
                            fileDescription = part.value
                        }*/

                        is PartData.FileItem -> {
                            fileName = part.originalFileName as String
                            val fileBytes = part.provider().readRemaining().readByteArray()
                            File("$dataPath/upload/$fileName").writeBytes(fileBytes)
                        }

                        else -> {}
                    }
                    part.dispose()
                }

                if (fileName.isNotBlank()) {
                    call.respondText("File $fileName was uploaded!")
                    fileManager.handleUpload(fileName)
                } else {
                    call.respond(HttpStatusCode.BadRequest, "No file found in request")
                }
            }
        }
    }
}
