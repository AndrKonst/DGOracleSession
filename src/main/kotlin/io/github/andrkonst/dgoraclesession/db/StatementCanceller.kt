package io.github.andrkonst.dgoraclesession.db

import com.intellij.database.remote.jdbc.RemoteStatement
import com.intellij.openapi.diagnostic.thisLogger
import java.sql.SQLException

/** Cancels the statement being executed on behalf of a cancelled coroutine: blocking JDBC calls ignore interruption. */
class StatementCanceller {
    @Volatile
    private var statement: RemoteStatement? = null

    @Volatile
    private var cancelled = false

    fun register(value: RemoteStatement) {
        statement = value
        if (cancelled) throw SQLException("Cancelled")
    }

    fun unregister() {
        statement = null
    }

    fun cancel() {
        cancelled = true
        val current = statement ?: return
        runCatching { current.cancel() }.onFailure { thisLogger().info("Failed to cancel statement", it) }
    }
}
