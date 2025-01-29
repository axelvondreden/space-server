package de.axl.importing.events

import kotlinx.serialization.Serializable

@Serializable
data class ImportStateEvent(
    val importing: Boolean,
    val currentFile: Int? = null,
    val fileCount: Int? = null,
    val guid: String? = null,
    val progress: Double? = null,
    val message: String? = null,
    val completedDocId: Int? = null
)
