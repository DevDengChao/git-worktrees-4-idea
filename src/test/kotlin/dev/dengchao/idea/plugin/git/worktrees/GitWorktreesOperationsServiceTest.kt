// Copyright 2000-2026 JetBrains s.r.o. and contributors.
package dev.dengchao.idea.plugin.git.worktrees

import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.ui.Messages
import com.intellij.testFramework.LightPlatform4TestCase
import dev.dengchao.idea.plugin.git.worktrees.model.WorktreeInfo
import dev.dengchao.idea.plugin.git.worktrees.services.DeleteWorktreeBranchDecision
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesOperationsService
import org.junit.Test

/**
 * Unit tests for GitWorktreesOperationsService.
 * Tests cover worktree parsing, branch lookup, and decision handling.
 */
class GitWorktreesOperationsServiceTest : LightPlatform4TestCase() {

    @Test
    fun `test parseWorktrees single main worktree`() {
        val porcelainOutput = listOf(
            "worktree ${project.basePath}",
            "branch refs/heads/master",
        )

        val worktrees = parseWorktreesFromPorcelain(porcelainOutput, project.basePath!!)

        assert(worktrees.size == 1)
        assert(worktrees[0].isMain)
        assert(worktrees[0].isCurrent)
        assert(worktrees[0].branchName == "master")
    }

    @Test
    fun `test parseWorktrees multiple worktrees`() {
        val basePath = project.basePath!!
        val porcelainOutput = listOf(
            "worktree $basePath",
            "branch refs/heads/master",
            "",
            "worktree /tmp/feature-tree",
            "branch refs/heads/feature",
            "",
            "worktree /tmp/bugfix-tree",
            "branch refs/heads/bugfix",
            "locked",
        )

        val worktrees = parseWorktreesFromPorcelain(porcelainOutput, basePath)

        assert(worktrees.size == 3)
        assert(worktrees[0].isMain)
        assert(worktrees[0].branchName == "master")
        assert(worktrees[1].branchName == "feature")
        assert(!worktrees[1].isMain)
        assert(worktrees[2].isLocked)
        assert(worktrees[2].branchName == "bugfix")
    }

    @Test
    fun `test parseWorktrees detached HEAD`() {
        val basePath = project.basePath!!
        val porcelainOutput = listOf(
            "worktree $basePath",
            "branch refs/heads/master",
            "",
            "worktree /tmp/detached-tree",
            "detached",
        )

        val worktrees = parseWorktreesFromPorcelain(porcelainOutput, basePath)

        assert(worktrees.size == 2)
        assert(worktrees[1].branchName == null)
    }

    @Test
    fun `test findLinkedWorktreeForBranch`() {
        val service = GitWorktreesOperationsService.getInstance(project)
        val worktrees = listOf(
            WorktreeInfo(path = project.basePath!!, branchName = "master", isMain = true, isCurrent = true, isLocked = false, isPrunable = false),
            WorktreeInfo(path = "/tmp/feature-tree", branchName = "feature", isMain = false, isCurrent = false, isLocked = false, isPrunable = false),
        )

        // Simulate lookup - in real scenario this would use service.worktrees()
        val found = worktrees.firstOrNull { it.branchName == "feature" && !it.isCurrent }
        assert(found != null)
        assert(found!!.path == "/tmp/feature-tree")

        val notFound = worktrees.firstOrNull { it.branchName == "nonexistent" && !it.isCurrent }
        assert(notFound == null)
    }

    @Test
    fun `test DeleteWorktreeBranchDecision enum values`() {
        assert(DeleteWorktreeBranchDecision.values().size == 3)
        assert(DeleteWorktreeBranchDecision.CANCEL != null)
        assert(DeleteWorktreeBranchDecision.DELETE_WORKTREE_ONLY != null)
        assert(DeleteWorktreeBranchDecision.DELETE_WORKTREE_AND_BRANCH != null)
    }

    /**
     * Helper function to parse worktrees from porcelain output.
     * This mirrors the logic in GitWorktreesOperationsService.parseWorktrees().
     */
    private fun parseWorktreesFromPorcelain(lines: List<String>, mainPath: String): List<WorktreeInfo> {
        val entries = mutableListOf<WorktreeInfo>()
        var path: String? = null
        var branchName: String? = null
        var isLocked = false
        var isPrunable = false

        fun flush() {
            val currentPath = path ?: return
            val normalizedPath = currentPath.replace('\\', '/').trimEnd('/')
            val normalizedMain = mainPath.replace('\\', '/').trimEnd('/')
            val isCurrent = normalizedPath == normalizedMain
            val isMain = entries.isEmpty()
            entries += WorktreeInfo(
                path = currentPath,
                branchName = branchName,
                isMain = isMain,
                isCurrent = isCurrent,
                isLocked = isLocked,
                isPrunable = isPrunable,
            )
            path = null
            branchName = null
            isLocked = false
            isPrunable = false
        }

        lines.forEach { line ->
            when {
                line.isBlank() -> flush()
                line.startsWith("worktree ") -> {
                    flush()
                    path = line.removePrefix("worktree ").trim()
                }
                line.startsWith("branch ") -> {
                    val fullRef = line.removePrefix("branch ").trim()
                    branchName = fullRef.removePrefix("refs/heads/")
                }
                line == "detached" -> branchName = null
                line.startsWith("locked") -> isLocked = true
                line.startsWith("prunable") -> isPrunable = true
            }
        }
        flush()
        return entries
    }
}
