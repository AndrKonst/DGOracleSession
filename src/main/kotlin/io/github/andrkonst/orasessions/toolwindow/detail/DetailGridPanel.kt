package io.github.andrkonst.orasessions.toolwindow.detail

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import io.github.andrkonst.orasessions.grid.QueryGrid
import io.github.andrkonst.orasessions.model.DetailDef

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
        val grid = QueryGrid(project, ds, sql, this)
        queryGrid = grid
        dataSourceId = ds.uniqueId
        setBody(grid.component)
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
