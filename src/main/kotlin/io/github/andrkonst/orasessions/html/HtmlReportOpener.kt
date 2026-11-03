package io.github.andrkonst.orasessions.html

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.jcef.JBCefApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.Icon

enum class HtmlTarget(val icon: Icon) {
    IDE(AllIcons.Actions.Preview),
    BROWSER(AllIcons.General.Web),
}

/** Opens an HTML document from a grid cell via a temporary file deleted on IDE exit. */
object HtmlReportOpener {
    private const val TEMP_DIR = "ora-sessions"
    private const val BOM = '\uFEFF'

    fun targets(): List<HtmlTarget> =
        if (JBCefApp.isSupported()) listOf(HtmlTarget.IDE, HtmlTarget.BROWSER) else listOf(HtmlTarget.BROWSER)

    suspend fun open(project: Project, title: String, html: String, target: HtmlTarget) {
        val file = withContext(Dispatchers.IO) { write(title, html) }
        withContext(Dispatchers.EDT) {
            when (target) {
                HtmlTarget.IDE -> {
                    val url = file.toPath().toUri().toString()
                    FileEditorManager.getInstance(project).openFile(HtmlReportFile("$title.html", url), true)
                }
                HtmlTarget.BROWSER -> BrowserUtil.browse(file)
            }
        }
    }

    private fun write(title: String, html: String): File {
        val dir = File(PathManager.getTempPath(), TEMP_DIR)
        val prefix = FileUtil.sanitizeFileName(title, false).take(60).ifBlank { "report" } + "-"
        val file = FileUtil.createTempFile(dir, prefix, ".html", true, true)
        // BOM makes browsers read a file without <meta charset> as UTF-8
        file.writeText(BOM + html, Charsets.UTF_8)
        return file
    }
}
