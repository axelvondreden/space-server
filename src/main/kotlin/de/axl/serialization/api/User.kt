package de.axl.serialization.api

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

@Serializable
data class ExposedUser(
    val username: String,
    val name: String?,
    val admin: Boolean = false,
    @Contextual val createdAt: LocalDateTime = LocalDateTime.now(),
    @Contextual val updatedAt: LocalDateTime? = null
)