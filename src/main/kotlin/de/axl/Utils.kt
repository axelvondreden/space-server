package de.axl

import de.axl.db.ExposedUser
import de.axl.db.UserService
import de.axl.web.UserSession
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val datetimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")

fun now(): Long = System.currentTimeMillis()

fun Application.property(path: String) = environment.config.property(path).getString()

val Application.dataPath get() = property("space.paths.data")

suspend fun <T> dbQuery(block: suspend () -> T): T = newSuspendedTransaction(Dispatchers.IO) { block() }

fun Route.apiRoute(build: Route.() -> Unit) = route("/api/v1") { build() }

suspend fun RoutingContext.getSessionUser(userService: UserService): ExposedUser? {
    return call.sessions.get<UserSession>()?.username?.let { userService.findByUsername(it) }
}

fun Long.toDatetimeString(): String {
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault()).format(datetimeFormatter)
}