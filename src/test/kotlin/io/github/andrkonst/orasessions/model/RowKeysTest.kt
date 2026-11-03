package io.github.andrkonst.orasessions.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.sql.Timestamp
import java.sql.Types
import java.time.LocalDateTime

class RowKeysTest {
    private val start = LocalDateTime.of(2026, 11, 3, 10, 15, 30)

    private val result = QueryResult(
        columns = listOf(
            QueryColumn("SQL_ID", Types.VARCHAR),
            QueryColumn("SQL_EXEC_ID", Types.NUMERIC),
            QueryColumn("SQL_EXEC_START", Types.DATE),
            QueryColumn("REPORT", Types.CLOB),
        ),
        rows = listOf(
            listOf("a1", BigDecimal("16777216"), Timestamp.valueOf(start), "<html>1</html>"),
            listOf("a1", BigDecimal("16777217"), Timestamp.valueOf(start), "<html>2</html>"),
            listOf("b2", BigDecimal("16777216"), null, "<html>3</html>"),
        ),
        truncated = false,
        elapsedMs = 0,
    )

    @Test
    fun `row is found regardless of order and value classes`() {
        val match = RowKeys.find(result, mapOf("sql_id" to "a1", "sql_exec_id" to 16777217L))
        assertEquals(RowKeys.Match.Found(1), match)
    }

    @Test
    fun `dates from grid match timestamps from jdbc`() {
        val match = RowKeys.find(result, mapOf("sql_exec_start" to start, "sql_exec_id" to 16777216.0, "sql_id" to "a1"))
        assertEquals(RowKeys.Match.Found(0), match)
    }

    @Test
    fun `null key matches null`() {
        assertEquals(RowKeys.Match.Found(2), RowKeys.find(result, mapOf("sql_exec_start" to null)))
    }

    @Test
    fun `not unique key is ambiguous`() {
        assertEquals(RowKeys.Match.Ambiguous, RowKeys.find(result, mapOf("sql_id" to "a1")))
    }

    @Test
    fun `missing row is not found`() {
        assertEquals(RowKeys.Match.NotFound, RowKeys.find(result, mapOf("sql_id" to "zz")))
    }

    @Test
    fun `unknown key column is reported`() {
        assertEquals(RowKeys.Match.MissingColumns(listOf("key")), RowKeys.find(result, mapOf("sql_id" to "a1", "key" to 1)))
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
