package dev.dengchao.idea.plugin.git.worktrees.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project

@State(
    name = "GitWorktreesProjectSettings",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
)
@Service(Service.Level.PROJECT)
class GitWorktreesProjectSettings : PersistentStateComponent<GitWorktreesProjectSettings.State> {

    data class State(
        var useProjectSettings: Boolean = false,
        var showRelativeLocations: Boolean = true,
        var rememberGitWindowTab: Boolean = true,
        var restoreGitWindowTab: Boolean = false,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    fun effectiveShowRelativeLocations(): Boolean {
        val current = state
        return if (current.useProjectSettings) current.showRelativeLocations else GitWorktreesGlobalSettings.getInstance().state.showRelativeLocations
    }

    fun effectiveRememberGitWindowTab(): Boolean {
        val current = state
        return if (current.useProjectSettings) current.rememberGitWindowTab else GitWorktreesGlobalSettings.getInstance().state.rememberGitWindowTab
    }

    fun markGitWindowTabOpenedIfRemembered() {
        state.restoreGitWindowTab = effectiveRememberGitWindowTab()
    }

    fun clearGitWindowTabRestore() {
        state.restoreGitWindowTab = false
    }

    fun shouldRestoreGitWindowTabOnStartup(): Boolean {
        return effectiveRememberGitWindowTab() && state.restoreGitWindowTab
    }

    companion object {
        fun getInstance(project: Project): GitWorktreesProjectSettings {
            return project.getService(GitWorktreesProjectSettings::class.java)
        }
    }
}
