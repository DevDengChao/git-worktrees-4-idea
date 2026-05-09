// Copyright 2000-2026 JetBrains s.r.o. and contributors.
package dev.dengchao.idea.plugin.git.worktrees

import com.intellij.openapi.project.Project
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataMap
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshotProvider
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.ui.components.JBTextField
import com.intellij.ui.speedSearch.SpeedSearchSupply
import com.intellij.util.ui.UIUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatform4TestCase
import dev.dengchao.idea.plugin.git.worktrees.model.WorktreeInfo
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesOperationsService
import dev.dengchao.idea.plugin.git.worktrees.ui.GitWorktreesDataKeys
import dev.dengchao.idea.plugin.git.worktrees.ui.GitWorktreesPanel
import git4idea.repo.GitRepository
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.event.MouseEvent
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.nio.file.Path
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
    fun `provider note is shown unobtrusively at toolbar end`() {
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        val panel = panelWithWorktrees(repository, emptyList())
        val table = panel.descendantsForTests().filterIsInstance<JTable>().single()
        val toolbar = panel.descendantsForTests().filterIsInstance<ActionToolbar>().single()
        val providerNote = panel.descendantsForTests()
            .filterIsInstance<JLabel>()
            .single { it.text == Gw4iBundle.message("toolwindow.GitWorktrees.provider.note") }
        val parent = providerNote.parent
        val layout = parent.layout as BorderLayout

        assertEquals(BorderLayout.EAST, layout.getConstraints(providerNote))
        assertEquals(SwingUtilities.HORIZONTAL, toolbar.orientation)
        assertFalse(SwingUtilities.isDescendingFrom(providerNote, table.tableHeader))
        assertEquals(UIUtil.getContextHelpForeground(), providerNote.foreground)
        assertTrue(providerNote.font.size2D < table.font.size2D)
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
    fun `table installs speed search supply`() {
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        val panel = panelWithWorktrees(repository, emptyList())

        assertNotNull(panel.speedSearchSupplyForTests())
    }

    @Test
    fun `speed search selects worktree rows by all visible columns`() {
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        val alpha = WorktreeInfo(path = "/project/root/alpha-tree", branchName = "feature/login", isMain = false, isCurrent = false, isLocked = false, isPrunable = false)
        val beta = WorktreeInfo(path = "/project/root/beta-tree", branchName = "feature/report", isMain = false, isCurrent = false, isLocked = false, isPrunable = false)
        val release = WorktreeInfo(path = "/release/location-target", branchName = "release/main", isMain = false, isCurrent = false, isLocked = false, isPrunable = false)
        val panel = panelWithWorktrees(repository, listOf(alpha, beta, release))
        val speedSearch = panel.speedSearchSupplyForTests()

        speedSearch.findAndSelectElement("beta-tree")
        assertSame(beta, panel.dataForTests(GitWorktreesDataKeys.SELECTED_WORKTREE))

        speedSearch.findAndSelectElement("feature/login")
        assertSame(alpha, panel.dataForTests(GitWorktreesDataKeys.SELECTED_WORKTREE))

        speedSearch.findAndSelectElement("location-target")
        assertSame(release, panel.dataForTests(GitWorktreesDataKeys.SELECTED_WORKTREE))
    }

    @Test
    fun `speed search ignores repository grouping rows`() {
        val repository = gitRepository(rootPath = "/project/matched-repository", currentBranchName = "master")
        val worktree = WorktreeInfo(path = "/project/plain-worktree", branchName = "feature/plain", isMain = false, isCurrent = false, isLocked = false, isPrunable = false)
        val panel = panelWithWorktrees(repository, listOf(worktree))
        val speedSearch = panel.speedSearchSupplyForTests()

        speedSearch.findAndSelectElement("matched-repository")

        assertNull(panel.dataForTests(GitWorktreesDataKeys.CURRENT_REPOSITORY))
        assertNull(panel.dataForTests(GitWorktreesDataKeys.SELECTED_WORKTREE))
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
        assertSame(alpha, panel.dataForTests(GitWorktreesDataKeys.SELECTED_WORKTREE))
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
    fun `double clicking repository row collapses and expands its worktrees`() {
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        val alpha = WorktreeInfo(path = "/project/root/alpha-tree", branchName = "feature/alpha", isMain = false, isCurrent = false, isLocked = false, isPrunable = false)
        val beta = WorktreeInfo(path = "/project/root/beta-tree", branchName = "feature/beta", isMain = false, isCurrent = false, isLocked = false, isPrunable = false)
        val panel = panelWithWorktrees(repository, listOf(alpha, beta))

        panel.doubleClickRowForTests(0)

        assertEquals(listOf("root"), panel.visibleRowLabelsForTests())
        assertTrue(panel.isRepositoryRowForTests(0))

        panel.doubleClickRowForTests(0)

        assertEquals(listOf("root", "alpha-tree", "beta-tree"), panel.visibleRowLabelsForTests())
    }

    @Test
    fun `single clicking repository chevron collapses and expands its worktrees`() {
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        val alpha = WorktreeInfo(path = "/project/root/alpha-tree", branchName = "feature/alpha", isMain = false, isCurrent = false, isLocked = false, isPrunable = false)
        val beta = WorktreeInfo(path = "/project/root/beta-tree", branchName = "feature/beta", isMain = false, isCurrent = false, isLocked = false, isPrunable = false)
        val panel = panelWithWorktrees(repository, listOf(alpha, beta))

        panel.clickRepositoryChevronForTests(0)

        assertEquals(listOf("root"), panel.visibleRowLabelsForTests())

        panel.clickRepositoryChevronForTests(0)

        assertEquals(listOf("root", "alpha-tree", "beta-tree"), panel.visibleRowLabelsForTests())
    }

    @Test
    fun `single clicking repository row outside chevron only selects it`() {
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        val worktree = WorktreeInfo(path = "/project/root/feature-tree", branchName = "feature", isMain = false, isCurrent = false, isLocked = false, isPrunable = false)
        val panel = panelWithWorktrees(repository, listOf(worktree))

        panel.clickRepositoryTextForTests(0)

        assertEquals(listOf("root", "feature-tree"), panel.visibleRowLabelsForTests())
        assertSame(repository, panel.dataForTests(GitWorktreesDataKeys.CURRENT_REPOSITORY))
        assertNull(panel.dataForTests(GitWorktreesDataKeys.SELECTED_WORKTREE))
    }

    @Test
    fun `sticky repository row appears while scrolling inside repository group`() {
        val firstRepository = gitRepository(rootPath = "/project/first", currentBranchName = "master")
        val secondRepository = gitRepository(rootPath = "/project/second", currentBranchName = "master")
        val firstWorktrees = listOf(
            WorktreeInfo(path = "/project/first/alpha-tree", branchName = "feature/alpha", isMain = false, isCurrent = false, isLocked = false, isPrunable = false),
            WorktreeInfo(path = "/project/first/beta-tree", branchName = "feature/beta", isMain = false, isCurrent = false, isLocked = false, isPrunable = false),
        )
        val secondWorktree = WorktreeInfo(path = "/project/second/gamma-tree", branchName = "feature/gamma", isMain = false, isCurrent = false, isLocked = false, isPrunable = false)
        val panel = panelWithWorktrees(
            mapOf(
                firstRepository to firstWorktrees,
                secondRepository to listOf(secondWorktree),
            ),
        )

        panel.scrollToRowTopForTests(1)

        assertEquals("first", panel.stickyRepositoryLabelForTests())
        assertEquals(0, panel.stickyRepositoryYOffsetForTests())
    }

    @Test
    fun `sticky repository row is pushed out and replaced by next repository group`() {
        val firstRepository = gitRepository(rootPath = "/project/first", currentBranchName = "master")
        val secondRepository = gitRepository(rootPath = "/project/second", currentBranchName = "master")
        val firstWorktrees = listOf(
            WorktreeInfo(path = "/project/first/alpha-tree", branchName = "feature/alpha", isMain = false, isCurrent = false, isLocked = false, isPrunable = false),
            WorktreeInfo(path = "/project/first/beta-tree", branchName = "feature/beta", isMain = false, isCurrent = false, isLocked = false, isPrunable = false),
            WorktreeInfo(path = "/project/first/delta-tree", branchName = "feature/delta", isMain = false, isCurrent = false, isLocked = false, isPrunable = false),
        )
        val secondWorktrees = listOf(
            WorktreeInfo(path = "/project/second/gamma-tree", branchName = "feature/gamma", isMain = false, isCurrent = false, isLocked = false, isPrunable = false),
            WorktreeInfo(path = "/project/second/omega-tree", branchName = "feature/omega", isMain = false, isCurrent = false, isLocked = false, isPrunable = false),
        )
        val panel = panelWithWorktrees(
            mapOf(
                firstRepository to firstWorktrees,
                secondRepository to secondWorktrees,
            ),
        )

        val secondRepositoryRow = panel.visibleRowLabelsForTests().indexOf("second")
        assertTrue(secondRepositoryRow >= 0)
        val nextRepositoryTop = panel.rowTopForTests(secondRepositoryRow)
        val pushTransitionOffset = panel.rowHeightForTests() / 2
        panel.scrollToYForTests(nextRepositoryTop - pushTransitionOffset)
        assertEquals("first", panel.stickyRepositoryLabelForTests())
        assertTrue(requireNotNull(panel.stickyRepositoryYOffsetForTests()) < 0)

        panel.scrollToRowTopForTests(5)
        assertEquals("second", panel.stickyRepositoryLabelForTests())
        assertEquals(0, panel.stickyRepositoryYOffsetForTests())
    }

    @Test
    fun `collapsing one repository does not affect sibling repository groups`() {
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

        panel.doubleClickRowForTests(0)

        assertEquals(listOf("first", "second", "beta-tree"), panel.visibleRowLabelsForTests())
    }

    @Test
    fun `sticky painting does not change repository expand collapse interactions`() {
        val firstRepository = gitRepository(rootPath = "/project/first", currentBranchName = "master")
        val secondRepository = gitRepository(rootPath = "/project/second", currentBranchName = "master")
        val firstWorktree = WorktreeInfo(path = "/project/first/alpha-tree", branchName = "feature/alpha", isMain = false, isCurrent = false, isLocked = false, isPrunable = false)
        val secondWorktrees = listOf(
            WorktreeInfo(path = "/project/second/beta-tree", branchName = "feature/beta", isMain = false, isCurrent = false, isLocked = false, isPrunable = false),
            WorktreeInfo(path = "/project/second/gamma-tree", branchName = "feature/gamma", isMain = false, isCurrent = false, isLocked = false, isPrunable = false),
        )
        val panel = panelWithWorktrees(
            mapOf(
                firstRepository to listOf(firstWorktree),
                secondRepository to secondWorktrees,
            ),
        )

        panel.scrollToRowTopForTests(1)
        assertEquals("first", panel.stickyRepositoryLabelForTests())

        panel.doubleClickRowForTests(2)

        assertEquals(listOf("first", "alpha-tree", "second"), panel.visibleRowLabelsForTests())
    }

    @Test
    fun `repository collapse state survives reload in the same panel`() {
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        val worktree = WorktreeInfo(path = "/project/root/feature-tree", branchName = "feature", isMain = false, isCurrent = false, isLocked = false, isPrunable = false)
        val panel = panelWithWorktrees(repository, listOf(worktree))

        panel.doubleClickRowForTests(0)
        panel.reloadSynchronouslyForTests()

        assertEquals(listOf("root"), panel.visibleRowLabelsForTests())
    }

    @Test
    fun `collapsed repository shows only matching repository row while filters are active`() {
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        val alpha = WorktreeInfo(path = "/project/root/alpha-tree", branchName = "feature/alpha", isMain = false, isCurrent = false, isLocked = false, isPrunable = false)
        val beta = WorktreeInfo(path = "/project/root/beta-tree", branchName = "feature/beta", isMain = false, isCurrent = false, isLocked = false, isPrunable = false)
        val panel = panelWithWorktrees(repository, listOf(alpha, beta))

        panel.setFilterForTests(GitWorktreesPanel.Column.BRANCH_NAME, "alpha")
        panel.doubleClickRowForTests(0)

        assertEquals(listOf("root"), panel.visibleRowLabelsForTests())

        panel.doubleClickRowForTests(0)

        assertEquals(listOf("root", "alpha-tree"), panel.visibleRowLabelsForTests())
    }

    @Test
    fun `double clicking worktree row does not collapse its repository`() {
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        val worktree = WorktreeInfo(path = "/project/root/feature-tree", branchName = "feature", isMain = false, isCurrent = false, isLocked = false, isPrunable = false)
        val panel = panelWithWorktrees(repository, listOf(worktree))
        val openedPaths = mutableListOf<String>()
        GitWorktreesPanel.overrideOpenWorktreeProjectForTests(
            opener = { path -> openedPaths += path.toString() },
            parentDisposable = testRootDisposable,
        )

        panel.doubleClickRowForTests(1)

        assertEquals(listOf("root", "feature-tree"), panel.visibleRowLabelsForTests())
        assertSame(worktree, panel.dataForTests(GitWorktreesDataKeys.SELECTED_WORKTREE))
        assertEquals(listOf(Path.of(worktree.path).toString()), openedPaths)
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

        assertSame(repository, panel.dataForTests(GitWorktreesDataKeys.CURRENT_REPOSITORY))
        assertNull(panel.dataForTests(GitWorktreesDataKeys.SELECTED_WORKTREE))
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

        assertSame(repository, panel.dataForTests(GitWorktreesDataKeys.CURRENT_REPOSITORY))
        assertSame(worktree, panel.dataForTests(GitWorktreesDataKeys.SELECTED_WORKTREE))
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
        val selected = panel.dataForTests(GitWorktreesDataKeys.SELECTED_WORKTREES) as List<GitWorktreesDataKeys.SelectedGitWorktree>
        assertEquals(listOf(featureWorktree, bugfixWorktree), selected.map { it.worktree })
        assertTrue(selected.all { it.repository === repository })
        assertNull(panel.dataForTests(GitWorktreesDataKeys.CURRENT_REPOSITORY))
        assertNull(panel.dataForTests(GitWorktreesDataKeys.SELECTED_WORKTREE))
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
        val selected = panel.dataForTests(GitWorktreesDataKeys.SELECTED_WORKTREES) as List<GitWorktreesDataKeys.SelectedGitWorktree>
        assertEquals(listOf(worktree), selected.map { it.worktree })
        assertNull(panel.dataForTests(GitWorktreesDataKeys.CURRENT_REPOSITORY))
        assertNull(panel.dataForTests(GitWorktreesDataKeys.SELECTED_WORKTREE))
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

        assertSame(worktree, panel.dataForTests(GitWorktreesDataKeys.SELECTED_WORKTREE))
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
        val selected = panel.dataForTests(GitWorktreesDataKeys.SELECTED_WORKTREES) as List<GitWorktreesDataKeys.SelectedGitWorktree>
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

    private fun GitWorktreesPanel.speedSearchSupplyForTests(): SpeedSearchSupply {
        val table = descendantsForTests().filterIsInstance<JTable>().single()
        return requireNotNull(SpeedSearchSupply.getSupply(table, true)) {
            "Git Worktrees table should install a speed search supply"
        }
    }

    private fun <T : Any> GitWorktreesPanel.dataForTests(key: DataKey<T>): T? {
        val sink = TestDataSink()
        uiDataSnapshot(sink)
        return sink.get(key)
    }

    private fun GitWorktreesPanel.doubleClickRowForTests(row: Int) {
        clickRowForTests(row, clickCount = 2, xOffset = { rect -> rect.width / 2 })
    }

    private fun GitWorktreesPanel.clickRepositoryChevronForTests(row: Int) {
        clickRowForTests(row, clickCount = 1, xOffset = { 10 })
    }

    private fun GitWorktreesPanel.clickRepositoryTextForTests(row: Int) {
        clickRowForTests(row, clickCount = 1, xOffset = { rect -> rect.width / 2 })
    }

    private fun GitWorktreesPanel.clickRowForTests(
        row: Int,
        clickCount: Int,
        xOffset: (java.awt.Rectangle) -> Int,
    ) {
        val table = descendantsForTests().filterIsInstance<JTable>().single()
        val rect = table.getCellRect(row, 0, true)
        table.setRowSelectionInterval(row, row)
        val event = MouseEvent(
            table,
            MouseEvent.MOUSE_CLICKED,
            System.currentTimeMillis(),
            0,
            rect.x + xOffset(rect),
            rect.y + rect.height / 2,
            clickCount,
            false,
            MouseEvent.BUTTON1,
        )
        table.mouseListeners.forEach { listener -> listener.mouseClicked(event) }
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

    private class TestDataSink : DataSink {
        private val data = mutableMapOf<DataKey<*>, Any?>()

        @Suppress("UNCHECKED_CAST")
        fun <T : Any> get(key: DataKey<T>): T? = data[key] as T?

        override fun <T : Any> set(key: DataKey<T>, data: T?) {
            this.data[key] = data
        }

        override fun <T : Any> setNull(key: DataKey<T>) {
            data[key] = null
        }

        override fun <T : Any> lazyValue(key: DataKey<T>, data: (DataMap) -> T?) {
            throw UnsupportedOperationException("Lazy data is not used in GitWorktreesPanel tests")
        }

        override fun <T : Any> lazyNull(key: DataKey<T>) {
            data[key] = null
        }

        override fun uiDataSnapshot(provider: UiDataProvider) {
            provider.uiDataSnapshot(this)
        }

        override fun uiDataSnapshot(provider: DataProvider) {
            throw UnsupportedOperationException("Legacy DataProvider is not used in GitWorktreesPanel tests")
        }

        override fun dataSnapshot(provider: DataSnapshotProvider) {
            provider.dataSnapshot(this)
        }
    }
}
