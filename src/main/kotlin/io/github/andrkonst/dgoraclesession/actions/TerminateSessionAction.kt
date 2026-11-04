package io.github.andrkonst.dgoraclesession.actions

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import io.github.andrkonst.dgoraclesession.DGOracleSessionBundle
import io.github.andrkonst.dgoraclesession.DGOracleSessionNotifications
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
            var instance: String? = null
            for (session in sessions) {
                try {
                    // DISCONNECT SESSION cannot address another RAC instance
                    if (mode == TerminateMode.DISCONNECT_IMMEDIATE && session.instId != null) {
                        val current = instance ?: service.adminInstance().also { instance = it }
                        if (session.instId.toString() != current) {
                            failures += session to DGOracleSessionBundle.message("terminate.other.instance", session.instId.toString(), current.orEmpty())
                            continue
                        }
                    }
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
        val succeeded = sessions.size - failures.size
        if (succeeded > 0) {
            DGOracleSessionNotifications.notify(
                project, DGOracleSessionBundle.message("terminate.done.${mode.key}", succeeded), null, NotificationType.INFORMATION,
            )
        }
        failures.forEach { (session, message) ->
            DGOracleSessionNotifications.notify(
                project, DGOracleSessionBundle.message("terminate.failed.${mode.key}", session.id), message, NotificationType.ERROR,
            )
        }
    }

    private val TerminateMode.key: String
        get() = when (this) {
            TerminateMode.KILL_IMMEDIATE -> "kill"
            TerminateMode.DISCONNECT_IMMEDIATE -> "disconnect"
        }

    private companion object {
        const val MAX_LISTED = 10
    }
}

class KillSessionImmediateAction : TerminateSessionAction(TerminateMode.KILL_IMMEDIATE)

class DisconnectSessionImmediateAction : TerminateSessionAction(TerminateMode.DISCONNECT_IMMEDIATE)
