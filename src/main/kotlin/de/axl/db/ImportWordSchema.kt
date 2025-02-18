package de.axl.db

import de.axl.db.ImportLineDbService.ImportLine
import de.axl.dbQuery
import de.axl.serialization.api.ExposedImportWord
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class ImportWordDbService(database: Database) {
    object ImportWord : Table() {
        val id = integer("id").autoIncrement()
        val text = text("text")
        val x = integer("x")
        val y = integer("y")
        val width = integer("width")
        val height = integer("height")
        val ocrConfidence = double("ocrConfidence").nullable()
        val line = reference("line", ImportLine.id, onDelete = ReferenceOption.CASCADE)

        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            SchemaUtils.create(ImportWord)
        }
    }

    suspend fun findById(id: Int): ExposedImportWord? = dbQuery {
        ImportWord.selectAll().where { ImportWord.id eq id }.mapExposed().singleOrNull()
    }

    suspend fun create(word: ExposedImportWord, lineId: Int): Int = dbQuery {
        ImportWord.insert {
            it[text] = word.text
            it[x] = word.x
            it[y] = word.y
            it[width] = word.width
            it[height] = word.height
            it[ocrConfidence] = word.ocrConfidence
            it[line] = lineId
        }[ImportWord.id]
    }

    suspend fun updateText(id: Int, newText: String) = dbQuery {
        ImportWord.update({ ImportWord.id eq id }) {
            it[text] = newText
        }
    }

    suspend fun delete(id: Int) {
        dbQuery {
            ImportWord.deleteWhere { ImportWord.id.eq(id) }
        }
    }

    private fun Query.mapExposed(): List<ExposedImportWord> = map {
        ExposedImportWord(
            it[ImportWord.id],
            it[ImportWord.text],
            it[ImportWord.x],
            it[ImportWord.y],
            it[ImportWord.width],
            it[ImportWord.height],
            it[ImportWord.ocrConfidence],
            it[ImportWord.line]
        )
    }
}