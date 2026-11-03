package io.github.andrkonst.orasessions.db

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.LocalDateTime

class BindParserTest {
    @Test
    fun `finds binds with hash and dollar in names`() {
        val p = BindParser.parse("select * from v\$session where sid = :sid and serial# = :SERIAL# and x = :a\$b")
        assertEquals(listOf("sid", "serial#", "a\$b"), p.names)
        assertEquals("select * from v\$session where sid = ? and serial# = ? and x = ?", p.toPositional())
    }

    @Test
    fun `skips string literals and quoted identifiers`() {
        val p = BindParser.parse("select ':no', 'it''s :no', \":no\" from dual where a = :yes")
        assertEquals(listOf("yes"), p.names)
    }

    @Test
    fun `skips q-quoted strings`() {
        val p = BindParser.parse("select q'[it's :no]', Q'{:no}', q'!:no!' from dual where a = :yes")
        assertEquals(listOf("yes"), p.names)
    }

    @Test
    fun `identifier ending with q is not q-quote`() {
        val p = BindParser.parse("select seq'x' from dual where a = :yes")
        assertEquals(listOf("yes"), p.names)
    }

    @Test
    fun `skips comments and concatenate hint`() {
        val p = BindParser.parse("select a -- :no\n from t /* :no */ where b = :yes /* concatenate */")
        assertEquals(listOf("yes"), p.names)
    }

    @Test
    fun `ignores assignment and time literals`() {
        val p = BindParser.parse("begin x := :val; y := to_date('10:30', 'hh24:mi'); end;")
        assertEquals(listOf("val"), p.names)
    }

    @Test
    fun `ignores colon followed by digit`() {
        val p = BindParser.parse("select :1, :a1 from dual")
        assertEquals(listOf("a1"), p.names)
    }

    @Test
    fun `repeated bind appears every time`() {
        val p = BindParser.parse(":sid + :sid")
        assertEquals(listOf("sid", "sid"), p.names)
    }

    @Test
    fun `unterminated literal does not hang`() {
        assertEquals(emptyList<String>(), BindParser.parse("select 'abc :x").names)
        assertEquals(emptyList<String>(), BindParser.parse("select /* :x").names)
        assertEquals(emptyList<String>(), BindParser.parse("select q'[ :x").names)
    }

    @Test
    fun `inlines literals`() {
        val p = BindParser.parse("where sid = :sid and serial# = :serial# and u = :username and t = :t and n = :n")
        val sql = p.inline(
            mapOf(
                "sid" to BigDecimal("123"),
                "serial#" to 4567L,
                "username" to "O'Brien",
                "t" to Timestamp.valueOf(LocalDateTime.of(2026, 11, 3, 17, 5, 9)),
                "n" to null,
            )
        )
        assertEquals(
            "where sid = 123 and serial# = 4567 and u = 'O''Brien' and t = timestamp '2026-11-03 17:05:09.000000000' and n = null",
            sql,
        )
    }

    @Test
    fun `big decimal is not in scientific notation`() {
        assertEquals("10000000000", BindParser.toLiteral(BigDecimal("1E+10")))
    }
}
