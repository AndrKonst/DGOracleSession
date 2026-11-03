package io.github.andrkonst.dgoraclesession.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class SessionRefTest {
    @Test
    fun `builds reference from numeric values`() {
        val ref = SessionRef.fromRow(mapOf("sid" to BigDecimal("123"), "serial#" to 4567L, "username" to "HR"))!!
        assertEquals("123,4567", ref.id)
        assertEquals("HR", ref.username)
        assertEquals("alter system kill session '123,4567' immediate", TerminateMode.KILL_IMMEDIATE.sql(ref))
        assertEquals("alter system disconnect session '123,4567' immediate", TerminateMode.DISCONNECT_IMMEDIATE.sql(ref))
    }

    @Test
    fun `accepts numeric strings`() {
        assertEquals("1,2", SessionRef.fromRow(mapOf("sid" to " 1 ", "serial#" to "2"))!!.id)
    }

    @Test
    fun `rejects missing, fractional, negative and non numeric values`() {
        assertNull(SessionRef.fromRow(mapOf("sid" to 1)))
        assertNull(SessionRef.fromRow(mapOf("sid" to null, "serial#" to 2)))
        assertNull(SessionRef.fromRow(mapOf("sid" to BigDecimal("1.5"), "serial#" to 2)))
        assertNull(SessionRef.fromRow(mapOf("sid" to -1, "serial#" to 2)))
        assertNull(SessionRef.fromRow(mapOf("sid" to "1' immediate --", "serial#" to 2)))
    }
}
