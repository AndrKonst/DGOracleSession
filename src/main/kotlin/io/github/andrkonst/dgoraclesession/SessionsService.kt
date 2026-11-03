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
import io.github.andrkonst.dgoraclesession.model.QueryResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Service(Service.Level.PROJECT)
class SessionsService(private val project: Project, val scope: CoroutineScope) : Disposable {
    private val mutex = Mutex()
    private var holder: OracleConnectionHolder? = null

    @Volatile
    var dataSource: LocalDataSource? = null
        private set

    @Volatile
    var ownSid: String? = null
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
        closeConnection()
    }

    fun closeConnection() {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                holder?.close()
                holder = null
                ownSid = null
            }
        }
    }

    suspend fun query(sql: String, binds: List<Any?> = emptyList()): QueryResult = withConnection { holder ->
        holder.use { QueryExecutor.query(it, sql, binds) }
    }

    suspend fun execute(sql: String): Unit = withConnection { holder ->
        holder.use { QueryExecutor.execute(it, sql) }
    }

    private suspend fun <T> withConnection(block: (OracleConnectionHolder) -> T): T = withContext(Dispatchers.IO) {
        val ds = dataSource ?: throw IllegalStateException(DGOracleSessionBundle.message("error.no.data.source"))
        mutex.withLock {
            val current = holder?.takeIf { it.dataSource.uniqueId == ds.uniqueId }
                ?: OracleConnectionHolder(project, ds).also {
                    holder?.close()
                    holder = it
                }
            try {
                block(current)
            }
            finally {
                ownSid = current.ownSid
            }
        }
    }

    override fun dispose() {
        holder?.close()
        holder = null
    }

    companion object {
        private const val SELECTED_DATA_SOURCE_KEY = "io.github.andrkonst.dgoraclesession.selectedDataSource"

        fun getInstance(project: Project): SessionsService = project.service()
    }
}
