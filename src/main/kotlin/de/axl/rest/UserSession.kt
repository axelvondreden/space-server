package de.axl.rest

import kotlinx.serialization.Serializable
import java.security.Principal

@Serializable
data class UserSession(val username: String) : Principal {
    override fun getName() = username
}