package io.github.andrkonst.orasessions.toolwindow

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.model.RawDataSource
import com.intellij.database.psi.DataSourceManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import io.github.andrkonst.orasessions.OraSessionsBundle
import io.github.andrkonst.orasessions.SessionsService
import io.github.andrkonst.orasessions.model.QueryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel

class SessionsPanel(private val project: Project, parentDisposable: Disposable) : SimpleToolWindowPanel(true, true), Disposable {
    private val service = SessionsService.getInstance(project)
    private val dataSourceModel = DefaultComboBoxModel<LocalDataSource>()
    private val dataSourceCombo = ComboBox(dataSourceModel)
    private val tableModel = object : DefaultTableModel() {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val table = JBTable(tableModel)
    private val statusLabel = JBLabel()
    private var loadJob: Job? = null
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

        val actions = DefaultActionGroup(RefreshAction())
        val actionToolbar = ActionManager.getInstance().createActionToolbar("OraSessions.Toolbar", actions, true)
        actionToolbar.targetComponent = this

        val toolbarPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
            add(JBLabel(OraSessionsBundle.message("datasource.label")))
            add(dataSourceCombo)
            add(actionToolbar.component)
        }
        toolbar = toolbarPanel

        table.autoResizeMode = JBTable.AUTO_RESIZE_OFF
        table.emptyText.text = OraSessionsBundle.message("toolwindow.empty.text")
        statusLabel.border = JBUI.Borders.empty(2, 6)
        setContent(JPanel(BorderLayout()).apply {
            add(JBScrollPane(table), BorderLayout.CENTER)
            add(statusLabel, BorderLayout.SOUTH)
        })

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
        if (ds == null) {
            loadJob?.cancel()
            tableModel.setDataVector(emptyArray(), emptyArray())
            statusLabel.text = OraSessionsBundle.message(if (dataSourceModel.size == 0) "status.no.oracle.sources" else "toolwindow.empty.text")
        }
        else {
            refresh()
        }
    }

    private fun refresh() {
        if (service.dataSource == null) return
        loadJob?.cancel()
        statusLabel.icon = AnimatedIcon.Default.INSTANCE
        statusLabel.text = OraSessionsBundle.message("status.loading")
        loadJob = service.scope.launch {
            val result = try {
                Result.success(service.query(TEST_QUERY))
            }
            catch (e: CancellationException) {
                throw e
            }
            catch (e: Throwable) {
                Result.failure(e)
            }
            withContext(Dispatchers.EDT) {
                result.onSuccess(::show).onFailure(::showError)
            }
        }
    }

    private fun show(result: QueryResult) {
        val columns = result.columns.map { it.name }.toTypedArray()
        val rows = result.rows.map { it.toTypedArray() }.toTypedArray()
        tableModel.setDataVector(rows, columns)
        statusLabel.icon = null
        statusLabel.text = OraSessionsBundle.message(
            "status.loaded",
            result.rows.size,
            if (result.truncated) "+" else "",
            result.elapsedMs,
            service.ownSid ?: "?",
        )
    }

    private fun showError(e: Throwable) {
        tableModel.setDataVector(emptyArray(), emptyArray())
        statusLabel.icon = AllIcons.General.Error
        statusLabel.text = e.message ?: e.javaClass.simpleName
    }

    override fun dispose() {
        loadJob?.cancel()
    }

    private inner class RefreshAction : DumbAwareAction(
        OraSessionsBundle.message("action.refresh.text"), null, AllIcons.Actions.Refresh,
    ) {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = service.dataSource != null
        }
        override fun actionPerformed(e: AnActionEvent) = refresh()
    }

    private companion object {
        const val TEST_QUERY = """
            select sid, serial#, username, status, osuser, machine, program, module, sql_id, event, logon_time
              from v${'$'}session
             where type = 'USER'
             order by sid
        """
    }
}
