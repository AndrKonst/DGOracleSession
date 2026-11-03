package io.github.andrkonst.orasessions.toolwindow.detail

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import io.github.andrkonst.orasessions.OraSessionsBundle
import io.github.andrkonst.orasessions.SessionsService
import io.github.andrkonst.orasessions.model.DetailDef
import io.github.andrkonst.orasessions.model.QueryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * Detail shown as read-only SQL-highlighted text. With the `/* concatenate */` hint all values are
 * glued together without separators (like PL/SQL Developer), otherwise rows are separated by new lines.
 */
class DetailTextPanel(project: Project, detail: DetailDef) : DetailPanel(project, detail) {
    private val service = SessionsService.getInstance(project)
    private val concatenate = hasConcatenateHint(detail.sql)
    private val statusLabel = JBLabel().apply { border = JBUI.Borders.empty(2, 6) }
    private val editor = EditorTextField("", project, PlainTextFileType.INSTANCE).apply {
        isViewer = true
        setOneLineMode(false)
        addSettingsProvider {
            it.settings.isUseSoftWraps = true
            it.settings.isLineNumbersShown = false
            it.setVerticalScrollbarVisible(true)
            it.setHorizontalScrollbarVisible(true)
        }
    }
    private val body = JPanel(BorderLayout()).apply {
        add(statusLabel, BorderLayout.NORTH)
        add(editor, BorderLayout.CENTER)
    }
    private var job: Job? = null

    override fun load(ds: LocalDataSource, values: Map<String, Any?>, reloadOnly: Boolean) {
        setBody(body)
        job?.cancel()
        statusLabel.icon = AnimatedIcon.Default.INSTANCE
        statusLabel.text = OraSessionsBundle.message("detail.loading")
        statusLabel.isVisible = true
        val sql = parsed.toPositional()
        val binds = parsed.names.map { values[it] }
        job = service.scope.launch {
            val result = try {
                Result.success(service.query(sql, binds))
            }
            catch (e: CancellationException) {
                throw e
            }
            catch (e: Throwable) {
                Result.failure(e)
            }
            withContext(Dispatchers.EDT) {
                result.onSuccess { showText(ds, toText(it)) }.onFailure { showError(it) }
            }
        }
    }

    private fun toText(result: QueryResult): String =
        result.rows.joinToString(if (concatenate) "" else "\n") { row ->
            row.joinToString(if (concatenate) "" else "\t") { it?.toString().orEmpty() }
        }

    private fun showText(ds: LocalDataSource, text: String) {
        statusLabel.isVisible = false
        val language = LocalDataSourceManager.getInstance(project).getQueryLanguage(ds) ?: PlainTextLanguage.INSTANCE
        val file = LightVirtualFile("detail.sql", language, text)
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return
        editor.setNewDocumentAndFileType(language.associatedFileType ?: PlainTextFileType.INSTANCE, document)
    }

    private fun showError(e: Throwable) {
        statusLabel.icon = AllIcons.General.Error
        statusLabel.text = e.message ?: e.javaClass.simpleName
        statusLabel.isVisible = true
        editor.text = ""
    }

    override fun showMessage(text: String) {
        job?.cancel()
        super.showMessage(text)
    }

    override fun dispose() {
        job?.cancel()
    }
}
