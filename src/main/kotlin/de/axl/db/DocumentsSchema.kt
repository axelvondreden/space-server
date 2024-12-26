package de.axl.db

import de.axl.dbQuery
import de.axl.now
import de.axl.toDatetimeString
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.*

@Serializable
data class ExposedDocument(val id: Int, val guid: String, val createdAt: String, val updatedAt: String?)

class DocumentService(database: Database) {
    object Documents : Table() {
        val id = integer("id").autoIncrement()
        val guid = varchar("guid", length = 36).uniqueIndex()
        val createdAt = long("createdAt")
        val updatedAt = long("updatedAt").nullable()

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
            it[createdAt] = now()
        }[Documents.guid]
    }

    suspend fun findAll(): List<ExposedDocument> {
        return dbQuery {
            Documents.selectAll().map { ExposedDocument(it[Documents.id], it[Documents.guid], it[Documents.createdAt].toDatetimeString(), it[Documents.updatedAt]?.toDatetimeString()) }
        }
    }

    suspend fun findByGuid(guid: String): ExposedDocument? {
        return dbQuery {
            Documents.selectAll()
                .where { Documents.guid eq guid }
                .map { ExposedDocument(it[Documents.id], it[Documents.guid], it[Documents.createdAt].toDatetimeString(), it[Documents.updatedAt]?.toDatetimeString()) }
                .singleOrNull()
        }
    }

    suspend fun update(document: ExposedDocument) {
        dbQuery {
            Documents.update({ Documents.guid eq document.guid }) {
                it[updatedAt] = now()
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