package de.axl.serialization.api

import de.axl.db.ImportDocumentDbService.OCRLanguage
import de.axl.db.ImportPageDbService
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime

@Serializable
data class ExposedImportDocument(
    val id: Int = 0,
    val guid: String,
    val language: OCRLanguage = OCRLanguage.DEU,
    @Contextual val date: LocalDate? = null,
    @Contextual val createdAt: LocalDateTime = LocalDateTime.now(),
    @Contextual val updatedAt: LocalDateTime? = null,
    val pages: List<ExposedImportDocumentPage> = emptyList()
)

@Serializable
data class ExposedImportDocumentPage(val page: Int, val id: Int)

@Serializable
data class ExposedImportPage(
    val id: Int = 0,
    val guid: String,
    val page: Int,
    val width: Int = 0,
    val height: Int = 0,
    val layout: String = ImportPageDbService.Orientation.PORTRAIT.name.lowercase(),
    val crop: ImportPageCrop? = null,
    val grayscale: Boolean = false,
    val enhance: Boolean = true,
    val backgroundFilter: Int = 15,
    val noiseFilter: Int = 5,
    val unrotate: Boolean = true,
    val preserveSize: Boolean = false,
    val textSmoothing: Int? = null,
    val trimBackground: Boolean = true,
    val borderPadding: Int = 0,
    val documentId: Int = 0,
    val blocks: List<Int> = emptyList()
)

@Serializable
data class ImportPageCrop(val left: Int, val top: Int, val right: Int, val bottom: Int)

@Serializable
data class Rectangle(val x: Int, val y: Int, val width: Int, val height: Int)

@Serializable
data class NewWord(val text: String, val x: Int, val y: Int, val width: Int, val height: Int)

@Serializable
data class ExposedImportBlock(
    val id: Int = 0,
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val pageId: Int = 0,
    val lines: List<Int> = emptyList()
)

@Serializable
data class ExposedImportBlockFull(
    val id: Int = 0,
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val pageId: Int = 0,
    val lines: List<ExposedImportLineFull> = emptyList()
)

@Serializable
data class ExposedImportLine(
    val id: Int = 0,
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val blockId: Int = 0,
    val words: List<Int> = emptyList()
)

@Serializable
data class ExposedImportLineFull(
    val id: Int = 0,
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val blockId: Int = 0,
    val words: List<ExposedImportWord> = emptyList()
)

@Serializable
data class ExposedImportWord(
    val id: Int = 0,
    val text: String,
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val ocrConfidence: Double? = 0.0,
    val spellingSuggestions: List<ExposedImportSpellingSuggestion> = emptyList(),
    val lineId: Int = 0
)

@Serializable
data class ExposedImportSpellingSuggestion(val id: Int = 0, val suggestion: String)