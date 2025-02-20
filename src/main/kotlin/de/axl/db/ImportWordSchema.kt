package de.axl.db

import de.axl.db.ImportBlockDbService.ImportBlock
import de.axl.db.ImportLineDbService.ImportLine
import de.axl.dbQuery
import de.axl.serialization.api.ExposedImportSpellingSuggestion
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

    object ImportSpellingSuggestion : Table() {
        val id = integer("id").autoIncrement()
        val suggestion = text("suggestion")
        val word = reference("word", ImportWord.id, onDelete = ReferenceOption.CASCADE)

        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            SchemaUtils.create(ImportWord)
            SchemaUtils.create(ImportSpellingSuggestion)
        }
    }

    suspend fun findById(id: Int): ExposedImportWord? = dbQuery {
        ImportWord.selectAll().where { ImportWord.id eq id }.mapExposed().singleOrNull()
    }

    suspend fun create(word: ExposedImportWord, lineId: Int): Int = dbQuery {
        val id = ImportWord.insert {
            it[text] = word.text
            it[x] = word.x
            it[y] = word.y
            it[width] = word.width
            it[height] = word.height
            it[ocrConfidence] = word.ocrConfidence
            it[line] = lineId
        }[ImportWord.id]
        word.spellingSuggestions.forEach { spelling ->
            ImportSpellingSuggestion.insert {
                it[suggestion] = spelling.suggestion
                it[ImportSpellingSuggestion.word] = id
            }
        }
        id
    }

    suspend fun updateText(id: Int, newText: String) = dbQuery {
        ImportWord.update({ ImportWord.id eq id }) {
            it[text] = newText
            it[ocrConfidence] = 1.0
        }
    }

    suspend fun delete(id: Int) {
        dbQuery {
            val dbWord = ImportWord.selectAll().where { ImportWord.id eq id }.single()
            ImportWord.deleteWhere { ImportWord.id eq id }

            if (ImportWord.selectAll().where { ImportWord.line eq dbWord[ImportWord.line] }.count() == 0L) {
                val dbLine = ImportLine.selectAll().where { ImportLine.id eq dbWord[ImportWord.line] }.single()
                ImportLine.deleteWhere { ImportLine.id eq dbLine[ImportLine.id] }

                if (ImportLine.selectAll().where { ImportLine.block eq dbLine[ImportLine.block] }.count() == 0L) {
                    ImportBlock.deleteWhere { ImportBlock.id eq dbLine[ImportLine.block] }
                }
            }
        }
    }

    private fun Query.mapExposed(): List<ExposedImportWord> = map { word ->
        ExposedImportWord(
            word[ImportWord.id],
            word[ImportWord.text],
            word[ImportWord.x],
            word[ImportWord.y],
            word[ImportWord.width],
            word[ImportWord.height],
            word[ImportWord.ocrConfidence],
            ImportSpellingSuggestion.selectAll().where { ImportSpellingSuggestion.word eq word[ImportWord.id] }.mapSpellingExposed(),
            word[ImportWord.line]
        )
    }

    private fun Query.mapSpellingExposed(): List<ExposedImportSpellingSuggestion> = map {
        ExposedImportSpellingSuggestion(it[ImportSpellingSuggestion.id], it[ImportSpellingSuggestion.suggestion])
    }
}