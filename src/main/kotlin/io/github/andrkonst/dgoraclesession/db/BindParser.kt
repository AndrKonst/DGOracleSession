package io.github.andrkonst.dgoraclesession.db

import java.math.BigDecimal
import java.math.BigInteger
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date

/**
 * Finds `:name` bind variables in Oracle SQL, skipping string literals (including q-quoted),
 * quoted identifiers, comments and the PL/SQL `:=` operator.
 */
class BindParser private constructor(val sql: String, private val binds: List<Bind>) {
    private data class Bind(val name: String, val start: Int, val end: Int)

    /** Bind names in order of appearance, lower-cased. */
    val names: List<String> get() = binds.map { it.name }

    /** SQL with `?` placeholders; bind values must be passed in [names] order. */
    fun toPositional(): String = replace { "?" }

    /** SQL with bind variables replaced by Oracle literals. */
    fun inline(values: Map<String, Any?>): String = replace { name -> toLiteral(values[name]) }

    private fun replace(replacement: (String) -> String): String {
        if (binds.isEmpty()) return sql
        val sb = StringBuilder(sql.length + 16)
        var last = 0
        for (bind in binds) {
            sb.append(sql, last, bind.start).append(replacement(bind.name))
            last = bind.end
        }
        return sb.append(sql, last, sql.length).toString()
    }

    companion object {
        private val TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSS")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val TIMESTAMP_TZ_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSS xxx")

        fun parse(sql: String): BindParser {
            val binds = mutableListOf<Bind>()
            var i = 0
            val n = sql.length
            while (i < n) {
                val c = sql[i]
                when {
                    c == '-' && i + 1 < n && sql[i + 1] == '-' -> {
                        i = sql.indexOf('\n', i).let { if (it < 0) n else it + 1 }
                    }
                    c == '/' && i + 1 < n && sql[i + 1] == '*' -> {
                        i = sql.indexOf("*/", i + 2).let { if (it < 0) n else it + 2 }
                    }
                    (c == 'q' || c == 'Q') && i + 2 < n && sql[i + 1] == '\'' && !isIdentifierPart(sql.getOrNull(i - 1)) -> {
                        val open = sql[i + 2]
                        val close = when (open) {
                            '[' -> ']'
                            '(' -> ')'
                            '{' -> '}'
                            '<' -> '>'
                            else -> open
                        }
                        i = sql.indexOf("$close'", i + 3).let { if (it < 0) n else it + 2 }
                    }
                    c == '\'' -> i = skipQuoted(sql, i, '\'')
                    c == '"' -> i = skipQuoted(sql, i, '"')
                    c == ':' && i + 1 < n && isIdentifierStart(sql[i + 1]) && sql.getOrNull(i - 1) != ':' -> {
                        var end = i + 2
                        while (end < n && isIdentifierPart(sql[end])) end++
                        binds += Bind(sql.substring(i + 1, end).lowercase(), i, end)
                        i = end
                    }
                    else -> i++
                }
            }
            return BindParser(sql, binds)
        }

        /** Oracle SQL literal for a value taken from a result grid. */
        fun toLiteral(value: Any?): String = when (value) {
            null -> "null"
            is BigDecimal -> value.toPlainString()
            is Int, is Long, is Short, is Byte, is BigInteger -> value.toString()
            is Double -> if (value.isFinite()) BigDecimal.valueOf(value).toPlainString() else quote(value.toString())
            is Float -> if (value.isFinite()) BigDecimal(value.toString()).toPlainString() else quote(value.toString())
            is java.sql.Date -> "date '${value.toLocalDate().format(DATE_FORMAT)}'"
            is Timestamp -> timestamp(value.toLocalDateTime())
            is Date -> timestamp(LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault()))
            is LocalDateTime -> timestamp(value)
            is OffsetDateTime -> timestampTz(value)
            is ZonedDateTime -> timestampTz(value.toOffsetDateTime())
            is LocalDate -> "date '${value.format(DATE_FORMAT)}'"
            is Boolean -> if (value) "1" else "0"
            else -> quote(value.toString())
        }

        private fun timestamp(value: LocalDateTime) = "timestamp '${value.format(TIMESTAMP_FORMAT)}'"

        private fun timestampTz(value: OffsetDateTime) = "timestamp '${value.format(TIMESTAMP_TZ_FORMAT)}'"

        private fun quote(text: String) = "'" + text.replace("'", "''") + "'"

        private fun skipQuoted(sql: String, start: Int, quote: Char): Int {
            var i = start + 1
            while (i < sql.length) {
                if (sql[i] == quote) {
                    if (i + 1 < sql.length && sql[i + 1] == quote) i += 2 else return i + 1
                }
                else {
                    i++
                }
            }
            return sql.length
        }

        private fun isIdentifierStart(c: Char) = c.isLetter() || c == '_'

        private fun isIdentifierPart(c: Char?) = c != null && (c.isLetterOrDigit() || c == '_' || c == '$' || c == '#')
    }
}
