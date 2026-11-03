package io.github.andrkonst.orasessions.toolwindow

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.model.RawDataSource
import com.intellij.database.psi.DataSourceManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.ui.JBUI
import io.github.andrkonst.orasessions.OraSessionsBundle
import io.github.andrkonst.orasessions.SessionsService
import io.github.andrkonst.orasessions.grid.QueryGrid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JPanel

class SessionsPanel(private val project: Project, parentDisposable: Disposable) : SimpleToolWindowPanel(true, true), Disposable {
    private val service = SessionsService.getInstance(project)
    private val dataSourceModel = DefaultComboBoxModel<LocalDataSource>()
    private val dataSourceCombo = ComboBox(dataSourceModel)
    private val contentPanel = JPanel(BorderLayout())
    private val emptyPanel = JBPanelWithEmptyText()
    private var queryGrid: QueryGrid? = null
    private var updatingCombo = false

    init {
        Disposer.register(parentDisposable, this)

        dataSourceCombo.renderer = SimpleListCellRenderer.create { label, ds, _ ->
            label.text = ds?.name ?: OraSessionsBundle.message("datasource.none")
            label.icon = ds?.dbms?.icon
        }
        dataSourceCombo.addActionListener {
            if (!updatingCombo) onDataSourceSelected(dataSourceCombo.selectedItem as LocalDataSource?)
        }

        toolbar = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(2))).apply {
            add(JBLabel(OraSessionsBundle.message("datasource.label")))
            add(dataSourceCombo)
        }
        setContent(contentPanel)

        project.messageBus.connect(this).subscribe(DataSourceManager.TOPIC, object : DataSourceManager.Listener {
            override fun <T : RawDataSource> dataSourceAdded(manager: DataSourceManager<T>, dataSource: T) = reloadDataSources()
            override fun <T : RawDataSource> dataSourceRemoved(manager: DataSourceManager<T>, dataSource: T) = reloadDataSources()
            override fun <T : RawDataSource> dataSourceChanged(manager: DataSourceManager<T>?, dataSource: T?) = reloadDataSources()
        })

        reloadDataSources(service.dataSource ?: service.restoreDataSource(), initial = true)
    }

    private fun reloadDataSources(preferred: LocalDataSource? = service.dataSource, initial: Boolean = false) {
        service.scope.launch(Dispatchers.EDT) {
            val sources = service.oracleDataSources()
            val selected = sources.firstOrNull { it.uniqueId == preferred?.uniqueId } ?: sources.singleOrNull()
            val current = (0 until dataSourceModel.size).map { dataSourceModel.getElementAt(it) }
            if (initial || current.map { it.uniqueId to it.name } != sources.map { it.uniqueId to it.name }) {
                updatingCombo = true
                try {
                    dataSourceModel.removeAllElements()
                    dataSourceModel.addAll(sources)
                    dataSourceModel.selectedItem = selected
                }
                finally {
                    updatingCombo = false
                }
            }
            if (initial || selected?.uniqueId != service.dataSource?.uniqueId) onDataSourceSelected(selected)
        }
    }

    private fun onDataSourceSelected(ds: LocalDataSource?) {
        service.selectDataSource(ds)
        queryGrid?.let { Disposer.dispose(it) }
        queryGrid = null
        contentPanel.removeAll()
        if (ds == null) {
            emptyPanel.emptyText.text = OraSessionsBundle.message(
                if (dataSourceModel.size == 0) "status.no.oracle.sources" else "toolwindow.empty.text"
            )
            contentPanel.add(emptyPanel, BorderLayout.CENTER)
        }
        else {
            val grid = QueryGrid(project, ds, TEST_QUERY, this)
            queryGrid = grid
            contentPanel.add(grid.component, BorderLayout.CENTER)
        }
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    override fun dispose() = Unit

    private companion object {
        const val TEST_QUERY = """select sid, serial#, username, status, osuser, machine, program, module, sql_id, event, logon_time
  from v${'$'}session
 where type = 'USER'
 order by sid"""
    }
}
