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
import dev.dengchao.idea.plugin.git.worktrees.actions.ToggleSelectedRepositoryAction
import dev.dengchao.idea.plugin.git.worktrees.model.WorktreeInfo
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesOperationsService
import dev.dengchao.idea.plugin.git.worktrees.ui.GitWorktreesDataKeys
import dev.dengchao.idea.plugin.git.worktrees.ui.GitWorktreesPanel
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

        assertTrue(event.presentation.isVisible)
        assertFalse(event.presentation.isEnabled)
        assertEquals(
            Gw4iBundle.message("action.GitWorktrees.CheckoutInOtherRepo.disabled.current.worktree"),
            event.presentation.description,
        )
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

        assertTrue(event.presentation.isVisible)
        assertFalse(event.presentation.isEnabled)
        assertEquals(
            Gw4iBundle.message("action.GitWorktrees.CheckoutInOtherRepo.disabled.detached"),
            event.presentation.description,
        )
    }

    @Test
    fun `test CheckoutWorktreeInOtherRepositoryAction enabled when branches match another worktree`() {
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

        assertTrue(event.presentation.isEnabledAndVisible)
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

        assertTrue(event.presentation.isVisible)
        assertFalse(event.presentation.isEnabled)
        assertEquals(
            Gw4iBundle.message("action.GitWorktrees.CheckoutInOtherRepo.disabled.multiple.repositories"),
            event.presentation.description,
        )
    }

    @Test
    fun `test CheckoutWorktreeInOtherRepositoryAction disabled for multiple selected worktrees`() {
        val action = CheckoutWorktreeInOtherRepositoryAction()
        replaceServiceWithRepositories(gitRepository(currentBranchName = "master"))

        val event = multiSelectionActionEvent(
            selectedWorktree(branchName = "feature"),
            selectedWorktree(branchName = "bugfix"),
        )
        action.update(event)

        assertFalse(event.presentation.isEnabledAndVisible)
    }

    @Test
    fun `test OpenSelectedWorktreeAction disabled for multiple selected worktrees`() {
        val action = OpenSelectedWorktreeAction()

        val event = multiSelectionActionEvent(
            selectedWorktree(branchName = "feature"),
            selectedWorktree(branchName = "bugfix"),
        )
        action.update(event)

        assertFalse(event.presentation.isEnabledAndVisible)
    }

    @Test
    fun `test CheckoutSelectedWorktreeAction disabled for multiple selected worktrees`() {
        val action = CheckoutSelectedWorktreeAction()

        val event = multiSelectionActionEvent(
            selectedWorktree(branchName = "feature"),
            selectedWorktree(branchName = "bugfix"),
        )
        action.update(event)

        assertFalse(event.presentation.isEnabledAndVisible)
    }

    @Test
    fun `test CheckoutSelectedWorktreeAction enabled when same branch is selected in another worktree`() {
        val action = CheckoutSelectedWorktreeAction()
        val repository = gitRepository(currentBranchName = "feature")
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
    fun `test CheckoutSelectedWorktreeAction visible with disabled hint for detached HEAD`() {
        val action = CheckoutSelectedWorktreeAction()
        val repository = gitRepository(currentBranchName = "master")
        val worktree = WorktreeInfo(
            path = "/tmp/detached-tree",
            branchName = null,
            isMain = false,
            isCurrent = false,
            isLocked = false,
            isPrunable = false,
        )

        val event = actionEvent(worktree, repository)
        action.update(event)

        assertTrue(event.presentation.isVisible)
        assertFalse(event.presentation.isEnabled)
        assertEquals(
            Gw4iBundle.message("action.GitWorktrees.Checkout.disabled.detached"),
            event.presentation.description,
        )
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
    fun `test ShowGitWorktreesToolWindowAction opens Worktrees tab in Git tool window`() {
        val action = ShowGitWorktreesToolWindowAction()
        var openedProject: Project? = null
        ShowGitWorktreesToolWindowAction.overrideOpenWorktreesTabForTests(
            opener = { openedProject = it },
            parentDisposable = testRootDisposable,
        )

        action.actionPerformed(TestActionEvent.createTestEvent(CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
            sink[PlatformDataKeys.PROJECT] = project
        }))

        assertSame(project, openedProject)
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
    fun `test RemoveSelectedWorktreeAction enabled for multiple selected worktrees with non-main item`() {
        val action = RemoveSelectedWorktreeAction()

        val event = multiSelectionActionEvent(
            selectedWorktree(path = "/project", branchName = "master", isMain = true, isCurrent = true),
            selectedWorktree(path = "/tmp/feature-tree", branchName = "feature"),
        )
        action.update(event)

        assertTrue(event.presentation.isEnabledAndVisible)
    }

    @Test
    fun `test RemoveSelectedWorktreeAction enabled when repository row and one worktree are selected`() {
        val action = RemoveSelectedWorktreeAction()

        val event = multiSelectionActionEvent(
            selectedWorktree(path = "/tmp/feature-tree", branchName = "feature"),
        )
        action.update(event)

        assertTrue(event.presentation.isEnabledAndVisible)
    }

    @Test
    fun `test RemoveSelectedWorktreeAction disabled when multiple selection has only main worktrees`() {
        val action = RemoveSelectedWorktreeAction()

        val event = multiSelectionActionEvent(
            selectedWorktree(path = "/project", branchName = "master", isMain = true, isCurrent = true),
        )
        action.update(event)

        assertFalse(event.presentation.isEnabledAndVisible)
    }

    @Test
    fun `test ToggleSelectedRepositoryAction visible for single repository row`() {
        val action = ToggleSelectedRepositoryAction()
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        val panel = panelWithWorktrees(repository, emptyList())
        panel.selectRowForTests(0)

        val event = panelActionEvent(panel)
        action.update(event)

        assertTrue(event.presentation.isEnabledAndVisible)
        assertEquals(Gw4iBundle.message("action.GitWorktrees.ToggleRepositoryCollapsed.collapse.text"), event.presentation.text)
    }

    @Test
    fun `test ToggleSelectedRepositoryAction toggles text and repository state`() {
        val action = ToggleSelectedRepositoryAction()
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        val worktree = WorktreeInfo(path = "/project/root/feature-tree", branchName = "feature", isMain = false, isCurrent = false, isLocked = false, isPrunable = false)
        val panel = panelWithWorktrees(repository, listOf(worktree))
        panel.selectRowForTests(0)

        action.actionPerformed(panelActionEvent(panel))

        assertEquals(listOf("root"), panel.visibleRowLabelsForTests())

        val event = panelActionEvent(panel)
        action.update(event)

        assertTrue(event.presentation.isEnabledAndVisible)
        assertEquals(Gw4iBundle.message("action.GitWorktrees.ToggleRepositoryCollapsed.expand.text"), event.presentation.text)
    }

    @Test
    fun `test ToggleSelectedRepositoryAction hidden for worktree and multiple selections`() {
        val action = ToggleSelectedRepositoryAction()
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        val feature = WorktreeInfo(path = "/project/root/feature-tree", branchName = "feature", isMain = false, isCurrent = false, isLocked = false, isPrunable = false)
        val bugfix = WorktreeInfo(path = "/project/root/bugfix-tree", branchName = "bugfix", isMain = false, isCurrent = false, isLocked = false, isPrunable = false)
        val panel = panelWithWorktrees(repository, listOf(feature, bugfix))

        panel.selectRowForTests(1)
        val worktreeEvent = panelActionEvent(panel)
        action.update(worktreeEvent)
        assertFalse(worktreeEvent.presentation.isEnabledAndVisible)

        panel.selectRowsForTests(1, 2)
        val multiWorktreeEvent = panelActionEvent(panel)
        action.update(multiWorktreeEvent)
        assertFalse(multiWorktreeEvent.presentation.isEnabledAndVisible)

        panel.selectRowsForTests(0, 1)
        val repositoryAndWorktreeEvent = panelActionEvent(panel)
        action.update(repositoryAndWorktreeEvent)
        assertFalse(repositoryAndWorktreeEvent.presentation.isEnabledAndVisible)
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

    private fun multiSelectionActionEvent(
        vararg selected: GitWorktreesDataKeys.SelectedGitWorktree,
    ): com.intellij.openapi.actionSystem.AnActionEvent {
        return TestActionEvent.createTestEvent(CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
            sink[PlatformDataKeys.PROJECT] = project
            sink[GitWorktreesDataKeys.SELECTED_WORKTREES] = selected.toList()
        })
    }

    private fun panelActionEvent(panel: GitWorktreesPanel): com.intellij.openapi.actionSystem.AnActionEvent {
        return TestActionEvent.createTestEvent(CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
            sink[PlatformDataKeys.PROJECT] = project
            sink[GitWorktreesDataKeys.PANEL] = panel
            panel.selectedRepository()?.let { sink[GitWorktreesDataKeys.CURRENT_REPOSITORY] = it }
            panel.selectedWorktree()?.let { sink[GitWorktreesDataKeys.SELECTED_WORKTREE] = it }
            sink[GitWorktreesDataKeys.SELECTED_WORKTREES] = panel.selectedWorktrees()
        })
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
        return GitWorktreesPanel(project).apply {
            reloadSynchronouslyForTests()
        }
    }

    private fun selectedWorktree(
        path: String = "/tmp/${System.nanoTime()}",
        branchName: String?,
        isMain: Boolean = false,
        isCurrent: Boolean = false,
        repository: GitRepository = gitRepository(currentBranchName = "master"),
    ): GitWorktreesDataKeys.SelectedGitWorktree {
        return GitWorktreesDataKeys.SelectedGitWorktree(
            repository = repository,
            worktree = WorktreeInfo(
                path = path,
                branchName = branchName,
                isMain = isMain,
                isCurrent = isCurrent,
                isLocked = false,
                isPrunable = false,
            ),
        )
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
