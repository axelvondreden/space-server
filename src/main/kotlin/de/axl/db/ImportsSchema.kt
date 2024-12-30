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
data class ExposedImport(val guid: String, val file: String, val type: ImportType, val createdAt: String, val updatedAt: String?)

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
            it[type] = import.type
            it[createdAt] = now()
        }[Imports.guid]
    }

    suspend fun findAll(): List<ExposedImport> {
        return dbQuery {
            Imports.selectAll().map { ExposedImport(it[Imports.guid], it[Imports.file], it[Imports.type], it[Imports.createdAt].toDatetimeString(), it[Imports.updatedAt]?.toDatetimeString()) }
        }
    }

    suspend fun findByGuid(guid: String): ExposedImport? {
        return dbQuery {
            Imports.selectAll()
                .where { Imports.guid eq guid }
                .map { ExposedImport(it[Imports.guid], it[Imports.file], it[Imports.type], it[Imports.createdAt].toDatetimeString(), it[Imports.updatedAt]?.toDatetimeString()) }
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