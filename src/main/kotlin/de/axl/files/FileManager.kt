package de.axl.files

import de.axl.db.ExposedImport
import de.axl.db.ImportService
import de.axl.db.ImportType
import de.axl.db.OCRLanguage
import de.axl.web.events.ImportStateEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import net.coobird.thumbnailator.Thumbnails
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.tools.imageio.ImageIOUtil
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.ProcessBuilder.Redirect
import java.nio.file.Files
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString

class FileManager(val dataPath: String, private val importService: ImportService) {

    private var importing = false

    fun createFolder(base: String, vararg path: String) {
        val p = Path(base, *path)
        if (!p.exists()) {
            logger.info("Creating directory ${p.pathString}")
            Files.createDirectory(p)
        }
    }

    suspend fun handleUploads(importFlow: MutableSharedFlow<ImportStateEvent>) {
        if (importing) return
        importing = true
        val files = File("$dataPath/upload").listFiles()
        logger.info("Found ${files?.size ?: 0} new files")
        files.forEachIndexed { index, file ->
            if (file.exists()) {
                val guid = UUID.randomUUID().toString()
                val percent = index.toDouble() / files.size.toDouble()
                val state = ImportStateEvent(importing = true, guid = guid, progress = percent, message = "Importing ${file.name}")
                importFlow.emit(state)
                when (file.extension) {
                    "pdf" -> handlePdfUpload(file, guid, importFlow, state, 1.0 / files.size)
                    "png", "jpg", "jpeg" -> handleImgUpload(file, guid)
                    else -> logger.error("File ${file.name} has unsupported filetype ${file.extension}")
                }
            } else {
                logger.error("File ${file.name} does not exist")
            }
        }
        importFlow.emit(ImportStateEvent(importing = false, message = "Finished importing files"))
        importing = false
    }

    private suspend fun handlePdfUpload(file: File, guid: String, importFlow: MutableSharedFlow<ImportStateEvent>, state: ImportStateEvent, fullStep: Double) {
        logger.info("Detected PDF")
        val originalFilename = "$guid-original.${file.extension}"
        logger.info("Moving file to $originalFilename")
        importFlow.emit(state.copy(message = "Moving file to $originalFilename"))
        val newPath = Files.move(file.toPath(), Path("$dataPath/docs/pdf", originalFilename))

        importFlow.emit(state.copy(message = "Running OCR on PDF"))
        val ocrPdf = createSearchablePdf(newPath.toFile(), guid)

        importFlow.emit(state.copy(message = "Extracting text from PDF"))
        val text = extractTextFromPdf(ocrPdf)

        importFlow.emit(state.copy(message = "Searching for dates in PDF"))
        val date = findDateFromText(text)

        importFlow.emit(state.copy(message = "Creating images from PDF"))
        createImagesFromPdf(ocrPdf)

        importFlow.emit(state.copy(message = "Creating thumbnails"))
        val page1Img = getImage(guid, 1)
        createThumbnails(page1Img)

        logger.info("Creating import for $guid")
        importFlow.emit(state.copy(message = "Creating import in database for $guid"))
        importService.create(ExposedImport(guid, originalFilename, ImportType.PDF, ocrPdf.name, ocrLanguage = OCRLanguage.DEU, text = text, date = date))
        logger.info("PDF import created")
    }

    private suspend fun handleImgUpload(file: File, guid: String) {
        logger.info("Detected Image (${file.extension})")
    }

    private fun createSearchablePdf(file: File, guid: String): File {
        logger.info("Creating searchable PDF")
        val dir = file.parentFile
        val name = file.name
        runCommand(dir, "ocrmypdf --rotate-pages --deskew --clean --skip-text --language deu $name $guid.pdf")
        return File(dir, "$guid.pdf")
    }

    private fun createImagesFromPdf(file: File) {
        val document = Loader.loadPDF(file)
        val pdfRenderer = PDFRenderer(document)
        for (i in 0 until document.numberOfPages) {
            logger.info("Creating image from PDF page ${i + 1}")
            var img = pdfRenderer.renderImageWithDPI(i, 300F, ImageType.RGB)
            ImageIOUtil.writeImage(img, "${dataPath}/docs/img/${file.nameWithoutExtension}-${(i + 1).toString().padStart(4, '0')}.png", 300)
        }
        document.close()
    }

    private fun createThumbnails(file: File) {
        logger.info("Creating Thumbnail 128x128")
        Thumbnails.of(file).size(128, 128).outputFormat("png").toFile(File("${dataPath}/docs/thumb/${file.nameWithoutExtension}-128.png"))
        logger.info("Creating Thumbnail 256x256")
        Thumbnails.of(file).size(256, 256).outputFormat("png").toFile(File("${dataPath}/docs/thumb/${file.nameWithoutExtension}-256.png"))
        logger.info("Creating Thumbnail 512x512")
        Thumbnails.of(file).size(512, 512).outputFormat("png").toFile(File("${dataPath}/docs/thumb/${file.nameWithoutExtension}-512.png"))
    }

    private fun extractTextFromPdf(file: File): String {
        logger.info("Extracting text from PDF")
        val document = Loader.loadPDF(file)
        val text = PDFTextStripper().getText(document)
        return text.lines().filter { it.isNotBlank() }.joinToString("\n")
    }

    private fun findDateFromText(text: String): LocalDate? {
        logger.info("Searching for date in PDF")
        val lines = text.lines()
        val dates = mutableListOf<LocalDate>()
        lines.forEach { line ->
            datePatterns.forEach { entry ->
                val match = entry.key.find(line)
                if (match != null) {
                    val date = LocalDate.parse(match.value, java.time.format.DateTimeFormatter.ofPattern(entry.value))
                    logger.info("Found date: $date")
                    dates += date
                }
            }
        }
        val datesSorted = dates.groupBy { it }.mapValues { it.value.size }
        val date = datesSorted.maxByOrNull { it.value }?.key
        logger.info("Choosing date for document: $date")
        return date
    }

    private fun getImage(guid: String, page: Int): File {
        return File("$dataPath/docs/img/${guid}-${page.toString().padStart(4, '0')}.png")
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

        private val datePatterns = mapOf(
            Regex("(3[01]|[12][0-9]|0?[1-9])\\.(1[012]|0?[1-9])\\.(\\d{4})") to "dd.MM.yyyy",
            Regex("(3[01]|[12][0-9]|0?[1-9])\\.(1[012]|0?[1-9])\\.(\\d{2})") to "dd.MM.yy",
        )
    }
}