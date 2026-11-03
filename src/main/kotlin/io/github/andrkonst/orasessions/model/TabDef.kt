package io.github.andrkonst.orasessions.model

import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection

enum class DetailMode { TABLE, TEXT }

@Tag("detail")
data class DetailDef(
    @Attribute("caption") var caption: String = "",
    @Attribute("mode") var mode: DetailMode = DetailMode.TABLE,
    @Tag("sql") var sql: String = "",
    @Attribute("htmlColumns") var htmlColumns: String = "",
    @Attribute("keyColumns") var keyColumns: String = "",
) {
    /** Lower-cased names of columns whose values are HTML documents (Table mode only). */
    fun htmlColumnNames(): Set<String> = if (mode == DetailMode.TABLE) parseColumns(htmlColumns).toSet() else emptySet()

    /** Lower-cased names of columns identifying a row when a truncated value is re-read. */
    fun keyColumnNames(): List<String> = parseColumns(keyColumns)

    companion object {
        fun parseColumns(text: String): List<String> =
            text.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
    }
}

@Tag("tab")
data class TabDef(
    @Attribute("caption") var caption: String = "",
    @Tag("sql") var sql: String = "",
    @XCollection(propertyElementName = "details") var details: MutableList<DetailDef> = mutableListOf(),
) {
    fun deepCopy(): TabDef = TabDef(caption, sql, details.mapTo(mutableListOf()) { it.copy() })
}
