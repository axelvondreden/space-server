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
data class ExposedImport(
    val guid: String,
    val ocrLanguage: OCRLanguage = OCRLanguage.DEU,
    val pages: Int = 1,
    val text: String? = null,
    @Contextual val date: LocalDate? = null,
    @Contextual val createdAt: LocalDateTime = LocalDateTime.now(),
    @Contextual val updatedAt: LocalDateTime? = null
)

enum class OCRLanguage(val lang: String) {
    DEU("deu"),
    ENG("eng")
}

class ImportDbService(database: Database) {
    object Imports : Table() {
        val id = integer("id").autoIncrement()
        val guid = varchar("guid", length = 36).uniqueIndex()
        val ocrLanguage = enumerationByName("ocrLanguage", 10, OCRLanguage::class).default(OCRLanguage.DEU)
        val pages = integer("pages")
        val text = text("text").nullable()
        val date = date("date").nullable()
        val createdAt = datetime("createdAt")
        val updatedAt = datetime("updatedAt").nullable()

        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            SchemaUtils.create(Imports)
        }
    }

    suspend fun create(import: ExposedImport): String = dbQuery {
        Imports.insert {
            it[guid] = import.guid
            it[ocrLanguage] = import.ocrLanguage
            it[pages] = import.pages
            it[text] = import.text
            it[date] = import.date
            it[createdAt] = LocalDateTime.now()
        }[Imports.guid]
    }

    suspend fun findAll(): List<ExposedImport> {
        return dbQuery {
            Imports.selectAll().map {
                ExposedImport(
                    it[Imports.guid],
                    it[Imports.ocrLanguage],
                    it[Imports.pages],
                    it[Imports.text],
                    it[Imports.date],
                    it[Imports.createdAt],
                    it[Imports.updatedAt]
                )
            }
        }
    }

    suspend fun findByGuid(guid: String): ExposedImport? {
        return dbQuery {
            Imports.selectAll()
                .where { Imports.guid eq guid }
                .map {
                    ExposedImport(
                        it[Imports.guid],
                        it[Imports.ocrLanguage],
                        it[Imports.pages],
                        it[Imports.text],
                        it[Imports.date],
                        it[Imports.createdAt],
                        it[Imports.updatedAt]
                    )
                }
                .singleOrNull()
        }
    }

    suspend fun update(import: ExposedImport) {
        dbQuery {
            Imports.update({ Imports.guid eq import.guid }) {
                it[date] = import.date
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }

    suspend fun delete(guid: String) {
        dbQuery {
            Imports.deleteWhere { Imports.guid.eq(guid) }
        }
    }
}