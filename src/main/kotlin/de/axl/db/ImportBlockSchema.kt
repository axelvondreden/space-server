package de.axl.db

import de.axl.db.ImportPageDbService.ImportPage
import de.axl.db.ImportWordDbService.ImportSpellingSuggestion
import de.axl.db.ImportWordDbService.ImportWord
import de.axl.dbQuery
import de.axl.serialization.api.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class ImportBlockDbService(database: Database) {
    object ImportBlock : Table() {
        val id = integer("id").autoIncrement()
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
                        line[ImportLineDbService.ImportLine.x],
                        line[ImportLineDbService.ImportLine.y],
                        line[ImportLineDbService.ImportLine.width],
                        line[ImportLineDbService.ImportLine.height],
                        line[ImportLineDbService.ImportLine.block],
                        ImportWord.selectAll()
                            .where { ImportWord.line eq line[ImportLineDbService.ImportLine.id] }
                            .map { word ->
                                ExposedImportWord(
                                    word[ImportWord.id],
                                    word[ImportWord.text],
                                    word[ImportWord.x],
                                    word[ImportWord.y],
                                    word[ImportWord.width],
                                    word[ImportWord.height],
                                    word[ImportWord.ocrConfidence],
                                    ImportSpellingSuggestion.selectAll().where { ImportSpellingSuggestion.word eq word[ImportWord.id] }.map {
                                        ExposedImportSpellingSuggestion(it[ImportSpellingSuggestion.id], it[ImportSpellingSuggestion.suggestion])
                                    },
                                    word[ImportWord.line]
                                )
                            }
                    )
                }
        )
    }
}