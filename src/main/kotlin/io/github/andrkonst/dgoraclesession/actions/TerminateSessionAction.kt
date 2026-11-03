package io.github.andrkonst.dgoraclesession.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import io.github.andrkonst.dgoraclesession.DGOracleSessionBundle
import io.github.andrkonst.dgoraclesession.SessionsService
import io.github.andrkonst.dgoraclesession.model.SessionRef
import io.github.andrkonst.dgoraclesession.model.TerminateMode
import io.github.andrkonst.dgoraclesession.toolwindow.QueryTabPanel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class TerminateSessionAction(private val mode: TerminateMode) : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val panel = e.getData(QueryTabPanel.KEY)
        e.presentation.isVisible = !e.isFromContextMenu || panel?.hasSessionColumns() == true
        e.presentation.isEnabled = panel?.selectedSessions()?.isNotEmpty() == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val panel = e.getData(QueryTabPanel.KEY) ?: return
        val sessions = panel.selectedSessions()
        if (sessions.isEmpty()) return
        if (!confirm(project, sessions)) return

        val service = SessionsService.getInstance(project)
        service.scope.launch {
            val failures = mutableListOf<Pair<SessionRef, String>>()
            for (session in sessions) {
                try {
                    service.execute(mode.sql(session))
                }
                catch (ex: CancellationException) {
                    throw ex
                }
                catch (ex: Throwable) {
                    failures += session to (ex.message ?: ex.javaClass.simpleName)
                }
            }
            withContext(Dispatchers.EDT) {
                notify(project, sessions, failures)
                panel.reloadGrid()
            }
        }
    }

    private fun confirm(project: Project, sessions: List<SessionRef>): Boolean {
        val list = sessions.take(MAX_LISTED).joinToString("\n") { s ->
            s.id + (s.username?.let { " ($it)" } ?: "")
        } + if (sessions.size > MAX_LISTED) "\n…" else ""
        return MessageDialogBuilder.yesNo(
            DGOracleSessionBundle.message("terminate.confirm.title.${mode.key}"),
            DGOracleSessionBundle.message("terminate.confirm.message.${mode.key}", sessions.size, list),
        )
            .yesText(DGOracleSessionBundle.message("terminate.confirm.yes.${mode.key}"))
            .asWarning()
            .ask(project)
    }

    private fun notify(project: Project, sessions: List<SessionRef>, failures: List<Pair<SessionRef, String>>) {
        val group = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
        val succeeded = sessions.size - failures.size
        if (succeeded > 0) {
            group.createNotification(
                DGOracleSessionBundle.message("terminate.done.${mode.key}", succeeded),
                NotificationType.INFORMATION,
            ).notify(project)
        }
        failures.forEach { (session, message) ->
            group.createNotification(
                DGOracleSessionBundle.message("terminate.failed.${mode.key}", session.id),
                message,
                NotificationType.ERROR,
            ).notify(project)
        }
    }

    private val TerminateMode.key: String
        get() = when (this) {
            TerminateMode.KILL_IMMEDIATE -> "kill"
            TerminateMode.DISCONNECT_IMMEDIATE -> "disconnect"
        }

    companion object {
        const val NOTIFICATION_GROUP = "DGOracleSession"
        private const val MAX_LISTED = 10
    }
}

class KillSessionImmediateAction : TerminateSessionAction(TerminateMode.KILL_IMMEDIATE)

class DisconnectSessionImmediateAction : TerminateSessionAction(TerminateMode.DISCONNECT_IMMEDIATE)
