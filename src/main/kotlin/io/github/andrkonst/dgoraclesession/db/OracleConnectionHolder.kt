package io.github.andrkonst.dgoraclesession.db

import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.dataSource.DatabaseConnectionManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.connection.ConnectionRequestor
import com.intellij.database.util.GuardedRef
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.rmi.RemoteException
import java.sql.SQLException

/**
 * Not thread-safe: callers must serialize access.
 */
class OracleConnectionHolder(private val project: Project, val dataSource: LocalDataSource) : AutoCloseable {
    private var ref: GuardedRef<DatabaseConnection>? = null

    var ownSid: String? = null
        private set

    fun <T> use(block: (DatabaseConnection) -> T): T {
        val connection = connection()
        return try {
            block(connection)
        }
        catch (e: Exception) {
            if (!isConnectionLost(e)) throw e
            thisLogger().info("Connection to ${dataSource.name} lost, reconnecting", e)
            close()
            block(connection())
        }
    }

    private fun connection(): DatabaseConnection {
        ref?.let { return it.get() }
        val newRef = DatabaseConnectionManager.getInstance()
            .build(project, dataSource)
            .setRequestor(ConnectionRequestor.Anonymous())
            .setAskPassword(true)
            .createBlocking()
            ?: throw SQLException("Cannot connect to ${dataSource.name}")
        ref = newRef
        ownSid = runCatching {
            QueryExecutor.query(newRef.get(), "select sys_context('USERENV', 'SID') from dual").rows.firstOrNull()?.firstOrNull()?.toString()
        }.getOrNull()
        return newRef.get()
    }

    override fun close() {
        val current = ref ?: return
        ref = null
        ownSid = null
        runCatching { current.close() }.onFailure { thisLogger().warn("Failed to close connection to ${dataSource.name}", it) }
    }

    private fun isConnectionLost(e: Throwable): Boolean =
        generateSequence(e) { it.cause }.any {
            it is RemoteException || (it is SQLException && it.errorCode in CONNECTION_LOST_CODES)
        }

    private companion object {
        // ORA-00028, ORA-01012, ORA-02396, ORA-03113, ORA-03114, ORA-03135, JDBC 17002/17008/17410
        val CONNECTION_LOST_CODES = setOf(28, 1012, 2396, 3113, 3114, 3135, 17002, 17008, 17410)
    }
}
