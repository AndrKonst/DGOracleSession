package io.github.andrkonst.orasessions.toolwindow

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.model.RawDataSource
import com.intellij.database.psi.DataSourceManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import io.github.andrkonst.orasessions.OraSessionsBundle
import io.github.andrkonst.orasessions.SessionsService
import io.github.andrkonst.orasessions.settings.QueryTabsSettings
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
    private val tabbedPane = JBTabbedPane()
    private val tabPanels = mutableListOf<QueryTabPanel>()
    private var updatingCombo = false
    private var rebuildingTabs = false

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
        tabbedPane.addChangeListener { activateSelectedTab() }
        setContent(contentPanel)

        project.messageBus.connect(this).subscribe(DataSourceManager.TOPIC, object : DataSourceManager.Listener {
            override fun <T : RawDataSource> dataSourceAdded(manager: DataSourceManager<T>, dataSource: T) = reloadDataSources()
            override fun <T : RawDataSource> dataSourceRemoved(manager: DataSourceManager<T>, dataSource: T) = reloadDataSources()
            override fun <T : RawDataSource> dataSourceChanged(manager: DataSourceManager<T>?, dataSource: T?) = reloadDataSources()
        })
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(QueryTabsSettings.TOPIC, QueryTabsSettings.Listener { rebuildTabs() })

        rebuildTabs()
        reloadDataSources(service.dataSource ?: service.restoreDataSource(), initial = true)
    }

    private fun rebuildTabs() {
        val selectedCaption = tabPanels.getOrNull(tabbedPane.selectedIndex)?.tab?.caption
        rebuildingTabs = true
        try {
            tabbedPane.removeAll()
            tabPanels.forEach { Disposer.dispose(it) }
            tabPanels.clear()

            QueryTabsSettings.getInstance().tabs.forEach { tab ->
                val panel = QueryTabPanel(project, tab, this)
                panel.setDataSource(service.dataSource)
                tabPanels += panel
                tabbedPane.addTab(tab.caption, panel)
            }
            tabPanels.indexOfFirst { it.tab.caption == selectedCaption }.takeIf { it >= 0 }?.let { tabbedPane.selectedIndex = it }
        }
        finally {
            rebuildingTabs = false
        }

        contentPanel.removeAll()
        if (tabPanels.isEmpty()) {
            contentPanel.add(JBPanelWithEmptyText().withEmptyText(OraSessionsBundle.message("tabs.empty")), BorderLayout.CENTER)
        }
        else {
            contentPanel.add(tabbedPane, BorderLayout.CENTER)
        }
        contentPanel.revalidate()
        contentPanel.repaint()
        activateSelectedTab()
    }

    private fun activateSelectedTab() {
        if (rebuildingTabs) return
        tabPanels.getOrNull(tabbedPane.selectedIndex)?.activate()
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
        tabPanels.forEach { it.setDataSource(ds) }
        activateSelectedTab()
    }

    override fun dispose() = Unit
}
