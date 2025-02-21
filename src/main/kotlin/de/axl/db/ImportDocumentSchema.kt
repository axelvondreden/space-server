package de.axl.db

import de.axl.dbQuery
import de.axl.serialization.api.ExposedImportDocument
import de.axl.serialization.api.ExposedImportDocumentPage
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class ImportDocumentDbService(database: Database) {
    object ImportDocument : Table() {
        val id = integer("id").autoIncrement()
        val guid = varchar("guid", length = 36)
        val language = enumerationByName("language", 10, OCRLanguage::class).default(OCRLanguage.DEU)
        val date = date("date").nullable()
        val isInvoice = bool("isInvoice").default(false)
        val createdAt = datetime("createdAt")
        val updatedAt = datetime("updatedAt").nullable()

        override val primaryKey = PrimaryKey(id)
    }

    enum class OCRLanguage { DEU, ENG }

    init {
        transaction(database) {
            SchemaUtils.create(ImportDocument)
        }
    }

    suspend fun create(import: ExposedImportDocument): Int = dbQuery {
        ImportDocument.insert {
            it[guid] = import.guid
            it[language] = import.language
            it[date] = import.date
            it[isInvoice] = import.isInvoice
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
                it[language] = import.language
                it[date] = import.date
                it[isInvoice] = import.isInvoice
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
            it[ImportDocument.language],
            it[ImportDocument.date],
            it[ImportDocument.isInvoice],
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