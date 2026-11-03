package io.github.andrkonst.orasessions.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import io.github.andrkonst.orasessions.settings.QueryTabsConfigurable

class OpenSettingsAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        ShowSettingsUtil.getInstance().showSettingsDialog(e.project, QueryTabsConfigurable::class.java)
    }
}
