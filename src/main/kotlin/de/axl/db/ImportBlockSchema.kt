package de.axl.db

import de.axl.db.ImportPageDbService.ImportPage
import de.axl.dbQuery
import de.axl.serialization.api.ExposedImportBlockFull
import de.axl.serialization.api.ExposedImportLineFull
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class ExposedImportBlock(
    val id: Int = 0,
    val text: String = "",
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val pageId: Int = 0,
    val lines: List<Int> = emptyList()
)

class ImportBlockDbService(database: Database) {
    object ImportBlock : Table() {
        val id = integer("id").autoIncrement()
        val text = text("text")
        val x = integer("x")
        val y = integer("y")
        val width = integer("width")
        val height = integer("height")
        val page = reference("page", ImportPage.id, onDelete = ReferenceOption.CASCADE)

        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            SchemaUtils.create(ImportBlock)
        }
    }

    suspend fun findById(id: Int): ExposedImportBlock? = dbQuery {
        ImportBlock.selectAll().where { ImportBlock.id eq id }.mapExposed().singleOrNull()
    }

    suspend fun findByPageId(id: Int): List<ExposedImportBlock> = dbQuery {
        ImportBlock.selectAll().where { ImportBlock.page eq id }.mapExposed()
    }

    suspend fun findByPageIdFull(pageId: Int): List<ExposedImportBlockFull> = dbQuery {
        ImportBlock.selectAll().where { ImportBlock.page eq pageId }.mapFull()
    }

    suspend fun create(block: ExposedImportBlock, pageId: Int): Int = dbQuery {
        ImportBlock.insert {
            it[text] = block.text
            it[x] = block.x
            it[y] = block.y
            it[width] = block.width
            it[height] = block.height
            it[page] = pageId
        }[ImportBlock.id]
    }

    suspend fun delete(id: Int) {
        dbQuery {
            ImportBlock.deleteWhere { ImportBlock.id.eq(id) }
        }
    }

    suspend fun deleteByPage(pageId: Int) {
        dbQuery {
            ImportBlock.deleteWhere { page.eq(pageId) }
        }
    }

    private suspend fun Query.mapExposed(): List<ExposedImportBlock> = map {
        ExposedImportBlock(
            it[ImportBlock.id],
            it[ImportBlock.text],
            it[ImportBlock.x],
            it[ImportBlock.y],
            it[ImportBlock.width],
            it[ImportBlock.height],
            it[ImportBlock.page],
            dbQuery {
                ImportLineDbService.ImportLine.selectAll()
                    .where { ImportLineDbService.ImportLine.block eq it[ImportBlock.id] }
                    .map { line -> line[ImportLineDbService.ImportLine.id] }
            }
        )
    }

    private fun Query.mapFull(): List<ExposedImportBlockFull> = map {
        ExposedImportBlockFull(
            it[ImportBlock.id],
            it[ImportBlock.text],
            it[ImportBlock.x],
            it[ImportBlock.y],
            it[ImportBlock.width],
            it[ImportBlock.height],
            it[ImportBlock.page],
            ImportLineDbService.ImportLine.selectAll()
                .where { ImportLineDbService.ImportLine.block eq it[ImportBlock.id] }
                .map { line ->
                    ExposedImportLineFull(
                        line[ImportLineDbService.ImportLine.id],
                        line[ImportLineDbService.ImportLine.text],
                        line[ImportLineDbService.ImportLine.x],
                        line[ImportLineDbService.ImportLine.y],
                        line[ImportLineDbService.ImportLine.width],
                        line[ImportLineDbService.ImportLine.height],
                        line[ImportLineDbService.ImportLine.block],
                        ImportWordDbService.ImportWord.selectAll()
                            .where { ImportWordDbService.ImportWord.line eq line[ImportLineDbService.ImportLine.id] }
                            .map {
                                ExposedImportWord(
                                    it[ImportWordDbService.ImportWord.id],
                                    it[ImportWordDbService.ImportWord.text],
                                    it[ImportWordDbService.ImportWord.x],
                                    it[ImportWordDbService.ImportWord.y],
                                    it[ImportWordDbService.ImportWord.width],
                                    it[ImportWordDbService.ImportWord.height],
                                    it[ImportWordDbService.ImportWord.ocrConfidence],
                                    it[ImportWordDbService.ImportWord.line]
                                )
                            }
                    )
                }
        )
    }
}