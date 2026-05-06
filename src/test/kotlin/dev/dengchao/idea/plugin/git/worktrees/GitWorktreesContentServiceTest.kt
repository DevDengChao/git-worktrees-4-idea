package dev.dengchao.idea.plugin.git.worktrees

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.ui.content.ContentFactory
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesContentService
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
}
