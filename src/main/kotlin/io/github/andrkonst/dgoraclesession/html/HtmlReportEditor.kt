package io.github.andrkonst.dgoraclesession.html

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import java.beans.PropertyChangeListener
import javax.swing.JComponent

/** Editor tab showing an HTML file from disk in the embedded Chromium. */
class HtmlReportFile(name: String, val reportUrl: String) : LightVirtualFile(name) {
    init {
        isWritable = false
    }
}

class HtmlReportEditor(private val file: HtmlReportFile) : UserDataHolderBase(), FileEditor {
    private val browser = JBCefBrowser(file.reportUrl)

    override fun getComponent(): JComponent = browser.component

    override fun getPreferredFocusedComponent(): JComponent = browser.component

    override fun getName() = "HTML"

    override fun getFile(): VirtualFile = file

    override fun setState(state: FileEditorState) = Unit

    override fun isModified() = false

    override fun isValid() = true

    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun dispose() {
        Disposer.dispose(browser)
    }
}

class HtmlReportEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile) = file is HtmlReportFile && JBCefApp.isSupported()

    override fun acceptRequiresReadAction() = false

    override fun createEditor(project: Project, file: VirtualFile): FileEditor = HtmlReportEditor(file as HtmlReportFile)

    override fun getEditorTypeId() = "DGOracleSession.HtmlReport"

    override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
