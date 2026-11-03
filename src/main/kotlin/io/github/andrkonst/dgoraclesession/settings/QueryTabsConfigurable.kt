package io.github.andrkonst.dgoraclesession.settings

import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.CollectionListModel
import com.intellij.ui.JBSplitter
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.github.andrkonst.dgoraclesession.DGOracleSessionBundle
import io.github.andrkonst.dgoraclesession.model.DefaultConfig
import io.github.andrkonst.dgoraclesession.model.DetailDef
import io.github.andrkonst.dgoraclesession.model.DetailMode
import io.github.andrkonst.dgoraclesession.model.TabDef
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

class QueryTabsConfigurable : SearchableConfigurable {
    private val tabsModel = CollectionListModel<TabDef>()
    private val tabsList = JBList(tabsModel)
    private val tabCaption = JBTextField()
    private val tabSql = SqlEditorField()

    private val detailsModel = CollectionListModel<DetailDef>()
    private val detailsList = JBList(detailsModel)
    private val detailCaption = JBTextField()
    private val detailMode = ComboBox(DetailMode.entries.toTypedArray())
    private val detailSql = SqlEditorField()
    private val detailHtmlColumns = JBTextField().apply { emptyText.text = DGOracleSessionBundle.message("settings.html.columns.empty") }
    private val detailKeyColumns = JBTextField().apply { emptyText.text = DGOracleSessionBundle.message("settings.key.columns.empty") }

    private var tabForm: JComponent? = null
    private var detailForm: JComponent? = null
    private var loading = false

    override fun getId() = ID

    override fun getDisplayName() = DGOracleSessionBundle.message("settings.display.name")

    override fun createComponent(): JComponent {
        tabsList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        tabsList.cellRenderer = SimpleListCellRenderer.create { label, tab, _ ->
            label.text = tab?.caption?.ifBlank { DGOracleSessionBundle.message("settings.untitled") }
        }
        detailsList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        detailsList.cellRenderer = SimpleListCellRenderer.create { label, detail, _ ->
            label.text = detail?.let { (it.caption.ifBlank { DGOracleSessionBundle.message("settings.untitled") }) + "  · " + it.mode.title() }
        }
        detailMode.renderer = SimpleListCellRenderer.create { label, mode, _ -> label.text = mode?.title() }

        val tabsPanel = ToolbarDecorator.createDecorator(tabsList)
            .setAddAction {
                tabsModel.add(TabDef(caption = DGOracleSessionBundle.message("settings.new.tab")))
                tabsList.selectedIndex = tabsModel.size - 1
            }
            .createPanel()

        val detailsPanel = ToolbarDecorator.createDecorator(detailsList)
            .setAddAction {
                val tab = selectedTab() ?: return@setAddAction
                val detail = DetailDef(caption = DGOracleSessionBundle.message("settings.new.detail"))
                tab.details += detail
                detailsModel.add(detail)
                detailsList.selectedIndex = detailsModel.size - 1
            }
            .setRemoveAction {
                val tab = selectedTab() ?: return@setRemoveAction
                val index = detailsList.selectedIndex.takeIf { it >= 0 } ?: return@setRemoveAction
                tab.details.removeAt(index)
                detailsModel.remove(index)
                if (detailsModel.size > 0) detailsList.selectedIndex = minOf(index, detailsModel.size - 1)
            }
            .setMoveUpAction { moveDetail(-1) }
            .setMoveDownAction { moveDetail(1) }
            .createPanel()

        val detailFormPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(DGOracleSessionBundle.message("settings.caption"), detailCaption)
            .addLabeledComponent(DGOracleSessionBundle.message("settings.mode"), detailMode)
            .addLabeledComponent(DGOracleSessionBundle.message("settings.html.columns"), detailHtmlColumns)
            .addLabeledComponent(DGOracleSessionBundle.message("settings.key.columns"), detailKeyColumns)
            .addLabeledComponentFillVertically(DGOracleSessionBundle.message("settings.sql"), detailSql)
            .panel
        detailForm = detailFormPanel

        val detailsSplitter = JBSplitter(false, "DGOracleSession.settings.details", 0.3f).apply {
            firstComponent = detailsPanel
            secondComponent = detailFormPanel
        }

        val tabFormPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(DGOracleSessionBundle.message("settings.caption"), tabCaption)
            .addLabeledComponentFillVertically(DGOracleSessionBundle.message("settings.sql"), tabSql)
            .panel
        val tabEditor = JBSplitter(true, "DGOracleSession.settings.tab", 0.5f).apply {
            firstComponent = tabFormPanel
            secondComponent = JPanel(BorderLayout()).apply {
                add(JBLabel(DGOracleSessionBundle.message("settings.details")).apply { border = JBUI.Borders.empty(6, 0, 4, 0) }, BorderLayout.NORTH)
                add(detailsSplitter, BorderLayout.CENTER)
            }
        }
        tabForm = tabEditor

        val mainSplitter = JBSplitter(false, "DGOracleSession.settings.main", 0.2f).apply {
            firstComponent = tabsPanel
            secondComponent = tabEditor
        }

        val hint = JBLabel(DGOracleSessionBundle.message("settings.hint")).apply {
            componentStyle = UIUtil.ComponentStyle.SMALL
            fontColor = UIUtil.FontColor.BRIGHTER
            setAllowAutoWrapping(true)
            setCopyable(true)
        }
        val resetButton = JButton(DGOracleSessionBundle.message("settings.reset")).apply {
            addActionListener { load(DefaultConfig.tabs()) }
        }
        val bottom = JPanel(VerticalLayout(JBUI.scale(6))).apply {
            border = JBUI.Borders.emptyTop(6)
            add(hint)
            add(JPanel(BorderLayout()).apply { add(resetButton, BorderLayout.WEST) })
        }

        tabsList.addListSelectionListener { if (!it.valueIsAdjusting) showTab() }
        detailsList.addListSelectionListener { if (!it.valueIsAdjusting) showDetail() }
        bindListeners()

        reset()
        return JPanel(BorderLayout()).apply {
            preferredSize = JBUI.size(900, 600)
            add(mainSplitter, BorderLayout.CENTER)
            add(bottom, BorderLayout.SOUTH)
        }
    }

    private fun bindListeners() {
        tabCaption.document.addDocumentListener(object : com.intellij.ui.DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                if (loading) return
                selectedTab()?.caption = tabCaption.text
                tabsList.repaint()
            }
        })
        tabSql.onTextChanged { if (!loading) selectedTab()?.sql = it }
        detailCaption.document.addDocumentListener(object : com.intellij.ui.DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                if (loading) return
                selectedDetail()?.caption = detailCaption.text
                detailsList.repaint()
            }
        })
        detailMode.addActionListener {
            if (loading) return@addActionListener
            selectedDetail()?.mode = detailMode.selectedItem as DetailMode
            detailsList.repaint()
            updateHtmlFields()
        }
        detailSql.onTextChanged { if (!loading) selectedDetail()?.sql = it }
        detailHtmlColumns.document.addDocumentListener(object : com.intellij.ui.DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                if (!loading) selectedDetail()?.htmlColumns = detailHtmlColumns.text
            }
        })
        detailKeyColumns.document.addDocumentListener(object : com.intellij.ui.DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                if (!loading) selectedDetail()?.keyColumns = detailKeyColumns.text
            }
        })
    }

    private fun selectedTab(): TabDef? = tabsList.selectedValue

    private fun selectedDetail(): DetailDef? = detailsList.selectedValue

    private fun showTab() {
        val tab = selectedTab()
        withLoading {
            tabCaption.text = tab?.caption.orEmpty()
            tabSql.text = tab?.sql.orEmpty()
            detailsModel.replaceAll(tab?.details.orEmpty())
        }
        setEnabled(tabForm, tab != null)
        if (detailsModel.size > 0) detailsList.selectedIndex = 0
        showDetail()
    }

    private fun showDetail() {
        val detail = selectedDetail()
        withLoading {
            detailCaption.text = detail?.caption.orEmpty()
            detailMode.selectedItem = detail?.mode ?: DetailMode.TABLE
            detailSql.text = detail?.sql.orEmpty()
            detailHtmlColumns.text = detail?.htmlColumns.orEmpty()
            detailKeyColumns.text = detail?.keyColumns.orEmpty()
        }
        setEnabled(detailForm, detail != null)
    }

    /** HTML columns are rendered only in grids, so the fields are disabled in Text mode. */
    private fun updateHtmlFields() {
        val enabled = selectedDetail()?.mode == DetailMode.TABLE
        detailHtmlColumns.isEnabled = enabled
        detailKeyColumns.isEnabled = enabled
    }

    private fun moveDetail(delta: Int) {
        val tab = selectedTab() ?: return
        val from = detailsList.selectedIndex
        val to = from + delta
        if (from < 0 || to !in tab.details.indices) return
        java.util.Collections.swap(tab.details, from, to)
        detailsModel.replaceAll(tab.details)
        detailsList.selectedIndex = to
    }

    private fun setEnabled(component: JComponent?, enabled: Boolean) {
        if (component != null) UIUtil.setEnabled(component, enabled, true)
        if (component === tabForm && enabled) setEnabled(detailForm, selectedDetail() != null)
        if (component === detailForm && enabled) updateHtmlFields()
    }

    private inline fun withLoading(block: () -> Unit) {
        loading = true
        try {
            block()
        }
        finally {
            loading = false
        }
    }

    private fun load(tabs: List<TabDef>) {
        tabsModel.replaceAll(tabs.map { it.deepCopy() })
        if (tabsModel.size > 0) tabsList.selectedIndex = 0
        showTab()
    }

    private fun currentTabs(): List<TabDef> = tabsModel.items

    override fun isModified(): Boolean = currentTabs() != QueryTabsSettings.getInstance().tabs

    override fun apply() {
        currentTabs().forEachIndexed { i, tab ->
            if (tab.caption.isBlank()) throw ConfigurationException(DGOracleSessionBundle.message("settings.error.tab.caption", i + 1))
            tab.details.forEachIndexed { j, detail ->
                if (detail.caption.isBlank()) {
                    throw ConfigurationException(DGOracleSessionBundle.message("settings.error.detail.caption", j + 1, tab.caption))
                }
            }
        }
        QueryTabsSettings.getInstance().tabs = currentTabs()
    }

    override fun reset() {
        val selected = tabsList.selectedIndex
        load(QueryTabsSettings.getInstance().tabs)
        if (selected in 0 until tabsModel.size) {
            tabsList.selectedIndex = selected
            showTab()
        }
    }

    override fun disposeUIResources() {
        tabForm = null
        detailForm = null
    }

    private fun DetailMode.title(): String = DGOracleSessionBundle.message("settings.mode.${name.lowercase()}")

    companion object {
        const val ID = "io.github.andrkonst.dgoraclesession.settings"
    }
}
