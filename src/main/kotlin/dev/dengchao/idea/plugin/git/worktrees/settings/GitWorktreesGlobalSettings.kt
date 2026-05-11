package dev.dengchao.idea.plugin.git.worktrees.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "GitWorktreesGlobalSettings",
    storages = [Storage("git-worktrees-gw4i.xml")],
)
@Service(Service.Level.APP)
class GitWorktreesGlobalSettings : PersistentStateComponent<GitWorktreesGlobalSettings.State> {

    data class State(
        var showRelativeLocations: Boolean = true,
        var rememberGitWindowTab: Boolean = true,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        fun getInstance(): GitWorktreesGlobalSettings {
            return ApplicationManager.getApplication().getService(GitWorktreesGlobalSettings::class.java)
        }
    }
}
