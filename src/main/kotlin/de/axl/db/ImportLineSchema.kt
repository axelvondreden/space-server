package de.axl.db

import de.axl.db.ImportBlockDbService.ImportBlock
import de.axl.dbQuery
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class ExposedImportLine(
    val id: Int = 0,
    val text: String = "",
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val blockId: Int = 0,
    val words: List<Int> = emptyList()
)

class ImportLineDbService(database: Database) {
    object ImportLine : Table() {
        val id = integer("id").autoIncrement()
        val text = text("text")
        val x = integer("x")
        val y = integer("y")
        val width = integer("width")
        val height = integer("height")
        val block = reference("block", ImportBlock.id, onDelete = ReferenceOption.CASCADE)

        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            SchemaUtils.create(ImportLine)
        }
    }

    suspend fun findById(id: Int): ExposedImportLine? = dbQuery {
        ImportLine.selectAll().where { ImportLine.id eq id }.mapExposed().singleOrNull()
    }

    suspend fun findByBlockId(id: Int): List<ExposedImportLine> = dbQuery {
        ImportLine.selectAll().where { ImportLine.block eq id }.orderBy(ImportLine.y).mapExposed()
    }

    suspend fun create(line: ExposedImportLine, blockId: Int): Int = dbQuery {
        ImportLine.insert {
            it[text] = line.text
            it[x] = line.x
            it[y] = line.y
            it[width] = line.width
            it[height] = line.height
            it[block] = blockId
        }[ImportLine.id]
    }

    suspend fun delete(id: Int) {
        dbQuery {
            ImportLine.deleteWhere { ImportLine.id.eq(id) }
        }
    }

    private suspend fun Query.mapExposed(): List<ExposedImportLine> = map {
        ExposedImportLine(
            it[ImportLine.id],
            it[ImportLine.text],
            it[ImportLine.x],
            it[ImportLine.y],
            it[ImportLine.width],
            it[ImportLine.height],
            it[ImportLine.block],
            dbQuery {
                ImportWordDbService.ImportWord.selectAll()
                    .where { ImportWordDbService.ImportWord.line eq it[ImportLine.id] }
                    .map { word -> word[ImportWordDbService.ImportWord.id] }
            }
        )
    }
}