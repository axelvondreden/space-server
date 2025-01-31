package de.axl.db

import de.axl.db.ImportBlockDbService.ImportBlock
import de.axl.db.ImportLineDbService.ImportLine
import de.axl.db.ImportPageDbService.ImportPage
import de.axl.dbQuery
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class ExposedImportWord(
    val id: Int = 0,
    val text: String,
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val ocrConfidence: Double? = 0.0,
    val lineId: Int = 0
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
        val dbWord = ImportWord.selectAll().where { ImportWord.id eq id }.single()
        val oldText = dbWord[ImportWord.text]
        ImportWord.update({ ImportWord.id eq id }) {
            it[text] = newText
        }

        val dbLine = ImportLine.selectAll().where { ImportLine.id eq dbWord[ImportWord.line] }.single()
        val oldLineText = dbLine[ImportLine.text]
        val newLineText = oldLineText.replace(oldText, newText)
        ImportLine.update({ ImportLine.id eq dbWord[ImportWord.line] }) {
            it[text] = newLineText
        }

        val dbBlock = ImportBlock.selectAll().where { ImportBlock.id eq dbLine[ImportLine.block] }.single()
        val oldBlockText = dbBlock[ImportBlock.text]
        val newBlockText = oldBlockText.replace(oldLineText, newLineText)
        ImportBlock.update({ ImportBlock.id eq dbLine[ImportLine.block] }) {
            it[text] = newBlockText
        }

        val dbPage = ImportPage.selectAll().where { ImportPage.id eq dbBlock[ImportBlock.page] }.single()
        val oldPageText = dbPage[ImportPage.text]
        val newPageText = oldPageText.replace(oldBlockText, newBlockText)
        ImportPage.update({ ImportPage.id eq dbBlock[ImportBlock.page] }) {
            it[text] = newPageText
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