package de.axl.db

import de.axl.dbQuery
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.LocalDateTime

@Serializable
data class ExposedImportDocument(
    val id: Int = 0,
    val guid: String,
    val ocrLanguage: OCRLanguage = OCRLanguage.DEU,
    @Contextual val date: LocalDate? = null,
    @Contextual val createdAt: LocalDateTime = LocalDateTime.now(),
    @Contextual val updatedAt: LocalDateTime? = null,
    val pages: List<ExposedImportDocumentPage> = emptyList()
)

@Serializable
data class ExposedImportDocumentPage(val page: Int, val id: Int)

enum class OCRLanguage(val lang: String) {
    DEU("deu"),
    ENG("eng")
}

class ImportDocumentDbService(database: Database) {
    object ImportDocument : Table() {
        val id = integer("id").autoIncrement()
        val guid = varchar("guid", length = 36)
        val ocrLanguage = enumerationByName("ocrLanguage", 10, OCRLanguage::class).default(OCRLanguage.DEU)
        val date = date("date").nullable()
        val createdAt = datetime("createdAt")
        val updatedAt = datetime("updatedAt").nullable()

        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            SchemaUtils.create(ImportDocument)
        }
    }

    suspend fun create(import: ExposedImportDocument): Int = dbQuery {
        ImportDocument.insert {
            it[guid] = import.guid
            it[ocrLanguage] = import.ocrLanguage
            it[date] = import.date
            it[createdAt] = LocalDateTime.now()
        }[ImportDocument.id]
    }

    suspend fun findAll(): List<ExposedImportDocument> {
        return dbQuery {
            ImportDocument.selectAll().mapExposed()
        }
    }

    suspend fun findById(id: Int): ExposedImportDocument? {
        return dbQuery {
            ImportDocument.selectAll().where { ImportDocument.id eq id }.mapExposed().singleOrNull()
        }
    }

    suspend fun update(import: ExposedImportDocument) {
        dbQuery {
            ImportDocument.update({ ImportDocument.id eq import.id }) {
                it[ocrLanguage] = import.ocrLanguage
                it[date] = import.date
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }

    suspend fun delete(id: Int) {
        dbQuery {
            ImportDocument.deleteWhere { ImportDocument.id.eq(id) }
        }
    }

    private suspend fun Query.mapExposed(): List<ExposedImportDocument> = map {
        ExposedImportDocument(
            it[ImportDocument.id],
            it[ImportDocument.guid],
            it[ImportDocument.ocrLanguage],
            it[ImportDocument.date],
            it[ImportDocument.createdAt],
            it[ImportDocument.updatedAt],
            dbQuery {
                ImportPageDbService.ImportPage.selectAll()
                    .where { ImportPageDbService.ImportPage.document eq it[ImportDocument.id] }
                    .map { ExposedImportDocumentPage(it[ImportPageDbService.ImportPage.page], it[ImportPageDbService.ImportPage.id]) }
            }
        )
    }
}