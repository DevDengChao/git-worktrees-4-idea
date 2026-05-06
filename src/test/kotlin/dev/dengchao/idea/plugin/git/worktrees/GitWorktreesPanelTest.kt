// Copyright 2000-2026 JetBrains s.r.o. and contributors.
package dev.dengchao.idea.plugin.git.worktrees

import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBTextField
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatform4TestCase
import dev.dengchao.idea.plugin.git.worktrees.model.WorktreeInfo
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesOperationsService
import dev.dengchao.idea.plugin.git.worktrees.ui.GitWorktreesDataKeys
import dev.dengchao.idea.plugin.git.worktrees.ui.GitWorktreesPanel
import git4idea.repo.GitRepository
import java.awt.Component
import java.awt.Container
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.SwingUtilities
import org.junit.Test

/**
 * Unit tests for GitWorktreesPanel.
 * Tests verify panel data handling and selection logic.
 */
class GitWorktreesPanelTest : LightPlatform4TestCase() {

    @Test
    fun `test WorktreeInfo status flags`() {
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
    fun `table shows repository group and three worktree columns`() {
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        val worktree = WorktreeInfo(
            path = "/project/root-feature",
            branchName = "feature/login",
            isMain = false,
            isCurrent = false,
            isLocked = false,
            isPrunable = false,
        )
        val detachedWorktree = WorktreeInfo(
            path = "/project/root-detached",
            branchName = null,
            isMain = false,
            isCurrent = false,
            isLocked = false,
            isPrunable = false,
        )

        val panel = panelWithWorktrees(repository, listOf(worktree, detachedWorktree))

        assertEquals(listOf("Worktree", "Branch", "Location"), panel.columnNamesForTests())
        assertEquals(3, panel.columnCountForTests())
        assertTrue(panel.isRepositoryRowForTests(0))
        assertEquals("root-feature", panel.tableValueForTests(1, 0))
        assertEquals("feature/login", panel.tableValueForTests(1, 1))
        assertEquals("/project/root-feature", panel.tableValueForTests(1, 2))
        assertEquals("root-detached", panel.tableValueForTests(2, 0))
        assertEquals("detached", panel.tableValueForTests(2, 1))
    }

    @Test
    fun `header controls live in JTable header`() {
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        val panel = panelWithWorktrees(repository, emptyList())
        val table = panel.descendantsForTests().filterIsInstance<JTable>().single()
        val scrollPane = panel.descendantsForTests().filterIsInstance<JScrollPane>().single()
        val columnHeaderView = scrollPane.columnHeader?.view
        val sortButtons = panel.descendantsForTests()
            .filterIsInstance<JButton>()
            .filter { it.toolTipText == Gw4iBundle.message("toolwindow.GitWorktrees.sort.button.tooltip") }
        val filterFields = panel.descendantsForTests().filterIsInstance<JBTextField>()

        assertSame(table.tableHeader, columnHeaderView)
        assertEquals(3, sortButtons.size)
        assertEquals(3, filterFields.size)
        sortButtons.forEach { button ->
            assertTrue(SwingUtilities.isDescendingFrom(button, table.tableHeader))
        }
        filterFields.forEach { field ->
            assertTrue(SwingUtilities.isDescendingFrom(field, table.tableHeader))
        }
    }

    @Test
    fun `header cells place filter between title and icon sort button`() {
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        val panel = panelWithWorktrees(repository, emptyList())
        val headerControls = panel.headerControlsForTests()

        assertEquals("Filter worktree", headerControls.filterFields.getValue(GitWorktreesPanel.Column.WORKTREE_ID).emptyText.text)
        GitWorktreesPanel.Column.entries.forEach { column ->
            val title = headerControls.titleLabels.getValue(column)
            val filter = headerControls.filterFields.getValue(column)
            val sortButton = headerControls.sortButtons.getValue(column)
            val headerCell = filter.parent
            val headerComponents = headerCell.components.toList()

            assertSame(headerCell, title.parent)
            assertSame(headerCell, sortButton.parent)
            assertTrue(headerComponents.indexOf(title) < headerComponents.indexOf(filter))
            assertTrue(headerComponents.indexOf(filter) < headerComponents.indexOf(sortButton))
            assertEquals("", sortButton.text)
            assertNotNull(sortButton.icon)
        }
    }

    @Test
    fun `header filter fields and sort buttons drive the visible rows`() {
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        val alpha = WorktreeInfo(path = "/project/alpha-tree", branchName = "feature/login", isMain = false, isCurrent = false, isLocked = false, isPrunable = false)
        val beta = WorktreeInfo(path = "/project/beta-tree", branchName = "feature/report", isMain = false, isCurrent = false, isLocked = false, isPrunable = false)
        val panel = panelWithWorktrees(repository, listOf(beta, alpha))
        val headerControls = panel.headerControlsForTests()
        val sortButton = headerControls.sortButtons.getValue(GitWorktreesPanel.Column.WORKTREE_ID)
        val disabledIcon = sortButton.icon

        headerControls.filterFields.getValue(GitWorktreesPanel.Column.BRANCH_NAME).text = "LOGIN"

        assertEquals(listOf("root", "alpha-tree"), panel.visibleRowLabelsForTests())

        headerControls.filterFields.getValue(GitWorktreesPanel.Column.BRANCH_NAME).text = ""
        sortButton.doClick()

        assertEquals("", sortButton.text)
        assertNotSame(disabledIcon, sortButton.icon)
        assertEquals(listOf("root", "alpha-tree", "beta-tree"), panel.visibleRowLabelsForTests())
    }

    @Test
    fun `column filters match case-insensitive contains with AND semantics`() {
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        val alpha = WorktreeInfo(path = "/project/alpha-tree", branchName = "feature/login", isMain = false, isCurrent = false, isLocked = false, isPrunable = false)
        val beta = WorktreeInfo(path = "/project/beta-tree", branchName = "feature/report", isMain = false, isCurrent = false, isLocked = false, isPrunable = false)
        val release = WorktreeInfo(path = "/release/alpha-prod", branchName = "release/login", isMain = false, isCurrent = false, isLocked = false, isPrunable = false)
        val panel = panelWithWorktrees(repository, listOf(alpha, beta, release))

        panel.setFilterForTests(GitWorktreesPanel.Column.WORKTREE_ID, "ALPHA")
        panel.setFilterForTests(GitWorktreesPanel.Column.BRANCH_NAME, "login")
        panel.setFilterForTests(GitWorktreesPanel.Column.LOCATION, "/project")

        assertEquals(listOf("root", "alpha-tree"), panel.visibleRowLabelsForTests())
        assertSame(alpha, panel.getData(GitWorktreesDataKeys.SELECTED_WORKTREE.name))
    }

    @Test
    fun `filter hides repository groups without matching worktrees`() {
        val firstRepository = gitRepository(rootPath = "/project/first", currentBranchName = "master")
        val secondRepository = gitRepository(rootPath = "/project/second", currentBranchName = "master")
        val firstWorktree = WorktreeInfo(path = "/project/first/alpha-tree", branchName = "feature/alpha", isMain = false, isCurrent = false, isLocked = false, isPrunable = false)
        val secondWorktree = WorktreeInfo(path = "/project/second/beta-tree", branchName = "feature/beta", isMain = false, isCurrent = false, isLocked = false, isPrunable = false)
        val panel = panelWithWorktrees(
            mapOf(
                firstRepository to listOf(firstWorktree),
                secondRepository to listOf(secondWorktree),
            ),
        )

        panel.setFilterForTests(GitWorktreesPanel.Column.BRANCH_NAME, "beta")

        assertEquals(listOf("second", "beta-tree"), panel.visibleRowLabelsForTests())
    }

    @Test
    fun `multi column sorting applies within repository groups by button priority`() {
        val firstRepository = gitRepository(rootPath = "/project/first", currentBranchName = "master")
        val secondRepository = gitRepository(rootPath = "/project/second", currentBranchName = "master")
        val firstWorktrees = listOf(
            WorktreeInfo(path = "/project/first/worktree-c", branchName = "feature/zeta", isMain = false, isCurrent = false, isLocked = false, isPrunable = false),
            WorktreeInfo(path = "/project/first/worktree-a", branchName = "feature/zeta", isMain = false, isCurrent = false, isLocked = false, isPrunable = false),
            WorktreeInfo(path = "/project/first/worktree-b", branchName = "feature/alpha", isMain = false, isCurrent = false, isLocked = false, isPrunable = false),
        )
        val secondWorktrees = listOf(
            WorktreeInfo(path = "/project/second/worktree-z", branchName = "feature/beta", isMain = false, isCurrent = false, isLocked = false, isPrunable = false),
            WorktreeInfo(path = "/project/second/worktree-y", branchName = "feature/alpha", isMain = false, isCurrent = false, isLocked = false, isPrunable = false),
        )
        val panel = panelWithWorktrees(
            mapOf(
                firstRepository to firstWorktrees,
                secondRepository to secondWorktrees,
            ),
        )

        panel.toggleSortForTests(GitWorktreesPanel.Column.BRANCH_NAME)
        panel.toggleSortForTests(GitWorktreesPanel.Column.WORKTREE_ID)

        assertEquals(
            listOf("first", "worktree-b", "worktree-a", "worktree-c", "second", "worktree-y", "worktree-z"),
            panel.visibleRowLabelsForTests(),
        )
    }

    @Test
    fun `sort button cycles ascending descending and disabled`() {
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        val worktrees = listOf(
            WorktreeInfo(path = "/project/root/a-tree", branchName = "feature/a", isMain = false, isCurrent = false, isLocked = false, isPrunable = false),
            WorktreeInfo(path = "/project/root/b-tree", branchName = "feature/b", isMain = false, isCurrent = false, isLocked = false, isPrunable = false),
        )
        val panel = panelWithWorktrees(repository, worktrees)

        panel.toggleSortForTests(GitWorktreesPanel.Column.WORKTREE_ID)
        assertEquals(listOf("root", "a-tree", "b-tree"), panel.visibleRowLabelsForTests())

        panel.toggleSortForTests(GitWorktreesPanel.Column.WORKTREE_ID)
        assertEquals(listOf("root", "b-tree", "a-tree"), panel.visibleRowLabelsForTests())

        panel.toggleSortForTests(GitWorktreesPanel.Column.WORKTREE_ID)
        assertEquals(listOf("root", "a-tree", "b-tree"), panel.visibleRowLabelsForTests())
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
    fun `test DataKey selected worktrees reference`() {
        assert(GitWorktreesDataKeys.SELECTED_WORKTREES.name == "GW4I_SELECTED_WORKTREES")
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
    fun `multiple selected worktree rows expose selected worktrees only`() {
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        val featureWorktree = WorktreeInfo(
            path = "/project/root-feature",
            branchName = "feature",
            isMain = false,
            isCurrent = false,
            isLocked = false,
            isPrunable = false,
        )
        val bugfixWorktree = WorktreeInfo(
            path = "/project/root-bugfix",
            branchName = "bugfix",
            isMain = false,
            isCurrent = false,
            isLocked = false,
            isPrunable = false,
        )
        val panel = panelWithWorktrees(repository, listOf(featureWorktree, bugfixWorktree))

        panel.selectRowsForTests(1, 2)

        @Suppress("UNCHECKED_CAST")
        val selected = panel.getData(GitWorktreesDataKeys.SELECTED_WORKTREES.name) as List<GitWorktreesDataKeys.SelectedGitWorktree>
        assertEquals(listOf(featureWorktree, bugfixWorktree), selected.map { it.worktree })
        assertTrue(selected.all { it.repository === repository })
        assertNull(panel.getData(GitWorktreesDataKeys.CURRENT_REPOSITORY.name))
        assertNull(panel.getData(GitWorktreesDataKeys.SELECTED_WORKTREE.name))
    }

    @Test
    fun `selected worktrees ignores repository rows`() {
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

        panel.selectRowsForTests(0, 1)

        @Suppress("UNCHECKED_CAST")
        val selected = panel.getData(GitWorktreesDataKeys.SELECTED_WORKTREES.name) as List<GitWorktreesDataKeys.SelectedGitWorktree>
        assertEquals(listOf(worktree), selected.map { it.worktree })
        assertNull(panel.getData(GitWorktreesDataKeys.CURRENT_REPOSITORY.name))
        assertNull(panel.getData(GitWorktreesDataKeys.SELECTED_WORKTREE.name))
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

    @Test
    fun `reload restores multiple selected worktree rows by path`() {
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        val featureWorktree = WorktreeInfo(
            path = "/project/root-feature",
            branchName = "feature",
            isMain = false,
            isCurrent = false,
            isLocked = false,
            isPrunable = false,
        )
        val bugfixWorktree = WorktreeInfo(
            path = "/project/root-bugfix",
            branchName = "bugfix",
            isMain = false,
            isCurrent = false,
            isLocked = false,
            isPrunable = false,
        )
        val panel = panelWithWorktrees(repository, listOf(featureWorktree, bugfixWorktree))
        panel.selectRowsForTests(1, 2)

        panel.reloadSynchronouslyForTests()

        @Suppress("UNCHECKED_CAST")
        val selected = panel.getData(GitWorktreesDataKeys.SELECTED_WORKTREES.name) as List<GitWorktreesDataKeys.SelectedGitWorktree>
        assertEquals(listOf(featureWorktree, bugfixWorktree), selected.map { it.worktree })
    }

    @Test
    fun `constructor loads worktrees outside EDT`() {
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        var loadedOnEdt = false
        GitWorktreesOperationsService.getInstance(project).overrideProvidersForTests(
            repositoriesProvider = { listOf(repository) },
            worktreesProvider = {
                loadedOnEdt = ApplicationManager.getApplication().isDispatchThread
                emptyList()
            },
            parentDisposable = testRootDisposable,
        )

        GitWorktreesPanel(project)

        assertFalse(loadedOnEdt)
    }

    private fun panelWithWorktrees(
        repository: GitRepository,
        worktrees: List<WorktreeInfo>,
    ): GitWorktreesPanel {
        return panelWithWorktrees(mapOf(repository to worktrees))
    }

    private fun panelWithWorktrees(
        repositoriesAndWorktrees: Map<GitRepository, List<WorktreeInfo>>,
    ): GitWorktreesPanel {
        GitWorktreesOperationsService.getInstance(project).overrideProvidersForTests(
            repositoriesProvider = { repositoriesAndWorktrees.keys.toList() },
            worktreesProvider = { repository -> repositoriesAndWorktrees.getValue(repository) },
            parentDisposable = testRootDisposable,
        )
        return GitWorktreesPanel(project).apply {
            reloadSynchronouslyForTests()
        }
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

    private data class HeaderControls(
        val titleLabels: Map<GitWorktreesPanel.Column, JLabel>,
        val filterFields: Map<GitWorktreesPanel.Column, JBTextField>,
        val sortButtons: Map<GitWorktreesPanel.Column, JButton>,
    )

    private fun GitWorktreesPanel.headerControlsForTests(): HeaderControls {
        val sortButtons = descendantsForTests()
            .filterIsInstance<JButton>()
            .filter { it.toolTipText == Gw4iBundle.message("toolwindow.GitWorktrees.sort.button.tooltip") }
        val filterFields = descendantsForTests().filterIsInstance<JBTextField>()
        val titleLabels = descendantsForTests()
            .filterIsInstance<JLabel>()
            .filter { label -> label.text in columnNamesForTests() }

        assertEquals(3, sortButtons.size)
        assertEquals(3, filterFields.size)
        assertEquals(3, titleLabels.size)

        return HeaderControls(
            titleLabels = GitWorktreesPanel.Column.entries.zip(titleLabels).toMap(),
            filterFields = GitWorktreesPanel.Column.entries.zip(filterFields).toMap(),
            sortButtons = GitWorktreesPanel.Column.entries.zip(sortButtons).toMap(),
        )
    }

    private fun Component.descendantsForTests(): List<Component> {
        val descendants = mutableListOf<Component>()

        fun collect(component: Component) {
            descendants += component
            if (component is Container) {
                component.components.forEach(::collect)
            }
        }

        collect(this)
        return descendants
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
