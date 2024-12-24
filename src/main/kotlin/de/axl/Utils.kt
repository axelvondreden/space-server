package de.axl

import de.axl.db.ExposedUser
import de.axl.db.UserService
import de.axl.web.UserSession
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

fun now(): Long = System.currentTimeMillis()

fun Application.property(path: String) = environment.config.property(path).getString()

val Application.dataPath get() = property("space.paths.data")

suspend fun <T> dbQuery(block: suspend () -> T): T = newSuspendedTransaction(Dispatchers.IO) { block() }

fun Route.apiRoute(build: Route.() -> Unit) = route("/api/v1") { build() }

suspend fun RoutingContext.getSessionUser(userService: UserService): ExposedUser? {
    return call.sessions.get<UserSession>()?.username?.let { userService.findByUsername(it) }
}