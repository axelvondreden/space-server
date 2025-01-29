package de.axl.serialization.api

data class ExposedImportBlockFull(
    val id: Int = 0,
    val text: String = "",
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val lines: List<ExposedImportLineFull> = emptyList()
)