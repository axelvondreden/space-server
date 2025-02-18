package de.axl.db

import at.favre.lib.crypto.bcrypt.BCrypt
import de.axl.dbQuery
import de.axl.serialization.api.ExposedUser
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class UserDbService(database: Database, private val adminUsername: String) {
    object Users : Table() {
        val id = integer("id").autoIncrement()
        val username = varchar("username", length = 50).uniqueIndex()
        val name = varchar("name", length = 50).nullable()
        val password = varchar("password", length = 256)
        val createdAt = datetime("createdAt")
        val updatedAt = datetime("updatedAt").nullable()

        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            SchemaUtils.create(Users)
        }
    }

    suspend fun create(user: ExposedUser, passwordPlain: String): Int = dbQuery {
        Users.insert {
            it[username] = user.username
            it[name] = user.name
            it[password] = BCrypt.withDefaults().hashToString(12, passwordPlain.toCharArray())
            it[createdAt] = LocalDateTime.now()
        }[Users.id]
    }

    suspend fun changePassword(user: ExposedUser, oldPasswordPlain: String, newPasswordPlain: String): Boolean {
        if (!testPassword(user, oldPasswordPlain)) return false
        dbQuery {
            Users.update({ Users.username eq user.username }) {
                it[password] = BCrypt.withDefaults().hashToString(12, newPasswordPlain.toCharArray())
                it[updatedAt] = LocalDateTime.now()
            }
        }
        return true
    }

    suspend fun findAll(): List<ExposedUser> {
        return dbQuery {
            Users.selectAll().map { ExposedUser(it[Users.username], it[Users.name], it[Users.username] == adminUsername, it[Users.createdAt], it[Users.updatedAt]) }
        }
    }

    suspend fun findByUsername(username: String): ExposedUser? {
        return dbQuery {
            Users.selectAll()
                .where { Users.username eq username }
                .map { ExposedUser(it[Users.username], it[Users.name], it[Users.username] == adminUsername, it[Users.createdAt], it[Users.updatedAt]) }
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
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }

    suspend fun delete(username: String) {
        dbQuery {
            Users.deleteWhere { Users.username.eq(username) }
        }
    }
}