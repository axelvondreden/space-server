package de.axl.db

import de.axl.dbQuery
import de.axl.now
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.*

@Serializable
data class ExposedImport(val guid: String, val file: String, val type: String, val createdAt: Long, val updatedAt: Long?)

enum class ImportType(val type: String) {
    PDF("pdf"),
    IMG("img")
}

class ImportService(database: Database) {
    object Imports : Table() {
        val id = integer("id").autoIncrement()
        val guid = varchar("guid", length = 36).uniqueIndex()
        val file = varchar("file", length = 200)
        val type = enumerationByName("type", 10, ImportType::class)
        val createdAt = long("createdAt")
        val updatedAt = long("updatedAt").nullable()

        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            SchemaUtils.create(Imports)
        }
    }

    suspend fun create(import: ExposedImport): String = dbQuery {
        Imports.insert {
            it[guid] = UUID.randomUUID().toString()
            it[file] = import.file
            it[type] = ImportType.valueOf(import.type)
            it[createdAt] = now()
        }[Imports.guid]
    }

    suspend fun findAll(): List<ExposedImport> {
        return dbQuery {
            Imports.selectAll().map { ExposedImport(it[Imports.guid], it[Imports.file], it[Imports.type].type, it[Imports.createdAt], it[Imports.updatedAt]) }
        }
    }

    suspend fun findByGuid(guid: String): ExposedImport? {
        return dbQuery {
            Imports.selectAll()
                .where { Imports.guid eq guid }
                .map { ExposedImport(it[Imports.guid], it[Imports.file], it[Imports.type].type, it[Imports.createdAt], it[Imports.updatedAt]) }
                .singleOrNull()
        }
    }

    suspend fun update(import: ExposedImport) {
        dbQuery {
            Imports.update({ Imports.guid eq import.guid }) {
                it[updatedAt] = now()
            }
        }
    }

    suspend fun delete(guid: String) {
        dbQuery {
            Imports.deleteWhere { Imports.guid.eq(guid) }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.declaringClass.name)
    }
}