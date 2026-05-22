package dev.dengchao.idea.plugin.git.worktrees

import com.intellij.testFramework.LightPlatform4TestCase
import dev.dengchao.idea.plugin.git.worktrees.settings.GitWorktreesGlobalSettings
import dev.dengchao.idea.plugin.git.worktrees.settings.GitWorktreesProjectConfigurable
import dev.dengchao.idea.plugin.git.worktrees.settings.GitWorktreesProjectSettings
import org.junit.Test

class GitWorktreesSettingsTest : LightPlatform4TestCase() {

    @Test
    fun `default effective settings are enabled`() {
        val settings = GitWorktreesProjectSettings.getInstance(project)
        settings.loadState(GitWorktreesProjectSettings.State())
        GitWorktreesGlobalSettings.getInstance().loadState(GitWorktreesGlobalSettings.State())

        assertTrue(settings.effectiveShowRelativeLocations())
        assertTrue(settings.effectiveRememberGitWindowTab())
        assertTrue(GitWorktreesGlobalSettings.getInstance().state.showToolWindowButtonTipOnStartup)
    }

    @Test
    fun `project settings override global only when enabled`() {
        GitWorktreesGlobalSettings.getInstance().loadState(
            GitWorktreesGlobalSettings.State(
                showRelativeLocations = false,
                rememberGitWindowTab = false,
            ),
        )
        val settings = GitWorktreesProjectSettings.getInstance(project)
        settings.loadState(
            GitWorktreesProjectSettings.State(
                useProjectSettings = false,
                showRelativeLocations = true,
                rememberGitWindowTab = true,
                restoreGitWindowTab = false,
            ),
        )

        assertFalse(settings.effectiveShowRelativeLocations())
        assertFalse(settings.effectiveRememberGitWindowTab())

        settings.state.useProjectSettings = true

        assertTrue(settings.effectiveShowRelativeLocations())
        assertTrue(settings.effectiveRememberGitWindowTab())
    }

    @Test
    fun `configurable apply reset and isModified work for global and project settings`() {
        GitWorktreesGlobalSettings.getInstance().loadState(
            GitWorktreesGlobalSettings.State(
                showRelativeLocations = true,
                rememberGitWindowTab = true,
                showToolWindowButtonTipOnStartup = true,
            ),
        )
        GitWorktreesProjectSettings.getInstance(project).loadState(
            GitWorktreesProjectSettings.State(
                useProjectSettings = false,
                showRelativeLocations = true,
                rememberGitWindowTab = true,
                restoreGitWindowTab = true,
            ),
        )
        val configurable = GitWorktreesProjectConfigurable(project)
        configurable.createComponent()
        configurable.reset()

        assertFalse(configurable.isModified)

        configurable.setTargetForTests(GitWorktreesProjectConfigurable.SettingsTarget.GLOBAL)
        configurable.setShowRelativeLocationsForTests(false)
        configurable.setRememberGitWindowTabForTests(false)
        configurable.setShowToolWindowButtonTipOnStartupForTests(false)
        configurable.setTargetForTests(GitWorktreesProjectConfigurable.SettingsTarget.PROJECT)
        configurable.setUseProjectSettingsForTests(true)
        configurable.setShowRelativeLocationsForTests(false)
        configurable.setRememberGitWindowTabForTests(true)

        assertTrue(configurable.isModified)
        configurable.apply()
        assertFalse(configurable.isModified)

        val global = GitWorktreesGlobalSettings.getInstance().state
        val projectState = GitWorktreesProjectSettings.getInstance(project).state
        assertFalse(global.showRelativeLocations)
        assertFalse(global.rememberGitWindowTab)
        assertFalse(global.showToolWindowButtonTipOnStartup)
        assertTrue(projectState.useProjectSettings)
        assertFalse(projectState.showRelativeLocations)
        assertTrue(projectState.rememberGitWindowTab)
        assertTrue(projectState.restoreGitWindowTab)

        GitWorktreesGlobalSettings.getInstance().loadState(
            GitWorktreesGlobalSettings.State(
                showRelativeLocations = true,
                rememberGitWindowTab = true,
                showToolWindowButtonTipOnStartup = true,
            ),
        )
        configurable.reset()
        assertFalse(configurable.isModified)
    }
}
