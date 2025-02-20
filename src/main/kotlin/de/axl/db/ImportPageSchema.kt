package de.axl.db

import de.axl.db.ImportDocumentDbService.ImportDocument
import de.axl.dbQuery
import de.axl.serialization.api.ExposedImportBlock
import de.axl.serialization.api.ExposedImportLine
import de.axl.serialization.api.ExposedImportPage
import de.axl.serialization.api.ExposedImportWord
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class ImportPageDbService(database: Database) {
    object ImportPage : Table() {
        val id = integer("id").autoIncrement()
        val guid = varchar("guid", length = 36)
        val page = integer("page")
        val width = integer("width")
        val height = integer("height")
        val deskew = integer("deskew").default(40)
        val colorFuzz = integer("colorFuzz").default(10)
        val cropFuzz = integer("cropFuzz").default(10)
        val document = reference("document", ImportDocument.id, onDelete = ReferenceOption.CASCADE)

        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            SchemaUtils.create(ImportPage)
        }
    }

    suspend fun findById(id: Int): ExposedImportPage? = dbQuery {
        (ImportPage innerJoin ImportDocument).selectAll().where { ImportPage.id eq id }.mapExposed().singleOrNull()
    }

    suspend fun findTextById(id: Int): String? = dbQuery {
        val pageId = ImportPage.selectAll().where { ImportPage.id eq id }.singleOrNull()?.get(ImportPage.id) ?: return@dbQuery null
        ImportBlockDbService.ImportBlock
            .join(ImportLineDbService.ImportLine, JoinType.INNER, ImportLineDbService.ImportLine.block, ImportBlockDbService.ImportBlock.id)
            .join(ImportWordDbService.ImportWord, JoinType.INNER, ImportWordDbService.ImportWord.line, ImportLineDbService.ImportLine.id)
            .join(ImportPage, JoinType.INNER, ImportBlockDbService.ImportBlock.page, ImportPage.id)
            .select(ImportBlockDbService.ImportBlock.y, ImportLineDbService.ImportLine.y, ImportWordDbService.ImportWord.x, ImportWordDbService.ImportWord.text)
            .where { ImportPage.id eq pageId }
            .orderBy(
                ImportBlockDbService.ImportBlock.y to SortOrder.ASC,
                ImportLineDbService.ImportLine.y to SortOrder.ASC,
                ImportWordDbService.ImportWord.x to SortOrder.ASC
            )
            .groupBy { it[ImportBlockDbService.ImportBlock.y] }
            .map { (_, blockGroup) ->
                blockGroup.groupBy { it[ImportLineDbService.ImportLine.y] }
                    .map { (_, lineGroup) ->
                        lineGroup.joinToString(" ") { it[ImportWordDbService.ImportWord.text] }
                    }
                    .joinToString("\n")
            }
            .joinToString("\n")
    }

    suspend fun findByDocumentId(id: Int): List<ExposedImportPage> = dbQuery {
        (ImportPage innerJoin ImportDocument).selectAll().where { ImportPage.document eq id }.mapExposed()
    }

    suspend fun create(page: ExposedImportPage): Int = dbQuery {
        ImportPage.insert {
            it[guid] = page.guid
            it[ImportPage.page] = page.page
            it[width] = page.width
            it[height] = page.height
            it[deskew] = page.deskew
            it[colorFuzz] = page.colorFuzz
            it[cropFuzz] = page.cropFuzz
            it[document] = page.documentId
        }[ImportPage.id]
    }

    suspend fun createPageContent(page: ExposedImportPage, blocks: Map<ExposedImportBlock, Map<ExposedImportLine, List<ExposedImportWord>>>) = dbQuery {
        blocks.forEach { (block, lines) ->
            val blockId = ImportBlockDbService.ImportBlock.insert {
                it[x] = block.x
                it[y] = block.y
                it[width] = block.width
                it[height] = block.height
                it[ImportBlockDbService.ImportBlock.page] = page.id
            }[ImportBlockDbService.ImportBlock.id]
            lines.forEach { (line, words) ->
                val lineId = ImportLineDbService.ImportLine.insert {
                    it[x] = line.x
                    it[y] = line.y
                    it[width] = line.width
                    it[height] = line.height
                    it[ImportLineDbService.ImportLine.block] = blockId
                }[ImportLineDbService.ImportLine.id]
                words.forEach { word ->
                    val wordId = ImportWordDbService.ImportWord.insert {
                        it[text] = word.text
                        it[x] = word.x
                        it[y] = word.y
                        it[width] = word.width
                        it[height] = word.height
                        it[ocrConfidence] = word.ocrConfidence
                        it[ImportWordDbService.ImportWord.line] = lineId
                    }[ImportWordDbService.ImportWord.id]
                    word.spellingSuggestions.forEach { suggestion ->
                        ImportWordDbService.ImportSpellingSuggestion.insert {
                            it[ImportWordDbService.ImportSpellingSuggestion.suggestion] = suggestion.suggestion
                            it[ImportWordDbService.ImportSpellingSuggestion.word] = wordId
                        }
                    }
                }
            }
        }
    }

    suspend fun update(page: ExposedImportPage) {
        dbQuery {
            ImportPage.update({ ImportPage.id eq page.id }) {
                it[ImportPage.page] = page.page
                it[width] = page.width
                it[height] = page.height
                it[deskew] = page.deskew
                it[colorFuzz] = page.colorFuzz
                it[cropFuzz] = page.cropFuzz
            }
        }
    }

    suspend fun delete(id: Int) {
        dbQuery {
            ImportPage.deleteWhere { ImportPage.id.eq(id) }
        }
    }

    private suspend fun Query.mapExposed(): List<ExposedImportPage> = map {
        ExposedImportPage(
            it[ImportPage.id],
            it[ImportPage.guid],
            it[ImportPage.page],
            it[ImportPage.width],
            it[ImportPage.height],
            it[ImportPage.deskew],
            it[ImportPage.colorFuzz],
            it[ImportPage.cropFuzz],
            it[ImportDocument.id],
            dbQuery {
                ImportBlockDbService.ImportBlock.selectAll()
                    .where { ImportBlockDbService.ImportBlock.page eq it[ImportPage.id] }
                    .map { block -> block[ImportBlockDbService.ImportBlock.id] }
            }
        )
    }
}