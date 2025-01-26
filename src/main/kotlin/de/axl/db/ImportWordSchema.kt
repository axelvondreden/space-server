package de.axl.db

import de.axl.db.ImportLineDbService.ImportLine
import de.axl.dbQuery
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class ExposedImportWord(
    val id: Int,
    val text: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val ocrConfidence: Double?
)

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

    suspend fun findById(id: Int): ExposedImportWord? {
        return dbQuery {
            ImportWord.selectAll()
                .where { ImportWord.id eq id }
                .map {
                    ExposedImportWord(
                        it[ImportWord.id],
                        it[ImportWord.text],
                        it[ImportWord.x],
                        it[ImportWord.y],
                        it[ImportWord.width],
                        it[ImportWord.height],
                        it[ImportWord.ocrConfidence]
                    )
                }
                .singleOrNull()
        }
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

    suspend fun update(word: ExposedImportWord) {
        dbQuery {
            ImportWord.update({ ImportWord.id eq word.id }) {
                it[text] = word.text
                it[x] = word.x
                it[y] = word.y
                it[width] = word.width
                it[height] = word.height
                it[ocrConfidence] = word.ocrConfidence
            }
        }
    }

    suspend fun delete(id: Int) {
        dbQuery {
            ImportWord.deleteWhere { ImportWord.id.eq(id) }
        }
    }
}