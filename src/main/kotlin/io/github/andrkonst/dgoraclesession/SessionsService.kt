package io.github.andrkonst.dgoraclesession

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import io.github.andrkonst.dgoraclesession.db.OracleConnectionHolder
import io.github.andrkonst.dgoraclesession.db.QueryExecutor
import io.github.andrkonst.dgoraclesession.db.StatementCanceller
import io.github.andrkonst.dgoraclesession.model.QueryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Service connections of the plugin. Queries and Kill / Disconnect use separate connections,
 * so terminating a session does not wait for a slow detail query.
 */
@Service(Service.Level.PROJECT)
class SessionsService(private val project: Project, val scope: CoroutineScope) : Disposable {
    private val queries = ConnectionSlot()
    private val admin = ConnectionSlot()

    @Volatile
    private var disposed = false

    @Volatile
    var dataSource: LocalDataSource? = null
        private set

    fun oracleDataSources(): List<LocalDataSource> =
        LocalDataSourceManager.getInstance(project).dataSources.filter { it.dbms.isOracle }

    fun restoreDataSource(): LocalDataSource? {
        val id = PropertiesComponent.getInstance(project).getValue(SELECTED_DATA_SOURCE_KEY) ?: return null
        return oracleDataSources().firstOrNull { it.uniqueId == id }
    }

    fun selectDataSource(ds: LocalDataSource?) {
        if (ds?.uniqueId == dataSource?.uniqueId) return
        dataSource = ds
        PropertiesComponent.getInstance(project).setValue(SELECTED_DATA_SOURCE_KEY, ds?.uniqueId)
        closeConnections()
    }

    private fun closeConnections() {
        listOf(queries, admin).forEach { slot ->
            scope.launch(Dispatchers.IO) { slot.mutex.withLock { slot.close() } }
        }
    }

    suspend fun query(sql: String, binds: List<Any?> = emptyList(), maxRows: Int = QueryExecutor.DEFAULT_MAX_ROWS): QueryResult =
        queries.run { holder, canceller ->
            holder.use { QueryExecutor.query(it, sql, binds, maxRows, canceller = canceller) }
        }

    suspend fun execute(sql: String): Unit = admin.run { holder, canceller ->
        holder.use { QueryExecutor.execute(it, sql, canceller = canceller) }
    }

    /** Instance number of the connection used by [execute]. */
    suspend fun adminInstance(): String? = admin.run { holder, canceller ->
        holder.use { QueryExecutor.query(it, INSTANCE_SQL, canceller = canceller) }.rows.firstOrNull()?.firstOrNull()?.toString()
    }

    override fun dispose() {
        disposed = true
        listOf(queries, admin).forEach { slot ->
            // A running query closes its connection itself when it finishes
            if (slot.mutex.tryLock()) {
                try {
                    slot.close()
                }
                finally {
                    slot.mutex.unlock()
                }
            }
        }
    }

    private inner class ConnectionSlot {
        val mutex = Mutex()
        private var holder: OracleConnectionHolder? = null

        fun close() {
            holder?.close()
            holder = null
        }

        suspend fun <T> run(block: (OracleConnectionHolder, StatementCanceller) -> T): T = withContext(Dispatchers.IO) {
            val ds = dataSource ?: throw IllegalStateException(DGOracleSessionBundle.message("error.no.data.source"))
            mutex.withLock {
                val current = holder?.takeIf { it.dataSource.uniqueId == ds.uniqueId }
                    ?: OracleConnectionHolder(project, ds).also {
                        holder?.close()
                        holder = it
                    }
                val canceller = StatementCanceller()
                try {
                    coroutineScope {
                        val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
                            try {
                                awaitCancellation()
                            }
                            finally {
                                canceller.cancel()
                            }
                        }
                        try {
                            block(current, canceller)
                        }
                        finally {
                            watcher.cancel()
                        }
                    }
                }
                finally {
                    if (disposed) close()
                }
            }
        }
    }

    companion object {
        private const val SELECTED_DATA_SOURCE_KEY = "io.github.andrkonst.dgoraclesession.selectedDataSource"
        private const val INSTANCE_SQL = "select sys_context('USERENV', 'INSTANCE') from dual"

        fun getInstance(project: Project): SessionsService = project.service()
    }
}
