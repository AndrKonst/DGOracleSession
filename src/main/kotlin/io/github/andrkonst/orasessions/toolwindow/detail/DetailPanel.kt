package io.github.andrkonst.orasessions.toolwindow.detail

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanelWithEmptyText
import io.github.andrkonst.orasessions.OraSessionsBundle
import io.github.andrkonst.orasessions.db.BindParser
import io.github.andrkonst.orasessions.model.DetailDef
import io.github.andrkonst.orasessions.model.DetailMode
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * One detail sub-tab. Shows the result of the detail query for the row selected in the tab grid.
 * The query is (re)executed only while the sub-tab is active; inactive sub-tabs remember they are stale.
 */
abstract class DetailPanel(
    protected val project: Project,
    val detail: DetailDef,
) : JPanel(BorderLayout()), Disposable {
    protected val parsed: BindParser = BindParser.parse(detail.sql)

    private var dataSource: LocalDataSource? = null
    private var rowValues: Map<String, Any?>? = null
    private var stale = true
    private var active = false

    /** Called when the selected row changed or the tab grid was reloaded. */
    fun update(ds: LocalDataSource?, values: Map<String, Any?>?, reload: Boolean) {
        val changed = ds?.uniqueId != dataSource?.uniqueId || values != rowValues
        dataSource = ds
        rowValues = values
        if (changed || reload) stale = true
        if (active) refreshIfStale(reloadOnly = !changed)
    }

    fun setActive(value: Boolean) {
        active = value
        if (value) refreshIfStale(reloadOnly = false)
    }

    private fun refreshIfStale(reloadOnly: Boolean) {
        if (!stale) return
        stale = false
        val ds = dataSource
        val values = rowValues
        when {
            detail.sql.isBlank() -> showMessage(OraSessionsBundle.message("detail.empty.sql"))
            ds == null -> showMessage(OraSessionsBundle.message("toolwindow.empty.text"))
            values == null -> showMessage(OraSessionsBundle.message("detail.no.row"))
            else -> {
                val missing = parsed.names.distinct().filter { it !in values }
                if (missing.isNotEmpty()) {
                    showMessage(OraSessionsBundle.message("detail.missing.columns", missing.joinToString { ":$it" }))
                }
                else {
                    load(ds, values, reloadOnly)
                }
            }
        }
    }

    protected abstract fun load(ds: LocalDataSource, values: Map<String, Any?>, reloadOnly: Boolean)

    protected open fun showMessage(text: String) {
        setBody(JBPanelWithEmptyText().withEmptyText(text))
    }

    protected fun setBody(component: java.awt.Component) {
        removeAll()
        add(component, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    override fun dispose() = Unit

    companion object {
        fun create(project: Project, detail: DetailDef, parent: Disposable): DetailPanel {
            val panel = if (detail.mode == DetailMode.TEXT) {
                DetailTextPanel(project, detail)
            }
            else {
                DetailGridPanel(project, detail)
            }
            com.intellij.openapi.util.Disposer.register(parent, panel)
            return panel
        }
    }
}
