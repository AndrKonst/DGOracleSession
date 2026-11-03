package io.github.andrkonst.orasessions.toolwindow

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.DataGridListener
import com.intellij.database.datagrid.GridRequestSource
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.Alarm
import io.github.andrkonst.orasessions.OraSessionsBundle
import io.github.andrkonst.orasessions.grid.GridRows
import io.github.andrkonst.orasessions.grid.QueryGrid
import io.github.andrkonst.orasessions.model.TabDef
import io.github.andrkonst.orasessions.toolwindow.detail.DetailPanel
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * One top-level tab: the grid of the tab query and, if configured, detail sub-tabs for the selected row.
 * The grid is created lazily on first activation.
 */
class QueryTabPanel(
    private val project: Project,
    val tab: TabDef,
    parentDisposable: Disposable,
) : JPanel(BorderLayout()), Disposable {
    private var dataSource: LocalDataSource? = null
    private var queryGrid: QueryGrid? = null
    private val gridHolder = JPanel(BorderLayout())
    private val detailTabs = JBTabbedPane()
    private val detailPanels = mutableListOf<DetailPanel>()
    private val detailsAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private var selectedKey: List<String>? = null
    private var pendingReload = false

    init {
        Disposer.register(parentDisposable, this)

        if (tab.details.isEmpty()) {
            add(gridHolder, BorderLayout.CENTER)
        }
        else {
            tab.details.forEach { detail ->
                val panel = DetailPanel.create(project, detail, this)
                detailPanels += panel
                detailTabs.addTab(detail.caption, panel)
            }
            detailTabs.addChangeListener { updateActiveDetail() }
            val splitter = OnePixelSplitter(true, "OraSessions.details.${tab.caption}", 0.6f).apply {
                firstComponent = gridHolder
                secondComponent = detailTabs
                dividerPositionStrategy = Splitter.DividerPositionStrategy.KEEP_PROPORTION
            }
            add(splitter, BorderLayout.CENTER)
            updateActiveDetail()
        }
        showEmpty()
    }

    fun setDataSource(ds: LocalDataSource?) {
        if (ds?.uniqueId == dataSource?.uniqueId && queryGrid != null) return
        dataSource = ds
        disposeGrid()
        showEmpty()
        updateDetails(reload = false)
    }

    fun activate() {
        if (queryGrid != null) return
        val ds = dataSource ?: return
        if (tab.sql.isBlank()) return
        val grid = QueryGrid(project, ds, tab.sql, this)
        queryGrid = grid
        if (detailPanels.isNotEmpty()) {
            grid.grid.addDataGridListener(object : DataGridListener {
                override fun onSelectionChanged(dataGrid: DataGrid) = scheduleDetailsUpdate(reload = false)
                override fun onContentChanged(dataGrid: DataGrid, place: GridRequestSource.RequestPlace?) {
                    restoreSelection(dataGrid)
                    scheduleDetailsUpdate(reload = true)
                }
            }, grid)
        }
        setGridComponent(grid.component)
    }

    private fun restoreSelection(grid: DataGrid) {
        val key = selectedKey ?: return
        val current = GridRows.selectedValues(grid)?.let(GridRows::key)
        if (current == key) return
        val row = GridRows.findRow(grid, key) ?: return
        grid.selectionModel.setRowSelection(row, true)
    }

    private fun scheduleDetailsUpdate(reload: Boolean) {
        pendingReload = pendingReload || reload
        detailsAlarm.cancelAllRequests()
        detailsAlarm.addRequest({
            val r = pendingReload
            pendingReload = false
            updateDetails(r)
        }, DETAILS_DELAY_MS)
    }

    private fun updateDetails(reload: Boolean) {
        if (detailPanels.isEmpty()) return
        val values = queryGrid?.grid?.let(GridRows::selectedValues)
        if (values != null) selectedKey = GridRows.key(values)
        detailPanels.forEach { it.update(dataSource, values, reload) }
    }

    private fun updateActiveDetail() {
        detailPanels.forEachIndexed { i, panel -> panel.setActive(i == detailTabs.selectedIndex) }
    }

    private fun showEmpty() {
        val key = when {
            tab.sql.isBlank() -> "tab.empty.sql"
            dataSource == null -> "toolwindow.empty.text"
            else -> null
        }
        setGridComponent(JBPanelWithEmptyText().apply { if (key != null) emptyText.text = OraSessionsBundle.message(key) })
    }

    private fun setGridComponent(component: java.awt.Component) {
        gridHolder.removeAll()
        gridHolder.add(component, BorderLayout.CENTER)
        gridHolder.revalidate()
        gridHolder.repaint()
    }

    private fun disposeGrid() {
        queryGrid?.let { Disposer.dispose(it) }
        queryGrid = null
        selectedKey = null
    }

    override fun dispose() {
        queryGrid = null
    }

    private companion object {
        const val DETAILS_DELAY_MS = 300
    }
}
