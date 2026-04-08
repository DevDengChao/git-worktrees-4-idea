package dev.dengchao.idea.plugin.git.worktrees

import com.intellij.execution.process.ProcessOutputTypes
import dev.dengchao.idea.plugin.git.worktrees.commands.GitBranchAlreadyUsedByWorktreeDetector
import dev.dengchao.idea.plugin.git.worktrees.commands.GitBranchDeleteBlockedByWorktreeDetector
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test

class WorktreeDetectorsTest {

    @Test
    fun `detect checkout branch used by worktree old format`() {
        val detector = GitBranchAlreadyUsedByWorktreeDetector()

        detector.onLineAvailable(
            "fatal: 'feature/test' is already checked out at '/tmp/feature-tree'",
            ProcessOutputTypes.STDERR,
        )

        assertTrue(detector.isDetected)
        assertEquals("feature/test", detector.branchName)
        assertEquals("/tmp/feature-tree", detector.worktreePath)
    }

    @Test
    fun `detect checkout branch used by worktree new format`() {
        val detector = GitBranchAlreadyUsedByWorktreeDetector()

        detector.onLineAvailable(
            "fatal: 'feature/test' is already used by worktree at '/tmp/feature-tree'",
            ProcessOutputTypes.STDERR,
        )

        assertTrue(detector.isDetected)
        assertEquals("feature/test", detector.branchName)
        assertEquals("/tmp/feature-tree", detector.worktreePath)
    }

    @Test
    fun `detect delete branch blocked by worktree old format`() {
        val detector = GitBranchDeleteBlockedByWorktreeDetector()

        detector.onLineAvailable(
            "error: Cannot delete branch 'feature/test' checked out at '/tmp/feature-tree'",
            ProcessOutputTypes.STDERR,
        )

        assertTrue(detector.isDetected)
        assertEquals("feature/test", detector.branchName)
        assertEquals("/tmp/feature-tree", detector.worktreePath)
    }

    @Test
    fun `detect delete branch blocked by worktree new format`() {
        val detector = GitBranchDeleteBlockedByWorktreeDetector()

        detector.onLineAvailable(
            "error: cannot delete branch 'feature/test' used by worktree at '/tmp/feature-tree'",
            ProcessOutputTypes.STDERR,
        )

        assertTrue(detector.isDetected)
        assertEquals("feature/test", detector.branchName)
        assertEquals("/tmp/feature-tree", detector.worktreePath)
    }
}
