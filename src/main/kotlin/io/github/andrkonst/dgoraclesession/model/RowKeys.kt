package io.github.andrkonst.dgoraclesession.model

import io.github.andrkonst.dgoraclesession.db.QueryExecutor

/**
 * Re-reads one row of a query by the values of key columns: the query is wrapped with a key filter,
 * so the database computes the other columns (e.g. heavy reports) only for the matching row.
 */
object RowKeys {
    sealed interface Match {
        data class Found(val row: Int) : Match
        data object NotFound : Match
        data object Ambiguous : Match
    }

    data class Filter(val sql: String, val binds: List<Any?>)

    /** [keys] are pairs of a column name exactly as the query returns it and the value to match. */
    fun filter(sql: String, keys: List<Pair<String, Any?>>): Filter {
        val conditions = keys.joinToString(" and ") { (column, value) ->
            quote(column) + if (value == null) " is null" else " = ?"
        }
        // The line break keeps the closing parenthesis out of a trailing -- comment
        val wrapped = "select * from (\n${QueryExecutor.normalize(sql)}\n) where $conditions"
        return Filter(wrapped, keys.mapNotNull { it.second })
    }

    /** At most two rows are needed to tell a unique row from an ambiguous key. */
    const val MAX_ROWS = 2

    fun match(result: QueryResult): Match = when (result.rows.size) {
        0 -> Match.NotFound
        1 -> Match.Found(0)
        else -> Match.Ambiguous
    }

    private fun quote(column: String) = "\"" + column.replace("\"", "\"\"") + "\""
}
