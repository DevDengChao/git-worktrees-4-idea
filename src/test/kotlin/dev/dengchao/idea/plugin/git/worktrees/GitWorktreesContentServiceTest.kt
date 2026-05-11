package dev.dengchao.idea.plugin.git.worktrees

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.ui.content.ContentFactory
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesContentService
import dev.dengchao.idea.plugin.git.worktrees.settings.GitWorktreesGlobalSettings
import dev.dengchao.idea.plugin.git.worktrees.settings.GitWorktreesProjectSettings
import javax.swing.JPanel
import org.junit.Test

class GitWorktreesContentServiceTest : LightPlatform4TestCase() {

    @Test
    fun `openOrSelectWorktreesTab creates one closable Worktrees content and selects it`() {
        val contentManager = ContentFactory.getInstance().createContentManager(false, project)
        Disposer.register(testRootDisposable, contentManager)
        val service = GitWorktreesContentService.getInstance(project)
        service.overridePanelFactoryForTests({ JPanel() }, testRootDisposable)

        val first = service.openOrSelectWorktreesTab(contentManager)
        val second = service.openOrSelectWorktreesTab(contentManager)

        assertSame(first, second)
        assertEquals(1, contentManager.contentCount)
        assertSame(first, contentManager.selectedContent)
        assertEquals("Worktrees", first.displayName)
        assertTrue(first.isCloseable)
    }

    @Test
    fun `opening Worktrees tab records startup restore flag when remembering is enabled`() {
        val contentManager = ContentFactory.getInstance().createContentManager(false, project)
        Disposer.register(testRootDisposable, contentManager)
        val service = GitWorktreesContentService.getInstance(project)
        service.overridePanelFactoryForTests({ JPanel() }, testRootDisposable)
        setRememberGitWindowTab(globalValue = true, projectOverride = false, projectValue = true)

        service.openOrSelectWorktreesTab(contentManager)

        assertTrue(GitWorktreesProjectSettings.getInstance(project).state.restoreGitWindowTab)
    }

    @Test
    fun `opening Worktrees tab does not record startup restore flag when remembering is disabled`() {
        val contentManager = ContentFactory.getInstance().createContentManager(false, project)
        Disposer.register(testRootDisposable, contentManager)
        val service = GitWorktreesContentService.getInstance(project)
        service.overridePanelFactoryForTests({ JPanel() }, testRootDisposable)
        setRememberGitWindowTab(globalValue = false, projectOverride = false, projectValue = true)

        service.openOrSelectWorktreesTab(contentManager)

        assertFalse(GitWorktreesProjectSettings.getInstance(project).state.restoreGitWindowTab)
    }

    @Test
    fun `closing Worktrees tab clears startup restore flag`() {
        val contentManager = ContentFactory.getInstance().createContentManager(false, project)
        Disposer.register(testRootDisposable, contentManager)
        val service = GitWorktreesContentService.getInstance(project)
        service.overridePanelFactoryForTests({ JPanel() }, testRootDisposable)
        setRememberGitWindowTab(globalValue = true, projectOverride = false, projectValue = true)

        val content = service.openOrSelectWorktreesTab(contentManager)
        assertTrue(GitWorktreesProjectSettings.getInstance(project).state.restoreGitWindowTab)

        contentManager.removeContent(content, true)

        assertFalse(GitWorktreesProjectSettings.getInstance(project).state.restoreGitWindowTab)
    }

    @Test
    fun `startup restore opens Worktrees tab only when remember and restore flag are enabled`() {
        val contentManager = ContentFactory.getInstance().createContentManager(false, project)
        Disposer.register(testRootDisposable, contentManager)
        val service = GitWorktreesContentService.getInstance(project)
        service.overridePanelFactoryForTests({ JPanel() }, testRootDisposable)

        setRememberGitWindowTab(globalValue = true, projectOverride = false, projectValue = true)
        GitWorktreesProjectSettings.getInstance(project).state.restoreGitWindowTab = true
        service.restoreWorktreesTabIfNeeded(contentManager)
        assertEquals(1, contentManager.contentCount)

        contentManager.removeAllContents(true)
        setRememberGitWindowTab(globalValue = false, projectOverride = false, projectValue = true)
        GitWorktreesProjectSettings.getInstance(project).state.restoreGitWindowTab = true
        service.restoreWorktreesTabIfNeeded(contentManager)
        assertEquals(0, contentManager.contentCount)

        setRememberGitWindowTab(globalValue = true, projectOverride = false, projectValue = true)
        GitWorktreesProjectSettings.getInstance(project).state.restoreGitWindowTab = false
        service.restoreWorktreesTabIfNeeded(contentManager)
        assertEquals(0, contentManager.contentCount)
    }

    private fun setRememberGitWindowTab(
        globalValue: Boolean,
        projectOverride: Boolean,
        projectValue: Boolean,
    ) {
        GitWorktreesGlobalSettings.getInstance().loadState(
            GitWorktreesGlobalSettings.State(
                showRelativeLocations = true,
                rememberGitWindowTab = globalValue,
            ),
        )
        GitWorktreesProjectSettings.getInstance(project).loadState(
            GitWorktreesProjectSettings.State(
                useProjectSettings = projectOverride,
                showRelativeLocations = true,
                rememberGitWindowTab = projectValue,
                restoreGitWindowTab = false,
            ),
        )
    }
}
