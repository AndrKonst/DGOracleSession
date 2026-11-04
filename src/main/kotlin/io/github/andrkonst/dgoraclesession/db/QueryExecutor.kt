package io.github.andrkonst.dgoraclesession.db

import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.remote.jdbc.RemotePreparedStatement
import com.intellij.database.remote.jdbc.RemoteResultSet
import io.github.andrkonst.dgoraclesession.model.QueryColumn
import io.github.andrkonst.dgoraclesession.model.QueryResult
import java.math.BigDecimal
import java.sql.Timestamp
import java.sql.Types
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.util.Date

object QueryExecutor {
    const val DEFAULT_MAX_ROWS = 10_000
    const val DEFAULT_TIMEOUT_SEC = 60

    fun query(
        connection: DatabaseConnection,
        sql: String,
        binds: List<Any?> = emptyList(),
        maxRows: Int = DEFAULT_MAX_ROWS,
        timeoutSec: Int = DEFAULT_TIMEOUT_SEC,
        canceller: StatementCanceller? = null,
    ): QueryResult {
        val statement = connection.remoteConnection.prepareStatement(normalize(sql))
        try {
            statement.setMaxRows(maxRows + 1)
            statement.setQueryTimeout(timeoutSec)
            statement.setFetchSize(500)
            bind(statement, binds)
            canceller?.register(statement)
            val rs = statement.executeQuery()
            try {
                val meta = rs.metaData
                val columns = (1..meta.columnCount).map { QueryColumn(meta.getColumnLabel(it), meta.getColumnType(it)) }
                val rows = ArrayList<List<Any?>>()
                var truncated = false
                while (rs.next()) {
                    if (rows.size == maxRows) {
                        truncated = true
                        break
                    }
                    rows += columns.mapIndexed { i, c -> read(rs, i + 1, c.sqlType) }
                }
                return QueryResult(columns, rows, truncated)
            }
            finally {
                rs.close()
            }
        }
        finally {
            canceller?.unregister()
            statement.close()
        }
    }

    fun execute(connection: DatabaseConnection, sql: String, timeoutSec: Int = DEFAULT_TIMEOUT_SEC, canceller: StatementCanceller? = null) {
        val statement = connection.remoteConnection.prepareStatement(normalize(sql))
        try {
            statement.setQueryTimeout(timeoutSec)
            canceller?.register(statement)
            statement.execute()
        }
        finally {
            canceller?.unregister()
            statement.close()
        }
    }

    /** SQL text without surrounding whitespace and the trailing semicolon, which JDBC does not accept. */
    fun normalize(sql: String): String = sql.trim().removeSuffix(";").trimEnd()

    private fun bind(statement: RemotePreparedStatement, binds: List<Any?>) {
        binds.forEachIndexed { i, value ->
            val index = i + 1
            when (value) {
                null -> statement.setNull(index, Types.VARCHAR)
                is BigDecimal -> statement.setBigDecimal(index, value)
                is Boolean -> statement.setBigDecimal(index, if (value) BigDecimal.ONE else BigDecimal.ZERO)
                is Number -> statement.setBigDecimal(index, BigDecimal(value.toString()))
                is Timestamp -> statement.setTimestamp(index, value)
                is java.sql.Date -> statement.setTimestamp(index, Timestamp.valueOf(value.toLocalDate().atStartOfDay()))
                is Date -> statement.setTimestamp(index, Timestamp(value.time))
                is LocalDateTime -> statement.setTimestamp(index, Timestamp.valueOf(value))
                is LocalDate -> statement.setTimestamp(index, Timestamp.valueOf(value.atStartOfDay()))
                is OffsetDateTime -> statement.setObject(index, value, Types.TIMESTAMP_WITH_TIMEZONE)
                is ZonedDateTime -> statement.setObject(index, value.toOffsetDateTime(), Types.TIMESTAMP_WITH_TIMEZONE)
                else -> statement.setString(index, value.toString())
            }
        }
    }

    private fun read(rs: RemoteResultSet, index: Int, sqlType: Int): Any? {
        val value: Any? = when (sqlType) {
            Types.NUMERIC, Types.DECIMAL, Types.INTEGER, Types.SMALLINT, Types.TINYINT, Types.BIGINT,
            Types.FLOAT, Types.DOUBLE, Types.REAL -> rs.getBigDecimal(index)
            Types.DATE, Types.TIMESTAMP -> rs.getTimestamp(index)
            // getTimestamp drops the time zone; the driver's text keeps it
            else -> rs.getString(index)
        }
        return if (rs.wasNull()) null else value
    }
}
