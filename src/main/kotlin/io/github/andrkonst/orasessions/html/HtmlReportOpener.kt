package io.github.andrkonst.orasessions.html

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.jcef.JBCefApp
import io.github.andrkonst.orasessions.OraSessionsBundle
import io.github.andrkonst.orasessions.actions.TerminateSessionAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.Icon

enum class HtmlTarget(val icon: Icon) {
    IDE(AllIcons.Actions.Preview),
    BROWSER(AllIcons.General.Web),
    COPY(AllIcons.Actions.Copy),
}

/** Opens an HTML document from a grid cell via a temporary file deleted on IDE exit, or copies it to the clipboard. */
object HtmlReportOpener {
    private const val TEMP_DIR = "ora-sessions"
    private const val BOM = '\uFEFF'

    fun targets(): List<HtmlTarget> =
        if (JBCefApp.isSupported()) HtmlTarget.entries else HtmlTarget.entries - HtmlTarget.IDE

    suspend fun open(project: Project, title: String, html: String, target: HtmlTarget) {
        if (target == HtmlTarget.COPY) {
            withContext(Dispatchers.EDT) {
                CopyPasteManager.getInstance().setContents(StringSelection(html))
                NotificationGroupManager.getInstance().getNotificationGroup(TerminateSessionAction.NOTIFICATION_GROUP)
                    .createNotification(OraSessionsBundle.message("html.copied"), title, NotificationType.INFORMATION)
                    .notify(project)
            }
            return
        }
        val file = withContext(Dispatchers.IO) { write(title, html) }
        withContext(Dispatchers.EDT) {
            when (target) {
                HtmlTarget.IDE -> {
                    val url = file.toPath().toUri().toString()
                    FileEditorManager.getInstance(project).openFile(HtmlReportFile("$title.html", url), true)
                }
                HtmlTarget.BROWSER -> BrowserUtil.browse(file)
                HtmlTarget.COPY -> Unit
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
