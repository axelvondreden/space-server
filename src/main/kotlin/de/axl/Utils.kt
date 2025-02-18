package de.axl

import de.axl.serialization.api.ExposedUser
import de.axl.db.UserDbService
import de.axl.web.UserSession
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.Logger
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.io.path.exists

fun Application.property(path: String) = environment.config.property(path).getString()

val Application.dataPath get() = property("space.paths.data")

suspend fun <T> dbQuery(block: suspend () -> T): T = newSuspendedTransaction(Dispatchers.IO) { block() }

fun Route.apiRoute(build: Route.() -> Unit) = route("/api/v1") { build() }

suspend fun RoutingContext.getSessionUser(userDbService: UserDbService): ExposedUser? {
    return call.sessions.get<UserSession>()?.username?.let { userDbService.findByUsername(it) }
}

fun createFolder(base: String, vararg path: String) {
    val p = Path(base, *path)
    if (!p.exists()) {
        Files.createDirectory(p)
    }
}

fun runCommand(workingDir: File, command: String, logger: Logger? = null) {
    logger?.info("Running command: $command")
    val process = ProcessBuilder(command.split(" "))
        .directory(workingDir)
        .redirectErrorStream(true)
        .start()

    process.inputStream.bufferedReader().lines().forEach { logger?.info(it) }

    if (!process.waitFor(10, TimeUnit.MINUTES)) {
        System.err.println("Command timed out: $command")
        process.destroy()
    }
}