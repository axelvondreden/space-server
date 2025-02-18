package de.axl.db

import de.axl.db.ImportBlockDbService.ImportBlock
import de.axl.dbQuery
import de.axl.serialization.api.ExposedImportLine
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class ImportLineDbService(database: Database) {
    object ImportLine : Table() {
        val id = integer("id").autoIncrement()
        val x = integer("x")
        val y = integer("y")
        val width = integer("width")
        val height = integer("height")
        val block = reference("block", ImportBlock.id, onDelete = ReferenceOption.CASCADE)

        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            SchemaUtils.create(ImportLine)
        }
    }

    suspend fun findById(id: Int): ExposedImportLine? = dbQuery {
        ImportLine.selectAll().where { ImportLine.id eq id }.mapExposed().singleOrNull()
    }

    suspend fun findByBlockId(id: Int): List<ExposedImportLine> = dbQuery {
        ImportLine.selectAll().where { ImportLine.block eq id }.orderBy(ImportLine.y).mapExposed()
    }

    suspend fun create(line: ExposedImportLine, blockId: Int): Int = dbQuery {
        ImportLine.insert {
            it[x] = line.x
            it[y] = line.y
            it[width] = line.width
            it[height] = line.height
            it[block] = blockId
        }[ImportLine.id]
    }

    suspend fun delete(id: Int) {
        dbQuery {
            ImportLine.deleteWhere { ImportLine.id.eq(id) }
        }
    }

    private suspend fun Query.mapExposed(): List<ExposedImportLine> = map {
        ExposedImportLine(
            it[ImportLine.id],
            it[ImportLine.x],
            it[ImportLine.y],
            it[ImportLine.width],
            it[ImportLine.height],
            it[ImportLine.block],
            dbQuery {
                ImportWordDbService.ImportWord.selectAll()
                    .where { ImportWordDbService.ImportWord.line eq it[ImportLine.id] }
                    .map { word -> word[ImportWordDbService.ImportWord.id] }
            }
        )
    }
}