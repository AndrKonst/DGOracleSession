package io.github.andrkonst.dgoraclesession.settings

import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xmlb.XmlSerializer
import io.github.andrkonst.dgoraclesession.model.DefaultConfig
import io.github.andrkonst.dgoraclesession.model.DetailDef
import io.github.andrkonst.dgoraclesession.model.DetailMode
import io.github.andrkonst.dgoraclesession.model.TabDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryTabsSettingsSerializationTest {
    @Test
    fun `custom tabs survive round trip`() {
        val state = QueryTabsSettings.SettingsState().apply {
            tabs = mutableListOf(
                TabDef(
                    caption = "Sessions",
                    sql = "select sid, serial#\n  from v\$session\n where username = 'A&B' and x < 1",
                    details = mutableListOf(
                        DetailDef("SQL Text", DetailMode.TEXT, "select sql_fulltext from v\$sql where sql_id = :sql_id"),
                        DetailDef("Locks", DetailMode.TABLE, "select * from v\$lock where sid = :sid"),
                        DetailDef(
                            "SQL Monitor", DetailMode.TABLE, "select sql_id, report from t",
                            htmlColumns = "report", keyColumns = "sql_id, sql_exec_id",
                        ),
                    ),
                ),
                TabDef(caption = "Empty", sql = ""),
            )
        }

        val element = XmlSerializer.serialize(state)
        println(JDOMUtil.write(element))
        val restored = XmlSerializer.deserialize(element, QueryTabsSettings.SettingsState::class.java)

        assertEquals(state.tabs, restored.tabs)
    }

    @Test
    fun `default state is not written`() {
        val element = XmlSerializer.serialize(QueryTabsSettings.SettingsState(), com.intellij.util.xmlb.SkipDefaultsSerializationFilter())
        assertTrue(JDOMUtil.isEmpty(element))
    }

    @Test
    fun `empty tab list survives round trip`() {
        val state = QueryTabsSettings.SettingsState().apply { tabs = mutableListOf() }
        val element = XmlSerializer.serialize(state, com.intellij.util.xmlb.SkipDefaultsSerializationFilter())
        val restored = XmlSerializer.deserialize(element, QueryTabsSettings.SettingsState::class.java)
        assertEquals(emptyList<TabDef>(), restored.tabs)
    }

    @Test
    fun `defaults contain sessions tab`() {
        assertEquals(listOf("Sessions"), DefaultConfig.tabs().map { it.caption })
    }
}
