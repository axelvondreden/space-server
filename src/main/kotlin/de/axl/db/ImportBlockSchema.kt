package de.axl.db

import de.axl.db.ImportPageDbService.ImportPage
import de.axl.dbQuery
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class ExposedImportBlock(
    val id: Int,
    val text: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
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

    suspend fun findById(id: Int): ExposedImportBlock? {
        return dbQuery {
            ImportBlock.selectAll()
                .where { ImportBlock.id eq id }
                .map {
                    ExposedImportBlock(
                        it[ImportBlock.id],
                        it[ImportBlock.text],
                        it[ImportBlock.x],
                        it[ImportBlock.y],
                        it[ImportBlock.width],
                        it[ImportBlock.height]
                    )
                }
                .singleOrNull()
        }
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

    suspend fun update(block: ExposedImportBlock) {
        dbQuery {
            ImportBlock.update({ ImportBlock.id eq block.id }) {
                it[text] = block.text
                it[x] = block.x
                it[y] = block.y
                it[width] = block.width
                it[height] = block.height
            }
        }
    }

    suspend fun delete(id: Int) {
        dbQuery {
            ImportBlock.deleteWhere { ImportBlock.id.eq(id) }
        }
    }
}