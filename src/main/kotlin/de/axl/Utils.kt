package de.axl

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.coroutines.Dispatchers
import kotlinx.css.CssBuilder
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

fun now(): Long = System.currentTimeMillis()

fun Application.property(path: String) = environment.config.property(path).getString()

val Application.dataPath get() = property("space.paths.data")

suspend fun <T> dbQuery(block: suspend () -> T): T = newSuspendedTransaction(Dispatchers.IO) { block() }

suspend inline fun ApplicationCall.respondCss(builder: CssBuilder.() -> Unit) {
    this.respondText(CssBuilder().apply(builder).toString(), ContentType.Text.CSS)
}