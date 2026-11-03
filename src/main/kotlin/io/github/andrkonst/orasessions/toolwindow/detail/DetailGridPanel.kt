package io.github.andrkonst.orasessions.toolwindow.detail

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.remote.jdbc.LobInfo
import com.intellij.database.run.ReservedCellValue
import com.intellij.database.run.ui.DataAccessType
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.progress.withBackgroundProgress
import io.github.andrkonst.orasessions.OraSessionsBundle
import io.github.andrkonst.orasessions.SessionsService
import io.github.andrkonst.orasessions.actions.TerminateSessionAction
import io.github.andrkonst.orasessions.grid.GridRows
import io.github.andrkonst.orasessions.grid.HtmlColumns
import io.github.andrkonst.orasessions.grid.QueryGrid
import io.github.andrkonst.orasessions.html.HtmlReportOpener
import io.github.andrkonst.orasessions.html.HtmlTarget
import io.github.andrkonst.orasessions.model.DetailDef
import io.github.andrkonst.orasessions.model.RowKeys
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
            notify(OraSessionsBundle.message("html.truncated.no.key", columnName), null, NotificationType.WARNING)
            return
        }
        val keyValues = keyNames.associateWith { rowValues[it] }
        service.scope.launch {
            try {
                val full = withBackgroundProgress(project, OraSessionsBundle.message("html.reload.progress", columnName)) {
                    fetchFull(service, sql, columnName, keyValues)
                }
                if (full != null) HtmlReportOpener.open(project, title, full, target)
            }
            catch (e: CancellationException) {
                throw e
            }
            catch (e: Throwable) {
                withContext(Dispatchers.EDT) {
                    notify(OraSessionsBundle.message("html.reload.failed"), e.message ?: e.javaClass.simpleName, NotificationType.ERROR)
                }
            }
        }
    }

    /** Re-executes the detail query and returns the full value of [columnName] in the row with [keyValues]. */
    private suspend fun fetchFull(service: SessionsService, sql: String, columnName: String, keyValues: Map<String, Any?>): String? {
        val result = service.query(sql)
        val keys = keyValues.keys.joinToString()
        val error = when (val match = RowKeys.find(result, keyValues)) {
            is RowKeys.Match.Found -> {
                val index = result.columns.indexOfFirst { it.name.equals(columnName, ignoreCase = true) }
                if (index >= 0) return result.rows[match.row][index]?.toString().orEmpty()
                OraSessionsBundle.message("html.reload.missing.columns", columnName)
            }
            is RowKeys.Match.MissingColumns -> OraSessionsBundle.message("html.reload.missing.columns", match.columns.joinToString())
            RowKeys.Match.NotFound -> OraSessionsBundle.message("html.reload.not.found", keys)
            RowKeys.Match.Ambiguous -> OraSessionsBundle.message("html.reload.ambiguous", keys)
        }
        withContext(Dispatchers.EDT) { notify(OraSessionsBundle.message("html.reload.failed"), error, NotificationType.ERROR) }
        return null
    }

    private fun notify(title: String, content: String?, type: NotificationType) {
        val group = NotificationGroupManager.getInstance().getNotificationGroup(TerminateSessionAction.NOTIFICATION_GROUP)
        val notification = if (content == null) group.createNotification(title, type) else group.createNotification(title, content, type)
        notification.notify(project)
    }

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
