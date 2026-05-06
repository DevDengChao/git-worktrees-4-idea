package dev.dengchao.idea.plugin.git.worktrees

import com.intellij.testFramework.LightPlatform4TestCase
import dev.dengchao.idea.plugin.git.worktrees.ui.GitWorktreesToolWindowFactory
import org.junit.Test

class GitWorktreesToolWindowFactoryTest : LightPlatform4TestCase() {

    @Test
    fun `test tool window remains available before repositories initialize`() {
        val factory = GitWorktreesToolWindowFactory()

        assertTrue(factory.shouldBeAvailable(project))
    }
}
