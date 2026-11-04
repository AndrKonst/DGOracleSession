package io.github.andrkonst.dgoraclesession.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.annotations.XCollection
import io.github.andrkonst.dgoraclesession.model.DefaultConfig
import io.github.andrkonst.dgoraclesession.model.TabDef

@Service(Service.Level.APP)
@State(name = "DGOracleSessionQueryTabs", storages = [Storage("dgOracleSession.xml")])
class QueryTabsSettings : PersistentStateComponent<QueryTabsSettings.SettingsState> {
    class SettingsState {
        @XCollection(propertyElementName = "tabs")
        var tabs: MutableList<TabDef> = DefaultConfig.tabs()
    }

    private var state = SettingsState()

    override fun getState(): SettingsState = state

    override fun loadState(state: SettingsState) {
        this.state = state
    }

    var tabs: List<TabDef>
        get() = state.tabs.map { it.deepCopy() }
        set(value) {
            state.tabs = value.mapTo(mutableListOf()) { it.deepCopy() }
            ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).tabsChanged()
        }

    fun interface Listener {
        fun tabsChanged()
    }

    companion object {
        @JvmField
        val TOPIC: Topic<Listener> = Topic.create("DGOracleSession query tabs", Listener::class.java)

        fun getInstance(): QueryTabsSettings = service()
    }
}
