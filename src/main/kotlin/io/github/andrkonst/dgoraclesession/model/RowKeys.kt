package io.github.andrkonst.dgoraclesession.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Date

/**
 * Finds a row of a re-executed query by the values of key columns taken from the grid.
 * Grid and JDBC return numbers and dates as different classes, so values are compared in a canonical form.
 */
object RowKeys {
    sealed interface Match {
        data class Found(val row: Int) : Match
        data class MissingColumns(val columns: List<String>) : Match
        data object NotFound : Match
        data object Ambiguous : Match
    }

    fun find(result: QueryResult, keyValues: Map<String, Any?>): Match {
        val names = result.columns.map { it.name.lowercase() }
        val missing = keyValues.keys.filter { it !in names }
        if (missing.isNotEmpty()) return Match.MissingColumns(missing)
        val expected = keyValues.map { (name, value) -> names.indexOf(name) to canonical(value) }
        val rows = result.rows.indices.filter { i -> expected.all { (col, value) -> canonical(result.rows[i][col]) == value } }
        return when (rows.size) {
            0 -> Match.NotFound
            1 -> Match.Found(rows.single())
            else -> Match.Ambiguous
        }
    }

    fun canonical(value: Any?): String? = when (value) {
        null -> null
        is Number -> number(value)
        is java.sql.Timestamp -> value.toLocalDateTime().toString()
        is java.sql.Date -> value.toLocalDate().atStartOfDay().toString()
        is Date -> LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault()).toString()
        is LocalDateTime -> value.toString()
        is LocalDate -> value.atStartOfDay().toString()
        is OffsetDateTime -> value.toLocalDateTime().toString()
        else -> value.toString()
    }

    private fun number(value: Number): String = try {
        BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
    }
    catch (_: NumberFormatException) {
        value.toString()
    }
}
