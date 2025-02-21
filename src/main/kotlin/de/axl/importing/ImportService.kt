package de.axl.importing

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import de.axl.db.*
import de.axl.db.ImportDocumentDbService.OCRLanguage
import de.axl.db.ImportPageDbService.Orientation
import de.axl.files.ImportFileManager
import de.axl.importing.events.ImportStateEvent
import de.axl.runCommand
import de.axl.serialization.alto.Alto
import de.axl.serialization.api.*
import kotlinx.coroutines.flow.MutableSharedFlow
import org.languagetool.JLanguageTool
import org.languagetool.Language
import org.languagetool.language.BritishEnglish
import org.languagetool.language.GermanyGerman
import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import javax.imageio.ImageIO


class ImportService(
    private val fileManager: ImportFileManager,
    private val docService: ImportDocumentDbService,
    private val pageService: ImportPageDbService,
    private val blockService: ImportBlockDbService,
    private val lineService: ImportLineDbService,
    private val wordService: ImportWordDbService
) {

    private var importing = false

    val dataPath get() = fileManager.dataPath

    suspend fun findAllDocuments(): List<ExposedImportDocument> = docService.findAll()
    suspend fun findDocumentById(id: Int): ExposedImportDocument? = docService.findById(id)
    suspend fun updateDocument(import: ExposedImportDocument) = docService.update(import)
    suspend fun deleteDocument(id: Int) = docService.delete(id)

    suspend fun findPageById(id: Int): ExposedImportPage? = pageService.findById(id)
    suspend fun findPageTextById(id: Int): String? = pageService.findTextById(id)
    suspend fun updatePage(page: ExposedImportPage) = pageService.update(page)
    suspend fun deletePage(id: Int) = pageService.delete(id)

    suspend fun findBlockById(id: Int): ExposedImportBlock? = blockService.findById(id)
    suspend fun getFullBlocksForPage(page: ExposedImportPage): List<ExposedImportBlockFull> = blockService.findByPageIdFull(page.id)
    suspend fun deleteBlock(id: Int) = blockService.delete(id)
    suspend fun findLineById(id: Int): ExposedImportLine? = lineService.findById(id)

    suspend fun deleteLine(id: Int) = lineService.delete(id)
    suspend fun findWordById(id: Int): ExposedImportWord? = wordService.findById(id)
    suspend fun updateWordText(id: Int, text: String) {
        wordService.updateText(id, text)
    }

    suspend fun deleteWord(id: Int) = wordService.delete(id)

    fun createCleanedImage(page: ExposedImportPage) {
        fileManager.createCleanedImage(page)
    }

    fun createThumbnails(page: ExposedImportPage) = fileManager.createThumbnails(page.guid)

    fun getPdfOriginal(guid: String) = fileManager.getPdfOriginal(guid)
    fun getImageOriginal(page: ExposedImportPage) = fileManager.getImageOriginal(page.guid)
    fun getImageCleaned(page: ExposedImportPage) = fileManager.getImage(page.guid)
    fun getThumbnail(page: ExposedImportPage, size: String) = fileManager.getThumbnail(page.guid, size)

    suspend fun handleUploads(importFlow: MutableSharedFlow<ImportStateEvent>) {
        if (importing) return
        importing = true
        val files = fileManager.getUploadedFiles()
        logger.info("Found ${files.size} new files")
        files.forEachIndexed { index, file ->
            if (file.exists()) {
                val guid = UUID.randomUUID().toString()
                val state = ImportStateEvent(importing = true, currentFile = index + 1, fileCount = files.size, guid = guid)
                handlePdfUpload(file, guid, importFlow, state)
            } else {
                logger.error("File ${file.name} does not exist")
            }
        }
        importFlow.emit(ImportStateEvent(importing = false, message = "Finished importing files"))
        importing = false
    }

    private suspend fun handlePdfUpload(file: File, pdfGuid: String, importFlow: MutableSharedFlow<ImportStateEvent>, state: ImportStateEvent) {
        importFlow.emit(state.copy(progress = 0.1, message = "Moving file and creating import directory"))
        fileManager.moveFileToImport(file, pdfGuid)

        logger.info("Creating import for $pdfGuid")
        val document = docService.findById(docService.create(ExposedImportDocument(guid = pdfGuid, language = OCRLanguage.DEU)))!!

        val pages = fileManager.createImagesFromOriginalPdf(pdfGuid, importFlow, state.copy(progress = 0.2), 0.1)
        val pageStep = 0.7 / pages.size
        val dbPages = pages.map { (page, guid) ->
            logger.info("Running image editing on page $page / ${pages.size}")
            importFlow.emit(state.copy(progress = 0.3 + (pageStep * (page - 1)) + ((pageStep / 3) * 0), message = "Creating cleaned image for page $page"))
            val img = ImageIO.read(fileManager.getImageOriginal(guid))
            val exposedPage = ExposedImportPage(
                guid = guid,
                page = page,
                documentId = document.id,
                layout = if (img.width > img.height) Orientation.LANDSCAPE.name.lowercase() else Orientation.PORTRAIT.name.lowercase()
            )
            fileManager.createCleanedImage(exposedPage)

            importFlow.emit(state.copy(progress = 0.3 + (pageStep * (page - 1)) + ((pageStep / 3) * 1), message = "Creating thumbnails for page $page"))
            fileManager.createThumbnails(guid)

            importFlow.emit(state.copy(progress = 0.3 + (pageStep * (page - 1)) + ((pageStep / 3) * 2), message = "Running OCR on page $page"))
            extractTextAndCreateDbObjects(exposedPage)
        }

        val date = findDateFromText(dbPages.firstOrNull { it.first.page == 1 }?.second ?: "")

        val isInvoice = guessIsInvoice(dbPages.map { it.second }.joinToString("\n") { it.trim() })
        logger.info("Guessed Invoice: $isInvoice")

        docService.update(document.copy(date = date, isInvoice = isInvoice))

        importFlow.emit(state.copy(progress = 1.0, message = "Import complete", completedDocId = document.id))
    }

    suspend fun extractTextAndCreateDbObjects(page: ExposedImportPage): Pair<ExposedImportPage, String> {
        val document = docService.findById(page.documentId)!!
        val image = fileManager.getImage(page.guid)
        logger.info("Running OCR (${document.language.name}) on page ${page.page}: ${image.name}")
        val xml = getAltoForImage(image, document.language.name)
        val mapper = XmlMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val alto = mapper.readValue(xml, Alto::class.java)
        val printSpace = alto.layout.page.printSpace
        logger.info("OCR finished!")
        val pageId = if (page.id > 0) {
            logger.info("Deleting old entries...")
            updatePage(page.copy(width = printSpace.width.toInt(), height = printSpace.height.toInt()))
            blockService.deleteByPage(page.id)
            page.id
        } else {
            pageService.create(page.copy(width = printSpace.width.toInt(), height = printSpace.height.toInt()))
        }
        val dbPage = pageService.findById(pageId)!!
        var text = ""

        val langTool = JLanguageTool(langSpellcheckMapping[document.language.name]).apply {
            allRules.filterNot { it.isDictionaryBasedSpellingRule }.forEach { disableRule(it.id) }
        }

        logger.info("Collecting new entries...")
        val blocks = mutableMapOf<ExposedImportBlock, Map<ExposedImportLine, List<ExposedImportWord>>>()
        printSpace.composedBlocks.sortedBy { (it.vpos.toInt() * 10000) + it.hpos.toInt() }.forEach { cBlock ->
            cBlock.textBlocks.sortedBy { (it.vpos.toInt() * 10000) + it.hpos.toInt() }.forEach { block ->
                val map = mutableMapOf<ExposedImportLine, List<ExposedImportWord>>()
                var dbBlock = ExposedImportBlock(x = block.hpos.toInt(), y = block.vpos.toInt(), width = block.width.toInt(), height = block.height.toInt())
                block.textLines.sortedBy { (it.vpos.toInt() * 10000) + it.hpos.toInt() }.forEach { line ->
                    var dbLine = ExposedImportLine(x = line.hpos.toInt(), y = line.vpos.toInt(), width = line.width.toInt(), height = line.height.toInt())
                    val exposedWords = line.words.sortedBy { it.hpos.toInt() }.map { word ->
                        text += "${word.content} "
                        val spellingSuggestions = langTool.check(word.content).flatMap {
                            it.suggestedReplacementObjects.map { suggestion ->
                                ExposedImportSpellingSuggestion(suggestion = suggestion.replacement)
                            }
                        }
                        ExposedImportWord(
                            text = word.content,
                            x = word.hpos.toInt(),
                            y = word.vpos.toInt(),
                            width = word.width.toInt(),
                            height = word.height.toInt(),
                            ocrConfidence = word.confidence.toDouble(),
                            spellingSuggestions = spellingSuggestions
                        )
                    }
                    text += "\n"
                    map[dbLine] = exposedWords
                }
                blocks[dbBlock] = map
            }
        }
        logger.info("Creating new entries...")
        pageService.createPageContent(dbPage, blocks)
        logger.info("Finished creating new entries!")
        return dbPage to text
    }

    private fun getAltoForImage(file: File, lang: String): String {
        runCommand(file.parentFile, "tesseract ${file.name} ${file.nameWithoutExtension} -l ${lang.lowercase()} alto", logger)
        val xml = File(file.parentFile, file.nameWithoutExtension + ".xml")
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
                    val date = LocalDate.parse(match.value, DateTimeFormatter.ofPattern(entry.value))
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

    private fun guessIsInvoice(text: String) = text.lines().any { line ->
        invoiceTextPatterns.any { line.contains(Regex("\\b$it\\b", RegexOption.IGNORE_CASE)) }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.declaringClass)

        private val datePatterns = mapOf(
            Regex("(3[01]|[12][0-9]|0?[1-9])\\.(1[012]|0?[1-9])\\.(\\d{4})") to "dd.MM.yyyy",
            Regex("(3[01]|[12][0-9]|0?[1-9])\\.(1[012]|0?[1-9])\\.(\\d{2})") to "dd.MM.yy",
        )

        private val invoiceTextPatterns = listOf("netto", "brutto", "zu zahlen", "gesamtpreis", "rechnungsdatum")

        private val langSpellcheckMapping = mapOf<String, Language>(
            "ENG" to BritishEnglish(),
            "DEU" to GermanyGerman()
        )
    }
}