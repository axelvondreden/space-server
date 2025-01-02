package de.axl.files

import de.axl.db.ExposedImport
import de.axl.db.ImportService
import de.axl.db.ImportType
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.tools.imageio.ImageIOUtil
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.ProcessBuilder.Redirect
import java.nio.file.Files
import java.util.*
import java.util.concurrent.TimeUnit
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
        val originalFilename = "$guid-original.${file.extension}"
        logger.info("Moving file to $originalFilename")
        val newPath = Files.move(file.toPath(), Path("$dataPath/docs/pdf", originalFilename))

        val ocrPdf = createSearchablePdf(newPath.toFile(), guid)

        createImgFromPdf(ocrPdf)

        logger.info("Creating import for $guid")
        importService.create(ExposedImport(guid, originalFilename, ImportType.PDF, ocrPdf.name, "", null))
    }

    private suspend fun handleImgUpload(file: File, guid: String) {
        logger.info("Detected Image (${file.extension})")
    }

    private fun createSearchablePdf(file: File, guid: String): File {
        logger.info("Creating searchable PDF")
        val dir = file.parentFile
        val name = file.name
        runCommand(dir, "ocrmypdf --rotate-pages --deskew --clean --skip-text --verbose 1 --language deu+eng $name $guid.pdf")
        return File(dir, "$guid.pdf")
    }

    private fun createImgFromPdf(file: File) {
        logger.info("Creating images from pdf")
        val document = Loader.loadPDF(file);
        val pdfRenderer = PDFRenderer(document);
        for (i in 0 until document.numberOfPages) {
            logger.info("Creating image from pdf page ${i + 1}")
            var img = pdfRenderer.renderImageWithDPI(i, 300F, ImageType.RGB);
            ImageIOUtil.writeImage(img, "${dataPath}/docs/img/${file.nameWithoutExtension}-${i + 1}.png", 300);
        }
        document.close();
    }

    private fun runCommand(workingDir: File, command: String) {
        ProcessBuilder(*command.split(" ").toTypedArray())
            .directory(workingDir)
            .redirectOutput(Redirect.INHERIT)
            .redirectError(Redirect.INHERIT)
            .start()
            .waitFor(10, TimeUnit.MINUTES)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.declaringClass)
    }
}