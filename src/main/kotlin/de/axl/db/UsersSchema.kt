package de.axl.db

import at.favre.lib.crypto.bcrypt.BCrypt
import de.axl.dbQuery
import de.axl.now
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

@Serializable
data class ExposedUser(val username: String, val name: String?, val createdAt: Long, val updatedAt: Long?)

class UserService(database: Database) {
    object Users : Table() {
        val id = integer("id").autoIncrement()
        val username = varchar("username", length = 50)
        val name = varchar("name", length = 50).nullable()
        val password = varchar("password", length = 256)
        val createdAt = long("createdAt")
        val updatedAt = long("updatedAt").nullable()

        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            SchemaUtils.create(Users)
        }

        runBlocking {
            newSuspendedTransaction(Dispatchers.IO) {
                addAdminUser()
            }
        }
    }

    private suspend fun addAdminUser() {
        if (findByUsername("admin") == null) {
            logger.info("Creating admin user")
            create(ExposedUser("admin", "Admin", now(), null), "admin")
        }
    }

    suspend fun create(user: ExposedUser, passwordPlain: String): Int = dbQuery {
        Users.insert {
            it[username] = user.username
            it[name] = user.name
            it[password] = BCrypt.withDefaults().hashToString(12, passwordPlain.toCharArray())
            it[createdAt] = now()
        }[Users.id]
    }

    suspend fun findAll(): List<ExposedUser> {
        return dbQuery {
            Users.selectAll().map { ExposedUser(it[Users.username], it[Users.name], it[Users.createdAt], it[Users.updatedAt]) }
        }
    }

    suspend fun findByUsername(username: String): ExposedUser? {
        return dbQuery {
            Users.selectAll()
                .where { Users.username eq username }
                .map { ExposedUser(it[Users.username], it[Users.name], it[Users.createdAt], it[Users.updatedAt]) }
                .singleOrNull()
        }
    }

    suspend fun testPassword(user: ExposedUser, passwordPlain: String): Boolean {
        val dbHash = dbQuery {
            Users.select(Users.password).where { Users.username eq user.username }.single()[Users.password]
        }
        val result = BCrypt.verifyer().verify(passwordPlain.toCharArray(), dbHash)
        return result.verified
    }

    suspend fun update(user: ExposedUser) {
        dbQuery {
            Users.update({ Users.username eq user.username }) {
                it[name] = user.name
                it[updatedAt] = now()
            }
        }
    }

    suspend fun delete(username: String) {
        dbQuery {
            Users.deleteWhere { Users.username.eq(username) }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.declaringClass.name)
    }
}