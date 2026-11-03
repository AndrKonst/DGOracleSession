package io.github.andrkonst.dgoraclesession.model

import java.math.BigDecimal
import java.math.BigInteger

data class SessionRef(val sid: BigInteger, val serial: BigInteger, val username: String?) {
    val id: String get() = "$sid,$serial"

    companion object {
        /** Builds a reference from row values; null if the row has no integer SID and SERIAL#. */
        fun fromRow(values: Map<String, Any?>): SessionRef? {
            val sid = toInteger(values["sid"]) ?: return null
            val serial = toInteger(values["serial#"]) ?: return null
            return SessionRef(sid, serial, values["username"]?.toString())
        }

        private fun toInteger(value: Any?): BigInteger? = try {
            when (value) {
                null -> null
                is BigInteger -> value
                is BigDecimal -> value.toBigIntegerExact()
                is Number -> BigDecimal(value.toString()).toBigIntegerExact()
                else -> BigDecimal(value.toString().trim()).toBigIntegerExact()
            }?.takeIf { it.signum() >= 0 }
        }
        catch (_: ArithmeticException) {
            null
        }
        catch (_: NumberFormatException) {
            null
        }
    }
}

enum class TerminateMode(private val template: String) {
    KILL_IMMEDIATE("alter system kill session '%s' immediate"),
    DISCONNECT_IMMEDIATE("alter system disconnect session '%s' immediate");

    fun sql(session: SessionRef): String = template.format(session.id)
}
