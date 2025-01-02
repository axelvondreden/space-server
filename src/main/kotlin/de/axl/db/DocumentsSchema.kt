package de.axl.db

import de.axl.dbQuery
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

@Serializable
data class ExposedDocument(
    val id: Int,
    val guid: String,
    @Contextual val createdAt: LocalDateTime,
    @Contextual val updatedAt: LocalDateTime?
)

class DocumentService(database: Database) {
    object Documents : Table() {
        val id = integer("id").autoIncrement()
        val guid = varchar("guid", length = 36).uniqueIndex()
        val createdAt = datetime("createdAt")
        val updatedAt = datetime("updatedAt").nullable()

        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            SchemaUtils.create(Documents)
        }
    }

    suspend fun createEmpty(): String = dbQuery {
        Documents.insert {
            it[guid] = UUID.randomUUID().toString()
            it[createdAt] = LocalDateTime.now()
        }[Documents.guid]
    }

    suspend fun findAll(): List<ExposedDocument> {
        return dbQuery {
            Documents.selectAll().map { ExposedDocument(it[Documents.id], it[Documents.guid], it[Documents.createdAt], it[Documents.updatedAt]) }
        }
    }

    suspend fun findByGuid(guid: String): ExposedDocument? {
        return dbQuery {
            Documents.selectAll()
                .where { Documents.guid eq guid }
                .map {
                    ExposedDocument(
                        it[Documents.id],
                        it[Documents.guid],
                        it[Documents.createdAt],
                        it[Documents.updatedAt]
                    )
                }
                .singleOrNull()
        }
    }

    suspend fun update(document: ExposedDocument) {
        dbQuery {
            Documents.update({ Documents.guid eq document.guid }) {
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }

    suspend fun delete(guid: String) {
        dbQuery {
            Documents.deleteWhere { Documents.guid.eq(guid) }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.declaringClass.name)
    }
}