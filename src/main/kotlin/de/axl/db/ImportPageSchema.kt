package de.axl.db

import de.axl.db.ImportDocumentDbService.ImportDocument
import de.axl.dbQuery
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class ExposedImportPage(
    val id: Int,
    val text: String,
    val page: Int,
    val width: Int,
    val height: Int,
    val deskew: Int = 40,
    val colorFuzz: Int = 10,
    val cropFuzz: Int = 20,
    val documentGuid: String = ""
)

class ImportPageDbService(database: Database) {
    object ImportPage : Table() {
        val id = integer("id").autoIncrement()
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
            (ImportPage innerJoin ImportDocument).selectAll()
                .where { ImportPage.id eq id }
                .map {
                    ExposedImportPage(
                        it[ImportPage.id],
                        it[ImportPage.text],
                        it[ImportPage.page],
                        it[ImportPage.width],
                        it[ImportPage.height],
                        it[ImportPage.deskew],
                        it[ImportPage.colorFuzz],
                        it[ImportPage.cropFuzz],
                        it[ImportDocument.guid]
                    )
                }
                .singleOrNull()
        }
    }

    suspend fun create(import: ExposedImportPage, documentId: Int): Int = dbQuery {
        ImportPage.insert {
            it[text] = import.text
            it[page] = import.page
            it[width] = import.width
            it[height] = import.height
            it[deskew] = import.deskew
            it[colorFuzz] = import.colorFuzz
            it[cropFuzz] = import.cropFuzz
            it[document] = documentId
        }[ImportPage.id]
    }

    suspend fun update(import: ExposedImportPage) {
        dbQuery {
            ImportPage.update({ ImportPage.id eq import.id }) {
                it[text] = import.text
                it[page] = import.page
                it[width] = import.width
                it[height] = import.height
                it[deskew] = import.deskew
                it[colorFuzz] = import.colorFuzz
                it[cropFuzz] = import.cropFuzz
            }
        }
    }

    suspend fun delete(id: Int) {
        dbQuery {
            ImportPage.deleteWhere { ImportPage.id.eq(id) }
        }
    }
}