package de.axl.db

import de.axl.dbQuery
import de.axl.serialization.api.ExposedImportInvoice
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.transactions.transaction

class ImportInvoiceDbService(database: Database) {
    object ImportInvoice : Table() {
        val id = integer("id").autoIncrement()
        val recipient = varchar("recipient", length = 50).nullable()
        val invoiceNumber = varchar("invoiceNumber", length = 50).nullable()
        val date = date("date").nullable()
        val amount = double("amount").nullable()

        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            SchemaUtils.create(ImportInvoice)
        }
    }

    suspend fun create(invoice: ExposedImportInvoice): Int = dbQuery {
        ImportInvoice.insert {
            it[recipient] = invoice.recipient
            it[invoiceNumber] = invoice.invoiceNumber
            it[date] = invoice.date
            it[amount] = invoice.amount
        }[ImportInvoice.id]
    }

    suspend fun findById(id: Int): ExposedImportInvoice? {
        return dbQuery {
            ImportInvoice.selectAll().where { ImportInvoice.id eq id }.mapExposed().singleOrNull()
        }
    }

    suspend fun update(invoice: ExposedImportInvoice) {
        dbQuery {
            ImportInvoice.update({ ImportInvoice.id eq invoice.id }) {
                it[recipient] = invoice.recipient
                it[invoiceNumber] = invoice.invoiceNumber
                it[date] = invoice.date
                it[amount] = invoice.amount
            }
        }
    }

    suspend fun delete(id: Int) {
        dbQuery {
            ImportInvoice.deleteWhere { ImportInvoice.id eq id }
        }
    }

    private fun Query.mapExposed(): List<ExposedImportInvoice> = map {
        ExposedImportInvoice(
            it[ImportInvoice.id],
            it[ImportInvoice.recipient],
            it[ImportInvoice.invoiceNumber],
            it[ImportInvoice.date],
            it[ImportInvoice.amount]
        )
    }
}