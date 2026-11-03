package io.github.andrkonst.orasessions.toolwindow

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBPanelWithEmptyText
import io.github.andrkonst.orasessions.OraSessionsBundle
import io.github.andrkonst.orasessions.grid.QueryGrid
import io.github.andrkonst.orasessions.model.TabDef
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * One top-level tab: the grid of the tab query. The grid is created lazily on first activation.
 */
class QueryTabPanel(
    private val project: Project,
    val tab: TabDef,
    parentDisposable: Disposable,
) : JPanel(BorderLayout()), Disposable {
    private var dataSource: LocalDataSource? = null
    private var queryGrid: QueryGrid? = null

    init {
        Disposer.register(parentDisposable, this)
        showEmpty()
    }

    fun setDataSource(ds: LocalDataSource?) {
        if (ds?.uniqueId == dataSource?.uniqueId && queryGrid != null) return
        dataSource = ds
        disposeGrid()
        showEmpty()
    }

    fun activate() {
        if (queryGrid != null) return
        val ds = dataSource ?: return
        if (tab.sql.isBlank()) return
        val grid = QueryGrid(project, ds, tab.sql, this)
        queryGrid = grid
        removeAll()
        add(grid.component, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    private fun showEmpty() {
        removeAll()
        val key = when {
            tab.sql.isBlank() -> "tab.empty.sql"
            dataSource == null -> "toolwindow.empty.text"
            else -> null
        }
        add(JBPanelWithEmptyText().apply { if (key != null) emptyText.text = OraSessionsBundle.message(key) }, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    private fun disposeGrid() {
        queryGrid?.let { Disposer.dispose(it) }
        queryGrid = null
    }

    override fun dispose() {
        queryGrid = null
    }
}
