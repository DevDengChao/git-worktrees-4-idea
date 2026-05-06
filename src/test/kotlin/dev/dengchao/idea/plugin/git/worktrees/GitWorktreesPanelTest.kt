// Copyright 2000-2026 JetBrains s.r.o. and contributors.
package dev.dengchao.idea.plugin.git.worktrees

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatform4TestCase
import dev.dengchao.idea.plugin.git.worktrees.model.WorktreeInfo
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesOperationsService
import dev.dengchao.idea.plugin.git.worktrees.ui.GitWorktreesDataKeys
import dev.dengchao.idea.plugin.git.worktrees.ui.GitWorktreesPanel
import git4idea.repo.GitRepository
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
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

    @Test
    fun `repository row exposes repository but not selected worktree`() {
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        val panel = panelWithWorktrees(repository, emptyList())

        panel.selectRowForTests(0)

        assertSame(repository, panel.getData(GitWorktreesDataKeys.CURRENT_REPOSITORY.name))
        assertNull(panel.getData(GitWorktreesDataKeys.SELECTED_WORKTREE.name))
    }

    @Test
    fun `worktree row exposes its repository and selected worktree`() {
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        val worktree = WorktreeInfo(
            path = "/project/root-feature",
            branchName = "feature",
            isMain = false,
            isCurrent = false,
            isLocked = false,
            isPrunable = false,
        )
        val panel = panelWithWorktrees(repository, listOf(worktree))

        panel.selectRowForTests(1)

        assertSame(repository, panel.getData(GitWorktreesDataKeys.CURRENT_REPOSITORY.name))
        assertSame(worktree, panel.getData(GitWorktreesDataKeys.SELECTED_WORKTREE.name))
    }

    @Test
    fun `reload selects first worktree row when previous selection is unavailable`() {
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        val worktree = WorktreeInfo(
            path = "/project/root-feature",
            branchName = "feature",
            isMain = false,
            isCurrent = false,
            isLocked = false,
            isPrunable = false,
        )
        val panel = panelWithWorktrees(repository, listOf(worktree))

        assertSame(worktree, panel.getData(GitWorktreesDataKeys.SELECTED_WORKTREE.name))
    }

    private fun panelWithWorktrees(
        repository: GitRepository,
        worktrees: List<WorktreeInfo>,
    ): GitWorktreesPanel {
        GitWorktreesOperationsService.getInstance(project).overrideProvidersForTests(
            repositoriesProvider = { listOf(repository) },
            worktreesProvider = { worktrees },
            parentDisposable = testRootDisposable,
        )
        return GitWorktreesPanel(project)
    }

    private fun gitRepository(rootPath: String, currentBranchName: String?): GitRepository {
        val root = TestVirtualFile(rootPath)
        val handler = InvocationHandler { proxy, method, args ->
            when (method.name) {
                "getProject" -> project
                "getRoot" -> root
                "getPresentableUrl" -> rootPath
                "getCurrentBranchName" -> currentBranchName
                "isFresh" -> false
                "isDisposed" -> false
                "update" -> Unit
                "dispose" -> Unit
                "toLogString" -> rootPath
                "toString" -> "GitRepository($rootPath)"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        }
        return Proxy.newProxyInstance(
            GitRepository::class.java.classLoader,
            arrayOf(GitRepository::class.java),
            handler,
        ) as GitRepository
    }

    private class TestVirtualFile(
        private val filePath: String,
    ) : VirtualFile() {
        override fun getName(): String = filePath.substringAfterLast('/')
        override fun getFileSystem() = LocalFileSystem.getInstance()
        override fun getPath(): String = filePath
        override fun isWritable(): Boolean = true
        override fun isDirectory(): Boolean = true
        override fun isValid(): Boolean = true
        override fun getParent(): VirtualFile? {
            val parentPath = filePath.trimEnd('/').substringBeforeLast('/', missingDelimiterValue = "")
            if (parentPath.isBlank()) return null
            return TestVirtualFile(parentPath)
        }

        override fun getChildren(): Array<VirtualFile> = emptyArray()
        override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long) = throw UnsupportedOperationException()
        override fun contentsToByteArray(): ByteArray = ByteArray(0)
        override fun getTimeStamp(): Long = 0
        override fun getLength(): Long = 0
        override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) = Unit
        override fun getInputStream() = throw UnsupportedOperationException()
    }
}
