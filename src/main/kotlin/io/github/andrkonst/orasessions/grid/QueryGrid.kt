package io.github.andrkonst.orasessions.grid

import com.intellij.database.console.session.DatabaseSessionManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.connection.DGDepartment
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.DataGridUtil
import com.intellij.database.datagrid.DatabaseGridDataHookUp
import com.intellij.database.datagrid.DbGridDataHookUpUtil
import com.intellij.database.datagrid.GridRequestSource
import com.intellij.database.datagrid.GridUtil
import com.intellij.database.run.ui.DataGridRequestPlace
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import javax.swing.JComponent

/**
 * Read-only DataGrip result grid for an arbitrary SQL query, with the standard grid toolbar
 * (reload, auto-refresh, export, etc.). Queries run in a dedicated DataGrip session.
 */
class QueryGrid(
    project: Project,
    dataSource: LocalDataSource,
    val sql: String,
    parentDisposable: Disposable,
) : Disposable {
    private val hookUp: DatabaseGridDataHookUp
    val grid: DataGrid
    val component: JComponent

    init {
        Disposer.register(parentDisposable, this)
        val session = DatabaseSessionManager.getSession(project, dataSource, SESSION_TITLE)
        hookUp = DbGridDataHookUpUtil.createReadOnlyDatabaseHookUp(project, session, this, sql, DGDepartment.UNKNOWN)
        grid = DataGridUtil.createDataGrid(project, hookUp, GridUtil.getGridPopupActions())
        Disposer.register(this, grid)
        GridUtil.addGridHeaderComponent(grid)
        component = grid.panel.component
        hookUp.loader.loadFirstPage(GridRequestSource(DataGridRequestPlace(grid)))
    }

    fun reload() {
        hookUp.loader.reloadCurrentPage(GridRequestSource(DataGridRequestPlace(grid)))
    }

    override fun dispose() = Unit

    companion object {
        const val SESSION_TITLE = "Oracle Sessions"
    }
}
