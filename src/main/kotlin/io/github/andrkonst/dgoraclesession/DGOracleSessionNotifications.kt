package io.github.andrkonst.dgoraclesession

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls

object DGOracleSessionNotifications {
    private const val GROUP = "DGOracleSession"

    fun notify(project: Project, @Nls title: String, @Nls content: String?, type: NotificationType) {
        val group = NotificationGroupManager.getInstance().getNotificationGroup(GROUP)
        val notification = if (content == null) group.createNotification(title, type) else group.createNotification(title, content, type)
        notification.notify(project)
    }
}
