package de.axl.files

import de.axl.db.ExposedImport
import de.axl.db.ImportService
import de.axl.db.ImportType
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString

class FileManager(val dataPath: String, private val importService: ImportService) {

    fun createFolder(base: String, vararg path: String) {
        val p = Path(base, *path)
        if (!p.exists()) {
            logger.info("Creating directory ${p.pathString}")
            Files.createDirectory(p)
        }
    }

    suspend fun handleUpload(filename: String) {
        logger.info("Handling uploaded file $filename")
        val file = File("$dataPath/upload", filename)
        if (file.exists()) {
            val guid = UUID.randomUUID().toString()
            when (file.extension) {
                "pdf" -> handlePdfUpload(file, guid)
                "png", "jpg", "jpeg" -> handleImgUpload(file, guid)
                else -> logger.error("File $filename has unsupported filetype ${file.extension}")
            }
        } else {
            logger.error("File $filename does not exist")
        }
    }

    private suspend fun handlePdfUpload(file: File, guid: String) {
        logger.info("Detected PDF")
        val filename = "$guid.${file.extension}"
        logger.info("Moving file to $filename")
        Files.move(file.toPath(), Path("$dataPath/docs/pdf", filename))
        logger.info("Creating import for $guid")
        importService.create(ExposedImport(guid, filename, ImportType.PDF, "", null))
    }

    private suspend fun handleImgUpload(file: File, guid: String) {
        logger.info("Detected Image (${file.extension})")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.declaringClass)
    }
}