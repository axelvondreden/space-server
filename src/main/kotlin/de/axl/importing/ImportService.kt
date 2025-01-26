package de.axl.importing

import de.axl.db.*
import de.axl.files.FileManagerImport
import de.axl.importing.events.ImportStateEvent
import de.axl.runCommand
import kotlinx.coroutines.flow.MutableSharedFlow
import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDate
import java.util.*

class ImportService(
    private val fileManager: FileManagerImport,
    private val docService: ImportDocumentDbService,
    private val pageService: ImportPageDbService,
    private val blockService: ImportBlockDbService,
    private val lineService: ImportLineDbService,
    private val wordService: ImportWordDbService
) {

    private var importing = false

    val dataPath get() = fileManager.dataPath

    suspend fun findAllDocuments(): List<ExposedImportDocument> = docService.findAll()
    suspend fun findDocumentByGuid(guid: String): ExposedImportDocument? = docService.findByGuid(guid)
    suspend fun updateDocument(import: ExposedImportDocument) = docService.update(import)
    suspend fun deleteDocument(guid: String) = docService.delete(guid)

    suspend fun findPageById(id: Int): ExposedImportPage? = pageService.findById(id)
    suspend fun updatePage(page: ExposedImportPage) = pageService.update(page)
    suspend fun deletePage(id: Int) = pageService.delete(id)

    suspend fun findBlockById(id: Int): ExposedImportBlock? = blockService.findById(id)
    suspend fun updateBlock(block: ExposedImportBlock) = blockService.update(block)
    suspend fun deleteBlock(id: Int) = blockService.delete(id)

    suspend fun findLineById(id: Int): ExposedImportLine? = lineService.findById(id)
    suspend fun updateLine(line: ExposedImportLine) = lineService.update(line)
    suspend fun deleteLine(id: Int) = lineService.delete(id)

    suspend fun findWordById(id: Int): ExposedImportWord? = wordService.findById(id)
    suspend fun updateWord(word: ExposedImportWord) = wordService.update(word)
    suspend fun deleteWord(id: Int) = wordService.delete(id)

    suspend fun createDeskewedImage(page: ExposedImportPage, deskew: Int) {
        val newPage = page.copy(deskew = deskew)
        updatePage(newPage)
        fileManager.createDeskewedImage(newPage.documentGuid, newPage.page, newPage.deskew)
    }

    suspend fun createColorAdjustedImage(page: ExposedImportPage, fuzz: Int) {
        val newPage = page.copy(colorFuzz = fuzz)
        updatePage(newPage)
        fileManager.createColorAdjustedImage(newPage.documentGuid, newPage.page, newPage.colorFuzz)
    }

    suspend fun createCroppedImage(page: ExposedImportPage, fuzz: Int) {
        val newPage = page.copy(cropFuzz = fuzz)
        updatePage(newPage)
        fileManager.createCroppedImage(newPage.documentGuid, newPage.page, newPage.cropFuzz)
    }

    fun createThumbnails(page: ExposedImportPage) = fileManager.createThumbnails(page.documentGuid, page.page)

    fun getPdfOriginal(guid: String) = fileManager.getPdfOriginal(guid)

    fun getImageOriginal(page: ExposedImportPage) = fileManager.getImageOriginal(page.documentGuid, page.page)
    fun getImageDeskewed(page: ExposedImportPage) = fileManager.getImageDeskewed(page.documentGuid, page.page)
    fun getImageColorAdjusted(page: ExposedImportPage) = fileManager.getImageColorAdjusted(page.documentGuid, page.page)
    fun getImage(page: ExposedImportPage) = fileManager.getImage(page.documentGuid, page.page)
    fun getThumbnail(page: ExposedImportPage, size: String) = fileManager.getThumbnail(page.documentGuid, page.page, size)

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

    private suspend fun handlePdfUpload(file: File, guid: String, importFlow: MutableSharedFlow<ImportStateEvent>, state: ImportStateEvent, fullStep: Double) {
        val step = fullStep / 7.0

        fileManager.moveFileToImport(file, guid)

        fileManager.createImagesFromOriginalPdf(guid)

        editImageComplete(guid)

        logger.info("Creating import for $guid")
        docService.create(ExposedImportDocument(guid, ocrLanguage = OCRLanguage.DEU))

        val document = docService.findByGuid(guid)!!
        extractTextAndCreateDbObjects(document)

        val date = findDateFromText("")
        if (date != null) {
            docService.update(document.copy(date = date))
        }
        importFlow.emit(state.copy(progress = state.progress?.plus((step * 7)), message = "Import complete", completedFile = true))
    }

    fun editImageComplete(guid: String, deskew: Int = 40, colorFuzz: Int = 10, cropFuzz: Int = 20) {
        logger.info("Running deskew on $guid: deskew: $deskew% colorFuzz: $colorFuzz% cropFuzz: $cropFuzz%")
        val pageCount = fileManager.getImagesOriginal(guid).size
        for (page in 1..pageCount) {
            fileManager.createDeskewedImage(guid, page, deskew)
            fileManager.createColorAdjustedImage(guid, page, colorFuzz)
            fileManager.createCroppedImage(guid, page, cropFuzz)
            fileManager.createThumbnails(guid, page)
        }
    }

    private fun extractTextAndCreateDbObjects(document: ExposedImportDocument) {
        val images = fileManager.getImages(document.guid)
        images.forEach { image ->
            val pageNr = image.nameWithoutExtension.substringAfterLast("-").toInt()
            logger.info("Running OCR on page $pageNr: ${image.name}")
            val alto = getAltoForImage(image, document.ocrLanguage.lang)
            logger.info(alto)
        }
    }

    private fun getAltoForImage(file: File, lang: String): String {
        runCommand(file.parentFile, "tesseract ${file.name} ${file.nameWithoutExtension} -l $lang alto", logger)
        val xml = File(file.nameWithoutExtension + ".xml")
        val text = xml.readText()
        xml.delete()
        return text
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