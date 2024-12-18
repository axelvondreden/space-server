package de.axl.startup

import de.axl.property
import io.ktor.server.application.*
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString

fun Application.configureStartup() {
    val base = property("space.paths.base")
    createFolder(base)
    createFolder(base, property("space.paths.upload"))
    createFolder(base, property("space.paths.docs"))
    createFolder(base, property("space.paths.docs"), "pdf")
    createFolder(base, property("space.paths.docs"), "img")
    createFolder(base, property("space.paths.docs"), "thumb")
    createFolder(base, property("space.paths.docs"), "import")

    log.info("Startup checks completed!")
}

private fun Application.createFolder(base: String, vararg path: String) {
    val p = Path(base, *path)
    if (!p.exists()) {
        log.info("Creating directory ${p.pathString}")
        Files.createDirectory(p)
    }
}