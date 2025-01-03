package de.axl.web.events

import kotlinx.serialization.Serializable

@Serializable
data class ImportStateEvent(
    val importing: Boolean,
    val guid: String? = null,
    val progress: Double? = null,
    val message: String? = null
)
