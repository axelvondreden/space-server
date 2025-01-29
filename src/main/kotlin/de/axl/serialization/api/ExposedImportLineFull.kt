package de.axl.serialization.api

import de.axl.db.ExposedImportWord

data class ExposedImportLineFull(
    val id: Int = 0,
    val text: String = "",
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val words: List<ExposedImportWord> = emptyList()
)