package de.axl.importing

import de.axl.db.ExposedImport
import de.axl.db.ImportDbService
import de.axl.db.OCRLanguage
import de.axl.files.FileManagerImport
import de.axl.importing.events.ImportStateEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDate
import java.util.*

class ImportService(private val fileManager: FileManagerImport, private val dbService: ImportDbService) {

    private var importing = false

    suspend fun handleUploads(importFlow: MutableSharedFlow<ImportStateEvent>) {
        if (importing) return
        importing = true
        val files = fileManager.getUploadedFiles()
        logger.info("Found ${files.size} new files")
        files.forEachIndexed { index, file ->
            if (file.exists()) {
                val guid = UUID.randomUUID().toString()
                val percent = index.toDouble() / files.size.toDouble()
                val state = ImportStateEvent(
                    importing = true,
                    currentFile = index + 1,
                    fileCount = files.size,
                    guid = guid,
                    progress = percent,
                    message = "Importing ${file.name}"
                )
                importFlow.emit(state)
                handlePdfUpload(file, guid, importFlow, state, 1.0 / files.size)
            } else {
                logger.error("File ${file.name} does not exist")
            }
        }
        importFlow.emit(ImportStateEvent(importing = false, message = "Finished importing files"))
        importing = false
    }

    suspend fun findAll(): List<ExposedImport> = dbService.findAll()

    suspend fun findByGuid(guid: String): ExposedImport? = dbService.findByGuid(guid)

    suspend fun update(import: ExposedImport) = dbService.update(import)

    suspend fun delete(guid: String) = dbService.delete(guid)

    private suspend fun handlePdfUpload(file: File, guid: String, importFlow: MutableSharedFlow<ImportStateEvent>, state: ImportStateEvent, fullStep: Double) {
        val step = fullStep / 7.0

        fileManager.moveFileToImport(file, guid)

        fileManager.createImagesFromOriginalPdf(guid)

        val pageImages = fileManager.getImagesOriginal(guid)
        val pageCount = pageImages.size

        editImageComplete(guid)

        val ocrState = state.copy(progress = state.progress?.plus((step * 2)), message = "Running OCR")
        importFlow.emit(ocrState)
        //val ocrPdf = createSearchablePdf(newPath.toFile(), guid, importFlow, ocrState)

        importFlow.emit(state.copy(progress = state.progress?.plus((step * 3)), message = "Extracting text from PDF"))
        //val text = extractTextFromPdf(ocrPdf)

        importFlow.emit(state.copy(progress = state.progress?.plus((step * 4)), message = "Searching for dates in PDF"))
        //val date = findDateFromText(text)

        logger.info("Creating import for $guid")
        importFlow.emit(state.copy(progress = state.progress?.plus((step * 7)), message = "Creating import in database for $guid"))
        dbService.create(ExposedImport(guid, ocrLanguage = OCRLanguage.DEU, pages = pageCount, text = null, date = null))
        logger.info("PDF import created")
        importFlow.emit(state.copy(progress = state.progress?.plus((step * 7)), message = "Import complete", completedFile = true))
    }

    suspend fun editImageComplete(guid: String, deskew: Int = 40, colorFuzz: Int = 10, cropFuzz: Int = 20) {
        logger.info("Running deskew on $guid: deskew: $deskew% colorFuzz: $colorFuzz% cropFuzz: $cropFuzz%")
        val pageCount = fileManager.getImagesOriginal(guid).size
        val dbImport = dbService.findByGuid(guid)
        if (dbImport != null) {
            dbService.update(dbImport.copy(deskew = deskew, colorFuzz = colorFuzz, cropFuzz = cropFuzz))
        }
        for (page in 1..pageCount) {
            fileManager.createDeskewedImage(guid, page, deskew)
            delay(100)
            fileManager.createColorAdjustedImage(guid, page, colorFuzz)
            delay(100)
            fileManager.createCroppedImage(guid, page, cropFuzz)
            delay(100)
            fileManager.createThumbnails(guid, page)
        }
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

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.declaringClass)

        private val datePatterns = mapOf(
            Regex("(3[01]|[12][0-9]|0?[1-9])\\.(1[012]|0?[1-9])\\.(\\d{4})") to "dd.MM.yyyy",
            Regex("(3[01]|[12][0-9]|0?[1-9])\\.(1[012]|0?[1-9])\\.(\\d{2})") to "dd.MM.yy",
        )
    }
}