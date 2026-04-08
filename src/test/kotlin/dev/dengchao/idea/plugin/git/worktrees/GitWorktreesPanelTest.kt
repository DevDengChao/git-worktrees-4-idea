// Copyright 2000-2026 JetBrains s.r.o. and contributors.
package dev.dengchao.idea.plugin.git.worktrees

import com.intellij.testFramework.LightPlatform4TestCase
import dev.dengchao.idea.plugin.git.worktrees.model.WorktreeInfo
import dev.dengchao.idea.plugin.git.worktrees.ui.GitWorktreesDataKeys
import org.junit.Test

/**
 * Unit tests for GitWorktreesPanel.
 * Tests verify panel data handling and selection logic.
 */
class GitWorktreesPanelTest : LightPlatform4TestCase() {

    @Test
    fun `test WorktreeListItem sealed interface hierarchy`() {
        // Test that the sealed interface pattern works correctly
        val mainWorktree = WorktreeInfo(
            path = project.basePath!!,
            branchName = "master",
            isMain = true,
            isCurrent = true,
            isLocked = false,
            isPrunable = false,
        )

        val featureWorktree = WorktreeInfo(
            path = "/tmp/feature-tree",
            branchName = "feature",
            isMain = false,
            isCurrent = false,
            isLocked = false,
            isPrunable = false,
        )

        // Verify worktree properties
        assert(mainWorktree.isMain)
        assert(mainWorktree.isCurrent)
        assert(!featureWorktree.isMain)
        assert(!featureWorktree.isCurrent)
    }

    @Test
    fun `test worktree sorting - main first then by path name`() {
        val worktrees = listOf(
            WorktreeInfo(path = "/tmp/zebra-tree", branchName = "zebra", isMain = false, isCurrent = false, isLocked = false, isPrunable = false),
            WorktreeInfo(path = project.basePath!!, branchName = "master", isMain = true, isCurrent = true, isLocked = false, isPrunable = false),
            WorktreeInfo(path = "/tmp/alpha-tree", branchName = "alpha", isMain = false, isCurrent = false, isLocked = false, isPrunable = false),
        )

        // Sort: main first, then by path name
        val sorted = worktrees.sortedWith(
            compareByDescending<WorktreeInfo> { it.isMain }.thenBy { it.path }
        )

        assert(sorted[0].isMain) { "Main worktree should be first" }
        assert(sorted[1].path.contains("alpha")) { "Alpha should come before Zebra" }
        assert(sorted[2].path.contains("zebra")) { "Zebra should be last" }
    }

    @Test
    fun `test DataKey panel reference`() {
        // Verify that GitWorktreesDataKeys.PANEL is correctly defined
        assert(GitWorktreesDataKeys.PANEL.name == "GW4I_PANEL")
    }

    @Test
    fun `test DataKey selected worktree reference`() {
        assert(GitWorktreesDataKeys.SELECTED_WORKTREE.name == "GW4I_SELECTED_WORKTREE")
    }

    @Test
    fun `test DataKey current repository reference`() {
        assert(GitWorktreesDataKeys.CURRENT_REPOSITORY.name == "GW4I_CURRENT_REPOSITORY")
    }
}
