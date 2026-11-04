package io.github.andrkonst.dgoraclesession.toolwindow.detail

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.remote.jdbc.LobInfo
import com.intellij.database.run.ReservedCellValue
import com.intellij.database.run.ui.DataAccessType
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.progress.withBackgroundProgress
import io.github.andrkonst.dgoraclesession.DGOracleSessionBundle
import io.github.andrkonst.dgoraclesession.DGOracleSessionNotifications
import io.github.andrkonst.dgoraclesession.SessionsService
import io.github.andrkonst.dgoraclesession.grid.GridRows
import io.github.andrkonst.dgoraclesession.grid.HtmlColumns
import io.github.andrkonst.dgoraclesession.grid.QueryGrid
import io.github.andrkonst.dgoraclesession.html.HtmlReportOpener
import io.github.andrkonst.dgoraclesession.html.HtmlTarget
import io.github.andrkonst.dgoraclesession.model.DetailDef
import io.github.andrkonst.dgoraclesession.model.RowKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Detail shown in a DataGrip grid. Bind variables are inlined as literals because the grid
 * hook-up executes plain SQL text.
 */
class DetailGridPanel(project: Project, detail: DetailDef) : DetailPanel(project, detail) {
    private var queryGrid: QueryGrid? = null
    private var dataSourceId: String? = null

    override fun load(ds: LocalDataSource, values: Map<String, Any?>, reloadOnly: Boolean) {
        val sql = parsed.inline(values)
        val current = queryGrid
        if (current != null && current.sql == sql && dataSourceId == ds.uniqueId) {
            current.reload()
            return
        }
        disposeGrid()
        val htmlColumns = detail.htmlColumnNames()
        val grid = QueryGrid(project, ds, sql, this) { dataGrid ->
            HtmlColumns.install(dataGrid, htmlColumns) { row, column, target -> openHtml(sql, row, column, target) }
        }
        queryGrid = grid
        dataSourceId = ds.uniqueId
        setBody(grid.component)
    }

    private fun openHtml(sql: String, row: ModelIndex<GridRow>, column: ModelIndex<GridColumn>, target: HtmlTarget) {
        val grid = queryGrid?.grid ?: return
        val model = grid.getDataModel(DataAccessType.DATABASE_DATA)
        val columnName = model.getColumn(column)?.name ?: return
        val rowValues = GridRows.rowValues(grid, row)
        val keyNames = detail.keyColumnNames()
        val title = (listOf(detail.caption) + keyNames.mapNotNull { rowValues[it]?.toString() }).joinToString(" ")
        val html = when (val value = model.getValueAt(row, column)) {
            null, is ReservedCellValue -> return
            is LobInfo.ClobInfo -> value.data.takeUnless { value.isTruncated }
            else -> value.toString()
        }
        val service = SessionsService.getInstance(project)
        if (html != null) {
            service.scope.launch { HtmlReportOpener.open(project, title, html, target) }
            return
        }
        if (keyNames.isEmpty()) {
            notify(DGOracleSessionBundle.message("html.truncated.no.key", columnName), null, NotificationType.WARNING)
            return
        }
        // Key columns are quoted with the names exactly as the query returns them
        val columnNames = model.columns.associateBy({ it.name.lowercase() }, { it.name })
        val missing = keyNames.filter { it !in columnNames }
        if (missing.isNotEmpty()) {
            notify(DGOracleSessionBundle.message("html.reload.failed"), DGOracleSessionBundle.message("html.reload.missing.columns", missing.joinToString()), NotificationType.ERROR)
            return
        }
        val keys = keyNames.map { columnNames.getValue(it) to rowValues[it] }
        service.scope.launch {
            try {
                val full = withBackgroundProgress(project, DGOracleSessionBundle.message("html.reload.progress", columnName)) {
                    fetchFull(service, sql, columnName, keys)
                }
                if (full != null) HtmlReportOpener.open(project, title, full, target)
            }
            catch (e: CancellationException) {
                throw e
            }
            catch (e: Throwable) {
                withContext(Dispatchers.EDT) {
                    notify(DGOracleSessionBundle.message("html.reload.failed"), e.message ?: e.javaClass.simpleName, NotificationType.ERROR)
                }
            }
        }
    }

    /** Re-executes the detail query for the row with [keys] and returns the full value of [columnName]. */
    private suspend fun fetchFull(service: SessionsService, sql: String, columnName: String, keys: List<Pair<String, Any?>>): String? {
        val filter = RowKeys.filter(sql, keys)
        val result = service.query(filter.sql, filter.binds, RowKeys.MAX_ROWS)
        val keyText = keys.joinToString { it.first.lowercase() }
        val error = when (val match = RowKeys.match(result)) {
            is RowKeys.Match.Found -> {
                val index = result.columns.indexOfFirst { it.name.equals(columnName, ignoreCase = true) }
                if (index >= 0) return result.rows[match.row][index]?.toString().orEmpty()
                DGOracleSessionBundle.message("html.reload.missing.columns", columnName)
            }
            RowKeys.Match.NotFound -> DGOracleSessionBundle.message("html.reload.not.found", keyText)
            RowKeys.Match.Ambiguous -> DGOracleSessionBundle.message("html.reload.ambiguous", keyText)
        }
        withContext(Dispatchers.EDT) { notify(DGOracleSessionBundle.message("html.reload.failed"), error, NotificationType.ERROR) }
        return null
    }

    private fun notify(title: String, content: String?, type: NotificationType) =
        DGOracleSessionNotifications.notify(project, title, content, type)

    override fun showMessage(text: String) {
        disposeGrid()
        super.showMessage(text)
    }

    private fun disposeGrid() {
        queryGrid?.let { Disposer.dispose(it) }
        queryGrid = null
        dataSourceId = null
    }

    override fun dispose() {
        queryGrid = null
    }
}
