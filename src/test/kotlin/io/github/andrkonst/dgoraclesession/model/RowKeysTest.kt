package io.github.andrkonst.dgoraclesession.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.sql.Types

class RowKeysTest {
    @Test
    fun `query is wrapped with quoted key columns and binds`() {
        val filter = RowKeys.filter(
            "select sql_id, sql_exec_id, report from v\$sql_monitor;  ",
            listOf("SQL_ID" to "a1", "SQL_EXEC_ID" to BigDecimal("16777216")),
        )
        assertEquals(
            "select * from (\nselect sql_id, sql_exec_id, report from v\$sql_monitor\n) where \"SQL_ID\" = ? and \"SQL_EXEC_ID\" = ?",
            filter.sql,
        )
        assertEquals(listOf("a1", BigDecimal("16777216")), filter.binds)
    }

    @Test
    fun `null key is matched with is null and not bound`() {
        val filter = RowKeys.filter("select a, b from t", listOf("A" to null, "b" to 1))
        assertEquals("select * from (\nselect a, b from t\n) where \"A\" is null and \"b\" = ?", filter.sql)
        assertEquals(listOf(1), filter.binds)
    }

    @Test
    fun `trailing line comment does not swallow the closing parenthesis`() {
        val filter = RowKeys.filter("select a from t -- comment", listOf("A" to 1))
        assertEquals("select * from (\nselect a from t -- comment\n) where \"A\" = ?", filter.sql)
    }

    @Test
    fun `double quotes in column names are escaped`() {
        assertEquals("select * from (\nq\n) where \"a\"\"b\" = ?", RowKeys.filter("q", listOf("a\"b" to 1)).sql)
    }

    @Test
    fun `match depends on the number of rows`() {
        val columns = listOf(QueryColumn("A", Types.NUMERIC))
        assertEquals(RowKeys.Match.NotFound, RowKeys.match(QueryResult(columns, emptyList(), false)))
        assertEquals(RowKeys.Match.Found(0), RowKeys.match(QueryResult(columns, listOf(listOf(1)), false)))
        assertEquals(RowKeys.Match.Ambiguous, RowKeys.match(QueryResult(columns, listOf(listOf(1), listOf(1)), false)))
    }

    @Test
    fun `column lists are trimmed, lower-cased and deduplicated`() {
        assertEquals(listOf("sql_id", "sql_exec_id"), DetailDef.parseColumns(" SQL_ID, sql_exec_id ,,sql_id"))
    }

    @Test
    fun `html columns are ignored in text mode`() {
        assertEquals(setOf("report"), DetailDef(mode = DetailMode.TABLE, htmlColumns = "Report").htmlColumnNames())
        assertEquals(emptySet<String>(), DetailDef(mode = DetailMode.TEXT, htmlColumns = "report").htmlColumnNames())
    }
}
