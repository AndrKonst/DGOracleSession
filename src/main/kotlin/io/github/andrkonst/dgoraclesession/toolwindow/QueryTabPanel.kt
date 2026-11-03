package io.github.andrkonst.dgoraclesession.toolwindow

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.DataGridListener
import com.intellij.database.datagrid.GridRequestSource
import com.intellij.database.datagrid.GridUtil
import com.intellij.database.run.ui.DataAccessType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.Alarm
import io.github.andrkonst.dgoraclesession.DGOracleSessionBundle
import io.github.andrkonst.dgoraclesession.grid.GridRows
import io.github.andrkonst.dgoraclesession.grid.QueryGrid
import io.github.andrkonst.dgoraclesession.model.SessionRef
import io.github.andrkonst.dgoraclesession.model.TabDef
import io.github.andrkonst.dgoraclesession.toolwindow.detail.DetailPanel
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
) : JPanel(BorderLayout()), Disposable, UiDataProvider {
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
            val splitter = OnePixelSplitter(true, "DGOracleSession.details.${tab.caption}", 0.6f).apply {
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
        val popupActions = DefaultActionGroup(
            ActionManager.getInstance().getAction(SESSION_ACTIONS_GROUP),
            Separator.create(),
            GridUtil.getGridPopupActions(),
        )
        val grid = QueryGrid(project, ds, tab.sql, this, popupActions)
        queryGrid = grid
        grid.grid.addDataGridListener(object : DataGridListener {
            override fun onSelectionChanged(dataGrid: DataGrid) = scheduleDetailsUpdate(reload = false)
            override fun onContentChanged(dataGrid: DataGrid, place: GridRequestSource.RequestPlace?) {
                restoreSelection(dataGrid)
                scheduleDetailsUpdate(reload = true)
            }
        }, grid)
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
        val values = queryGrid?.grid?.let(GridRows::selectedValues)
        if (values != null) selectedKey = GridRows.key(values)
        if (detailPanels.isEmpty()) return
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
        setGridComponent(JBPanelWithEmptyText().apply { if (key != null) emptyText.text = DGOracleSessionBundle.message(key) })
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

    fun hasSessionColumns(): Boolean {
        val grid = queryGrid?.grid ?: return false
        val names = grid.getDataModel(DataAccessType.DATABASE_DATA).columns.map { it.name.lowercase() }
        return "sid" in names && "serial#" in names
    }

    fun selectedSessions(): List<SessionRef> {
        val grid = queryGrid?.grid ?: return emptyList()
        return GridRows.selectedRowsValues(grid).mapNotNull(SessionRef::fromRow)
    }

    fun reloadGrid() {
        queryGrid?.reload()
    }

    override fun uiDataSnapshot(sink: DataSink) {
        sink[KEY] = this
    }

    override fun dispose() {
        queryGrid = null
    }

    companion object {
        val KEY: DataKey<QueryTabPanel> = DataKey.create("DGOracleSession.QueryTabPanel")
        const val SESSION_ACTIONS_GROUP = "DGOracleSession.SessionActions"
        private const val DETAILS_DELAY_MS = 300
    }
}
