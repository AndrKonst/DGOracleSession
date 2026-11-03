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
)

@Tag("tab")
data class TabDef(
    @Attribute("caption") var caption: String = "",
    @Tag("sql") var sql: String = "",
    @XCollection(propertyElementName = "details") var details: MutableList<DetailDef> = mutableListOf(),
) {
    fun deepCopy(): TabDef = TabDef(caption, sql, details.mapTo(mutableListOf()) { it.copy() })
}
