package de.axl.db

import de.axl.db.ImportDocumentDbService.ImportDocument
import de.axl.dbQuery
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class ExposedImportPage(
    val id: Int = 0,
    val guid: String,
    val text: String = "",
    val page: Int,
    val width: Int = 0,
    val height: Int = 0,
    val deskew: Int = 40,
    val colorFuzz: Int = 10,
    val cropFuzz: Int = 20,
    val documentId: Int = 0,
    val blocks: List<Int> = emptyList()
)

class ImportPageDbService(database: Database) {
    object ImportPage : Table() {
        val id = integer("id").autoIncrement()
        val guid = varchar("guid", length = 36).uniqueIndex()
        val text = text("text")
        val page = integer("page")
        val width = integer("width")
        val height = integer("height")
        val deskew = integer("deskew").default(40)
        val colorFuzz = integer("colorFuzz").default(10)
        val cropFuzz = integer("cropFuzz").default(20)
        val document = reference("document", ImportDocument.id, onDelete = ReferenceOption.CASCADE)

        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            SchemaUtils.create(ImportPage)
        }
    }

    suspend fun findById(id: Int): ExposedImportPage? {
        return dbQuery {
            (ImportPage innerJoin ImportDocument).selectAll().where { ImportPage.id eq id }.mapExposed().singleOrNull()
        }
    }

    suspend fun findByDocumentId(id: Int): List<ExposedImportPage> {
        return dbQuery {
            (ImportPage innerJoin ImportDocument).selectAll().where { ImportPage.document eq id }.mapExposed()
        }
    }

    suspend fun create(page: ExposedImportPage): Int = dbQuery {
        ImportPage.insert {
            it[guid] = page.guid
            it[text] = page.text
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
                it[text] = block.text
                it[x] = block.x
                it[y] = block.y
                it[width] = block.width
                it[height] = block.height
                it[ImportBlockDbService.ImportBlock.page] = page.id
            }[ImportBlockDbService.ImportBlock.id]
            lines.forEach { (line, words) ->
                val lineId = ImportLineDbService.ImportLine.insert {
                    it[text] = line.text
                    it[x] = line.x
                    it[y] = line.y
                    it[width] = line.width
                    it[height] = line.height
                    it[ImportLineDbService.ImportLine.block] = blockId
                }[ImportLineDbService.ImportLine.id]
                words.forEach { word ->
                    ImportWordDbService.ImportWord.insert {
                        it[text] = word.text
                        it[x] = word.x
                        it[y] = word.y
                        it[width] = word.width
                        it[height] = word.height
                        it[ImportWordDbService.ImportWord.line] = lineId
                    }
                }
            }
        }
    }

    suspend fun update(page: ExposedImportPage) {
        dbQuery {
            ImportPage.update({ ImportPage.id eq page.id }) {
                it[text] = page.text
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
            it[ImportPage.text],
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