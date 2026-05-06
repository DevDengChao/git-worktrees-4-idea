// Copyright 2000-2026 JetBrains s.r.o. and contributors.
package dev.dengchao.idea.plugin.git.worktrees

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.testFramework.TestActionEvent
import dev.dengchao.idea.plugin.git.worktrees.actions.CheckoutSelectedWorktreeAction
import dev.dengchao.idea.plugin.git.worktrees.actions.CheckoutWorktreeInOtherRepositoryAction
import dev.dengchao.idea.plugin.git.worktrees.actions.OpenSelectedWorktreeAction
import dev.dengchao.idea.plugin.git.worktrees.actions.RefreshGitWorktreesAction
import dev.dengchao.idea.plugin.git.worktrees.actions.RemoveSelectedWorktreeAction
import dev.dengchao.idea.plugin.git.worktrees.actions.ShowGitWorktreesToolWindowAction
import dev.dengchao.idea.plugin.git.worktrees.model.WorktreeInfo
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesOperationsService
import dev.dengchao.idea.plugin.git.worktrees.ui.GitWorktreesDataKeys
import git4idea.repo.GitRepository
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import org.junit.Test

/**
 * Unit tests for Git Worktrees Actions.
 * Tests verify action enablement conditions and data key propagation.
 */
class GitWorktreesActionsTest : LightPlatform4TestCase() {

    @Test
    fun `test toolbar actions have semantic icons`() {
        assertSame(AllIcons.Actions.Refresh, RefreshGitWorktreesAction().templatePresentation.icon)
        assertSame(AllIcons.Actions.CheckOut, CheckoutSelectedWorktreeAction().templatePresentation.icon)
        assertSame(AllIcons.Actions.MenuOpen, OpenSelectedWorktreeAction().templatePresentation.icon)
    }

    @Test
    fun `test CheckoutWorktreeInOtherRepositoryAction enabled for non-current worktree`() {
        val action = CheckoutWorktreeInOtherRepositoryAction()
        assert(action.actionUpdateThread == ActionUpdateThread.BGT)
        replaceServiceWithRepositories(gitRepository(currentBranchName = "master"))

        val worktree = WorktreeInfo(
            path = "/tmp/feature-tree",
            branchName = "feature",
            isMain = false,
            isCurrent = false,
            isLocked = false,
            isPrunable = false,
        )

        val event = actionEvent(worktree)
        action.update(event)

        assertTrue(event.presentation.isEnabledAndVisible)
    }

    @Test
    fun `test CheckoutWorktreeInOtherRepositoryAction disabled for current worktree`() {
        val action = CheckoutWorktreeInOtherRepositoryAction()
        replaceServiceWithRepositories(gitRepository(currentBranchName = "master"))

        val worktree = WorktreeInfo(
            path = project.basePath!!,
            branchName = "master",
            isMain = true,
            isCurrent = true,
            isLocked = false,
            isPrunable = false,
        )

        val event = actionEvent(worktree)
        action.update(event)

        assertFalse(event.presentation.isEnabledAndVisible)
    }

    @Test
    fun `test CheckoutWorktreeInOtherRepositoryAction disabled for detached HEAD`() {
        val action = CheckoutWorktreeInOtherRepositoryAction()
        replaceServiceWithRepositories(gitRepository(currentBranchName = "master"))

        val worktree = WorktreeInfo(
            path = "/tmp/detached-tree",
            branchName = null,  // detached HEAD
            isMain = false,
            isCurrent = false,
            isLocked = false,
            isPrunable = false,
        )

        val event = actionEvent(worktree)
        action.update(event)

        assertFalse(event.presentation.isEnabledAndVisible)
    }

    @Test
    fun `test CheckoutWorktreeInOtherRepositoryAction disabled when branches match`() {
        val action = CheckoutWorktreeInOtherRepositoryAction()
        replaceServiceWithRepositories(gitRepository(currentBranchName = "feature"))

        val worktree = WorktreeInfo(
            path = "/tmp/feature-tree",
            branchName = "feature",
            isMain = false,
            isCurrent = false,
            isLocked = false,
            isPrunable = false,
        )

        val event = actionEvent(worktree)
        action.update(event)

        assertFalse(event.presentation.isEnabledAndVisible)
    }

    @Test
    fun `test CheckoutWorktreeInOtherRepositoryAction disabled for sibling repositories`() {
        val action = CheckoutWorktreeInOtherRepositoryAction()
        replaceServiceWithRepositories(
            gitRepository(rootPath = "/project/one", currentBranchName = "master"),
            gitRepository(rootPath = "/project/two", currentBranchName = "master"),
        )

        val worktree = WorktreeInfo(
            path = "/tmp/feature-tree",
            branchName = "feature",
            isMain = false,
            isCurrent = false,
            isLocked = false,
            isPrunable = false,
        )

        val event = actionEvent(worktree)
        action.update(event)

        assertFalse(event.presentation.isEnabledAndVisible)
    }

    @Test
    fun `test ShowGitWorktreesToolWindowAction visible before repositories initialize`() {
        val action = ShowGitWorktreesToolWindowAction()
        replaceServiceWithRepositories()

        val event = TestActionEvent.createTestEvent(CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
            sink[PlatformDataKeys.PROJECT] = project
        })
        action.update(event)

        assertTrue(event.presentation.isEnabledAndVisible)
    }

    @Test
    fun `test ShowGitWorktreesToolWindowAction visible with repositories`() {
        val action = ShowGitWorktreesToolWindowAction()
        replaceServiceWithRepositories(gitRepository(currentBranchName = "master"))

        val event = TestActionEvent.createTestEvent(CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
            sink[PlatformDataKeys.PROJECT] = project
        })
        action.update(event)

        assertTrue(event.presentation.isEnabledAndVisible)
    }

    @Test
    fun `test RemoveSelectedWorktreeAction enabled for linked worktree with repository context`() {
        val action = RemoveSelectedWorktreeAction()
        assert(action.actionUpdateThread == ActionUpdateThread.BGT)
        val repository = gitRepository(currentBranchName = "master")
        val worktree = WorktreeInfo(
            path = "/tmp/feature-tree",
            branchName = "feature",
            isMain = false,
            isCurrent = false,
            isLocked = false,
            isPrunable = false,
        )

        val event = actionEvent(worktree, repository)
        action.update(event)

        assertTrue(event.presentation.isEnabledAndVisible)
    }

    @Test
    fun `test RemoveSelectedWorktreeAction disabled without repository context`() {
        val action = RemoveSelectedWorktreeAction()
        val worktree = WorktreeInfo(
            path = "/tmp/feature-tree",
            branchName = "feature",
            isMain = false,
            isCurrent = false,
            isLocked = false,
            isPrunable = false,
        )

        val event = actionEvent(worktree)
        action.update(event)

        assertFalse(event.presentation.isEnabledAndVisible)
    }

    @Test
    fun `test RemoveSelectedWorktreeAction disabled for main worktree`() {
        val action = RemoveSelectedWorktreeAction()
        val repository = gitRepository(currentBranchName = "master")
        val worktree = WorktreeInfo(
            path = "/project",
            branchName = "master",
            isMain = true,
            isCurrent = true,
            isLocked = false,
            isPrunable = false,
        )

        val event = actionEvent(worktree, repository)
        action.update(event)

        assertFalse(event.presentation.isEnabledAndVisible)
    }

    @Test
    fun `test WorktreeInfo data class`() {
        val worktree = WorktreeInfo(
            path = "/tmp/feature-tree",
            branchName = "feature",
            isMain = false,
            isCurrent = false,
            isLocked = true,
            isPrunable = false,
        )

        assert(worktree.path == "/tmp/feature-tree")
        assert(worktree.branchName == "feature")
        assert(!worktree.isMain)
        assert(!worktree.isCurrent)
        assert(worktree.isLocked)
        assert(!worktree.isPrunable)
        assert(worktree.name == "feature-tree")  // name derived from path
    }

    @Test
    fun `test WorktreeInfo name extraction from path`() {
        val worktree1 = WorktreeInfo(path = "/tmp/feature-tree", branchName = "feature", isMain = false, isCurrent = false, isLocked = false, isPrunable = false)
        assert(worktree1.name == "feature-tree")

        val worktree2 = WorktreeInfo(path = "C:\\Users\\admin\\worktrees\\bugfix-123", branchName = "bugfix-123", isMain = false, isCurrent = false, isLocked = false, isPrunable = false)
        assert(worktree2.name == "bugfix-123")
    }

    private fun actionEvent(
        worktree: WorktreeInfo,
        repository: GitRepository? = null,
    ): com.intellij.openapi.actionSystem.AnActionEvent {
        return TestActionEvent.createTestEvent(CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
            sink[PlatformDataKeys.PROJECT] = project
            repository?.let { sink[GitWorktreesDataKeys.CURRENT_REPOSITORY] = it }
            sink[GitWorktreesDataKeys.SELECTED_WORKTREE] = worktree
        })
    }

    private fun replaceServiceWithRepositories(vararg repositories: GitRepository) {
        GitWorktreesOperationsService.getInstance(project).overrideProvidersForTests(
            repositoriesProvider = { repositories.toList() },
            parentDisposable = testRootDisposable,
        )
    }

    private fun gitRepository(
        rootPath: String = "/project",
        currentBranchName: String?,
    ): GitRepository {
        val root = TestVirtualFile(rootPath)
        val handler = InvocationHandler { proxy, method, args ->
            when (method.name) {
                "getProject" -> this.project
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

        override fun equals(other: Any?): Boolean {
            return other is VirtualFile && other.path == filePath
        }

        override fun hashCode(): Int = filePath.hashCode()
    }
}
