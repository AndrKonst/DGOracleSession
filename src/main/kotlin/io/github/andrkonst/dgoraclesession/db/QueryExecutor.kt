package io.github.andrkonst.dgoraclesession.db

import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.remote.jdbc.RemotePreparedStatement
import com.intellij.database.remote.jdbc.RemoteResultSet
import io.github.andrkonst.dgoraclesession.model.QueryColumn
import io.github.andrkonst.dgoraclesession.model.QueryResult
import java.math.BigDecimal
import java.sql.Timestamp
import java.sql.Types

object QueryExecutor {
    const val DEFAULT_MAX_ROWS = 10_000
    const val DEFAULT_TIMEOUT_SEC = 60

    fun query(
        connection: DatabaseConnection,
        sql: String,
        binds: List<Any?> = emptyList(),
        maxRows: Int = DEFAULT_MAX_ROWS,
        timeoutSec: Int = DEFAULT_TIMEOUT_SEC,
    ): QueryResult {
        val started = System.nanoTime()
        val statement = connection.remoteConnection.prepareStatement(normalize(sql))
        try {
            statement.setMaxRows(maxRows + 1)
            statement.setQueryTimeout(timeoutSec)
            statement.setFetchSize(500)
            bind(statement, binds)
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
                return QueryResult(columns, rows, truncated, (System.nanoTime() - started) / 1_000_000)
            }
            finally {
                rs.close()
            }
        }
        finally {
            statement.close()
        }
    }

    fun execute(connection: DatabaseConnection, sql: String, timeoutSec: Int = DEFAULT_TIMEOUT_SEC) {
        val statement = connection.remoteConnection.prepareStatement(normalize(sql))
        try {
            statement.setQueryTimeout(timeoutSec)
            statement.execute()
        }
        finally {
            statement.close()
        }
    }

    private fun normalize(sql: String): String = sql.trim().removeSuffix(";").trimEnd()

    private fun bind(statement: RemotePreparedStatement, binds: List<Any?>) {
        binds.forEachIndexed { i, value ->
            val index = i + 1
            when (value) {
                null -> statement.setNull(index, Types.VARCHAR)
                is BigDecimal -> statement.setBigDecimal(index, value)
                is Timestamp -> statement.setTimestamp(index, value)
                is Number -> statement.setBigDecimal(index, BigDecimal(value.toString()))
                else -> statement.setString(index, value.toString())
            }
        }
    }

    private fun read(rs: RemoteResultSet, index: Int, sqlType: Int): Any? {
        val value: Any? = when (sqlType) {
            Types.NUMERIC, Types.DECIMAL, Types.INTEGER, Types.SMALLINT, Types.TINYINT, Types.BIGINT,
            Types.FLOAT, Types.DOUBLE, Types.REAL -> rs.getBigDecimal(index)
            Types.DATE, Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> rs.getTimestamp(index)
            else -> rs.getString(index)
        }
        return if (rs.wasNull()) null else value
    }
}
