package io.github.andrkonst.dgoraclesession.model

import java.math.BigDecimal
import java.math.BigInteger

/** [instId] is set when the row has an INST_ID column (GV$ views on RAC). */
data class SessionRef(val sid: BigInteger, val serial: BigInteger, val instId: BigInteger?, val username: String?) {
    val id: String get() = "$sid,$serial" + (instId?.let { ",@$it" } ?: "")

    companion object {
        /** Builds a reference from row values; null if the row has no integer SID and SERIAL# or a non-integer INST_ID. */
        fun fromRow(values: Map<String, Any?>): SessionRef? {
            val sid = toInteger(values["sid"]) ?: return null
            val serial = toInteger(values["serial#"]) ?: return null
            val instId = if (values["inst_id"] == null) null else toInteger(values["inst_id"]) ?: return null
            return SessionRef(sid, serial, instId, values["username"]?.toString())
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

enum class TerminateMode {
    /** KILL SESSION accepts the instance as `@inst_id`, so sessions of other RAC instances can be killed. */
    KILL_IMMEDIATE {
        override fun sql(session: SessionRef) = "alter system kill session '${session.id}' immediate"
    },

    /** DISCONNECT SESSION has no instance parameter: the session must be on the instance of the connection. */
    DISCONNECT_IMMEDIATE {
        override fun sql(session: SessionRef) = "alter system disconnect session '${session.sid},${session.serial}' immediate"
    };

    abstract fun sql(session: SessionRef): String
}
