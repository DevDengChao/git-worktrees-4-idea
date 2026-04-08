package dev.dengchao.idea.plugin.git.worktrees

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import git4idea.commands.Git

class GitWorktreesPluginTest : BasePlatformTestCase() {

    fun testGitServiceAvailable() {
        assertNotNull(Git.getInstance())
    }
}
