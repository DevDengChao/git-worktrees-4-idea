package dev.dengchao.idea.plugin.git.worktrees.ui

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.icons.AllIcons
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.Cell
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.components.JBTextField
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import dev.dengchao.idea.plugin.git.worktrees.Gw4iBundle
import dev.dengchao.idea.plugin.git.worktrees.model.WorktreeInfo
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesOperationsService
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.AbstractTableModel

class GitWorktreesPanel(private val project: Project) : SimpleToolWindowPanel(true, true), DataProvider, Disposable {

    internal enum class Column(
        val titleKey: String,
        val filterKey: String,
    ) {
        WORKTREE_ID(
            "toolwindow.GitWorktrees.column.worktree.id",
            "toolwindow.GitWorktrees.filter.worktree.id",
        ),
        BRANCH_NAME(
            "toolwindow.GitWorktrees.column.branch.name",
            "toolwindow.GitWorktrees.filter.branch.name",
        ),
        LOCATION(
            "toolwindow.GitWorktrees.column.location",
            "toolwindow.GitWorktrees.filter.location",
        );

        fun value(worktree: WorktreeInfo): String {
            return when (this) {
                WORKTREE_ID -> worktree.name
                BRANCH_NAME -> worktree.branchName ?: DETACHED_BRANCH
                LOCATION -> worktree.path
            }
        }
    }

    private val service = GitWorktreesOperationsService.getInstance(project)
    private val tableModel = WorktreesTableModel()
    private val table = JBTable(tableModel)
    private val filters = linkedMapOf<Column, String>().apply {
        Column.entries.forEach { put(it, "") }
    }
    private val sortRules = mutableListOf<SortRule>()
    private val filterFields = mutableMapOf<Column, JBTextField>()
    private val sortButtons = mutableMapOf<Column, JButton>()
    private var snapshots: List<RepositoryWorktreesSnapshot> = emptyList()
    private var visibleRows: List<WorktreeTableRow> = emptyList()

    init {
        initToolbar()
        initTable()
        subscribeToRepositoryUpdates()
        reload()
    }

    override fun dispose() = Unit

    fun reload() {
        val selectedPaths = selectedWorktrees().map { it.worktree.path }.toSet()
        object : Task.Backgroundable(project, Gw4iBundle.message("GitWorktrees.task.load.worktrees.title"), true) {
            private var snapshot: List<RepositoryWorktreesSnapshot> = emptyList()

            override fun run(indicator: ProgressIndicator) {
                snapshot = loadSnapshot()
            }

            override fun onSuccess() {
                applySnapshot(snapshot, selectedPaths)
            }
        }.queue()
    }

    internal fun reloadSynchronouslyForTests() {
        applySnapshot(loadSnapshot(), selectedWorktrees().map { it.worktree.path }.toSet())
    }

    private fun applySnapshot(snapshot: List<RepositoryWorktreesSnapshot>, selectedPaths: Set<String>) {
        snapshots = snapshot
        rebuildVisibleRows(selectedPaths)
    }

    private fun rebuildVisibleRows(selectedPaths: Set<String> = selectedWorktrees().map { it.worktree.path }.toSet()) {
        visibleRows = snapshots.flatMap { repositorySnapshot ->
            val worktreeRows = repositorySnapshot.worktrees
                .filter(::matchesFilters)
                .let(::sortWorktrees)
                .map { worktree -> WorkingTreeRow(repositorySnapshot.repository, worktree) }

            if (worktreeRows.isEmpty() && hasActiveFilter()) {
                emptyList()
            } else {
                listOf(RepositoryRow(repositorySnapshot.repository)) + worktreeRows
            }
        }

        tableModel.fireTableDataChanged()
        restoreSelection(selectedPaths)
    }

    private fun restoreSelection(selectedPaths: Set<String>) {
        if (visibleRows.isEmpty()) {
            table.clearSelection()
            return
        }

        val restoredRows = visibleRows.indices.filter { row ->
            val item = visibleRows[row] as? WorkingTreeRow ?: return@filter false
            item.worktree.path in selectedPaths
        }

        table.selectionModel.valueIsAdjusting = true
        table.clearSelection()
        if (restoredRows.isNotEmpty()) {
            restoredRows.forEach { row -> table.addRowSelectionInterval(row, row) }
        } else {
            table.setRowSelectionInterval(firstWorktreeIndex() ?: 0, firstWorktreeIndex() ?: 0)
        }
        table.selectionModel.valueIsAdjusting = false
    }

    private fun loadSnapshot(): List<RepositoryWorktreesSnapshot> {
        return service.repositories().map { repository ->
            RepositoryWorktreesSnapshot(repository, service.worktrees(repository))
        }
    }

    internal fun selectRowForTests(index: Int) {
        table.setRowSelectionInterval(index, index)
    }

    internal fun selectRowsForTests(vararg indices: Int) {
        table.clearSelection()
        indices.forEach { index -> table.addRowSelectionInterval(index, index) }
    }

    internal fun columnCountForTests(): Int = tableModel.columnCount

    internal fun columnNamesForTests(): List<String> {
        return Column.entries.map { Gw4iBundle.message(it.titleKey) }
    }

    internal fun tableValueForTests(row: Int, column: Int): String {
        return tableModel.getValueAt(row, column).toString()
    }

    internal fun isRepositoryRowForTests(row: Int): Boolean = visibleRows[row] is RepositoryRow

    internal fun visibleRowLabelsForTests(): List<String> {
        return visibleRows.map { row ->
            when (row) {
                is RepositoryRow -> row.presentationName(project)
                is WorkingTreeRow -> row.worktree.name
            }
        }
    }

    internal fun setFilterForTests(column: Column, value: String) {
        setFilter(column, value, updateField = true)
    }

    internal fun toggleSortForTests(column: Column) {
        toggleSort(column)
    }

    fun selectedRepository(): GitRepository? {
        return singleSelectedRow()?.repository
    }

    fun selectedWorktree(): WorktreeInfo? {
        return (singleSelectedRow() as? WorkingTreeRow)?.worktree
    }

    fun selectedWorktrees(): List<GitWorktreesDataKeys.SelectedGitWorktree> {
        val selected = mutableListOf<GitWorktreesDataKeys.SelectedGitWorktree>()
        table.selectedRows.forEach { row ->
            val item = visibleRows.getOrNull(row) as? WorkingTreeRow ?: return@forEach
            selected += GitWorktreesDataKeys.SelectedGitWorktree(item.repository, item.worktree)
        }
        return selected
    }

    fun openSelectedWorktree() {
        val worktree = selectedWorktree() ?: return
        ProjectUtil.openOrImport(Path.of(worktree.path))
    }

    override fun getData(dataId: String): Any? {
        return when {
            GitWorktreesDataKeys.PANEL.`is`(dataId) -> this
            GitWorktreesDataKeys.CURRENT_REPOSITORY.`is`(dataId) -> selectedRepository()
            GitWorktreesDataKeys.SELECTED_WORKTREE.`is`(dataId) -> selectedWorktree()
            GitWorktreesDataKeys.SELECTED_WORKTREES.`is`(dataId) -> selectedWorktrees()
            else -> null
        }
    }

    private fun initToolbar() {
        val action = ActionManager.getInstance().getAction(GitWorktreesToolWindowFactory.TOOLBAR_ACTION_GROUP_ID)
        val actionGroup = action as? ActionGroup ?: return

        val toolbar = ActionManager.getInstance().createActionToolbar(
            GitWorktreesToolWindowFactory.TOOLBAR_ACTION_GROUP_ID,
            actionGroup,
            false,
        )
        toolbar.targetComponent = this
        setToolbar(toolbar.component)
    }

    private fun initTable() {
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        table.emptyText.text = Gw4iBundle.message("toolwindow.GitWorktrees.empty")
        table.setDefaultRenderer(Any::class.java, WorktreeTableCellRenderer())
        table.setShowGrid(false)
        table.intercellSpacing = Dimension(0, 0)
        table.rowHeight = JBUI.scale(24)
        table.autoResizeMode = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
        table.tableHeader.reorderingAllowed = false

        TableSpeedSearch.installOn(table) { _, cell -> speedSearchText(cell) }
        installToolWindowPopup(table)

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    openSelectedWorktree()
                }
            }
        })

        val scrollPane = ScrollPaneFactory.createScrollPane(table)
        scrollPane.setColumnHeaderView(null)
        val contentPanel = JPanel(BorderLayout()).apply {
            add(createHeaderPanel(), BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
        }
        setContent(contentPanel)
    }

    private fun speedSearchText(cell: Cell): String? {
        val row = visibleRows.getOrNull(cell.row) as? WorkingTreeRow ?: return null
        val column = Column.entries.getOrNull(cell.column) ?: return null
        return column.value(row.worktree)
    }

    private fun createHeaderPanel(): JPanel {
        return JPanel(GridLayout(1, Column.entries.size)).apply {
            border = JBUI.Borders.customLine(UIUtil.getBoundsColor(), 0, 0, 1, 0)
            Column.entries.forEach { column ->
                add(createHeaderCell(column))
            }
        }
    }

    private fun createHeaderCell(column: Column): JPanel {
        val title = JLabel(Gw4iBundle.message(column.titleKey))
        val sortButton = JButton(sortButtonText(column)).apply {
            margin = JBUI.insets(0, 4)
            toolTipText = Gw4iBundle.message("toolwindow.GitWorktrees.sort.button.tooltip")
            addActionListener {
                toggleSort(column)
            }
        }
        sortButtons[column] = sortButton

        val labelRow = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(title, BorderLayout.CENTER)
            add(sortButton, BorderLayout.EAST)
        }

        val filterField = JBTextField().apply {
            emptyText.text = Gw4iBundle.message(column.filterKey)
            border = JBUI.Borders.empty(1, 0)
            document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = updateFilter()
                override fun removeUpdate(e: DocumentEvent) = updateFilter()
                override fun changedUpdate(e: DocumentEvent) = updateFilter()

                private fun updateFilter() {
                    setFilter(column, text, updateField = false)
                }
            })
        }
        filterFields[column] = filterField

        return JPanel(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                JBUI.Borders.customLine(UIUtil.getBoundsColor(), 0, 0, 0, 1),
                JBUI.Borders.empty(2, 4, 2, 4),
            )
            add(labelRow, BorderLayout.NORTH)
            add(filterField, BorderLayout.CENTER)
        }
    }

    private fun subscribeToRepositoryUpdates() {
        val connection = project.messageBus.connect(this)
        connection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener {
            refreshLater()
        })
        connection.subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, VcsRepositoryMappingListener {
            refreshLater()
        })
    }

    private fun refreshLater() {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                reload()
            }
        }
    }

    private fun matchesFilters(worktree: WorktreeInfo): Boolean {
        return filters.all { (column, filter) ->
            val normalizedFilter = filter.trim()
            normalizedFilter.isEmpty() || column.value(worktree).contains(normalizedFilter, ignoreCase = true)
        }
    }

    private fun sortWorktrees(worktrees: List<WorktreeInfo>): List<WorktreeInfo> {
        if (sortRules.isEmpty()) return worktrees

        return worktrees.sortedWith { left, right ->
            sortRules.firstNotNullOfOrNull { rule ->
                val comparison = rule.column.value(left).compareTo(rule.column.value(right), ignoreCase = true)
                when {
                    comparison == 0 -> null
                    rule.direction == SortDirection.ASCENDING -> comparison
                    else -> -comparison
                }
            } ?: 0
        }
    }

    private fun hasActiveFilter(): Boolean {
        return filters.values.any { it.isNotBlank() }
    }

    private fun setFilter(column: Column, value: String, updateField: Boolean) {
        if (filters[column] == value) return
        filters[column] = value
        if (updateField) {
            filterFields[column]?.let { field ->
                if (field.text != value) {
                    field.text = value
                }
            }
        }
        rebuildVisibleRows()
    }

    private fun toggleSort(column: Column) {
        val existingIndex = sortRules.indexOfFirst { it.column == column }
        if (existingIndex < 0) {
            sortRules += SortRule(column, SortDirection.ASCENDING)
        } else {
            val existingRule = sortRules[existingIndex]
            when (existingRule.direction) {
                SortDirection.ASCENDING -> sortRules[existingIndex] = existingRule.copy(direction = SortDirection.DESCENDING)
                SortDirection.DESCENDING -> sortRules.removeAt(existingIndex)
            }
        }
        updateSortButtons()
        rebuildVisibleRows()
    }

    private fun updateSortButtons() {
        Column.entries.forEach { column ->
            sortButtons[column]?.text = sortButtonText(column)
        }
    }

    private fun sortButtonText(column: Column): String {
        return when (sortRules.firstOrNull { it.column == column }?.direction) {
            SortDirection.ASCENDING -> "^"
            SortDirection.DESCENDING -> "v"
            null -> "-"
        }
    }

    private fun firstWorktreeIndex(): Int? {
        return visibleRows.indices.firstOrNull { index -> visibleRows[index] is WorkingTreeRow }
    }

    private fun singleSelectedRow(): WorktreeTableRow? {
        val selectedRows = table.selectedRows
        if (selectedRows.size != 1) return null
        return visibleRows.getOrNull(selectedRows.single())
    }

    private sealed interface WorktreeTableRow {
        val repository: GitRepository
    }

    private data class RepositoryRow(override val repository: GitRepository) : WorktreeTableRow {
        fun presentationName(project: Project): String {
            val basePath = project.basePath ?: return repository.root.name
            val relativePath = FileUtil.getRelativePath(basePath, repository.root.path, '/')
            return relativePath?.takeIf { it.isNotBlank() && it != "." } ?: repository.root.name
        }

        fun presentationLocation(): String {
            return FileUtil.getLocationRelativeToUserHome(repository.root.path)
        }
    }

    private data class WorkingTreeRow(
        override val repository: GitRepository,
        val worktree: WorktreeInfo,
    ) : WorktreeTableRow

    private data class RepositoryWorktreesSnapshot(
        val repository: GitRepository,
        val worktrees: List<WorktreeInfo>,
    )

    private data class SortRule(
        val column: Column,
        val direction: SortDirection,
    )

    private enum class SortDirection {
        ASCENDING,
        DESCENDING,
    }

    private inner class WorktreesTableModel : AbstractTableModel() {
        override fun getRowCount(): Int = visibleRows.size

        override fun getColumnCount(): Int = Column.entries.size

        override fun getColumnName(column: Int): String {
            return Gw4iBundle.message(Column.entries[column].titleKey)
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            return when (val row = visibleRows[rowIndex]) {
                is RepositoryRow -> {
                    if (columnIndex == 0) {
                        "${row.presentationName(project)}  ${row.presentationLocation()}"
                    } else {
                        ""
                    }
                }
                is WorkingTreeRow -> Column.entries[columnIndex].value(row.worktree)
            }
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false
    }

    private inner class WorktreeTableCellRenderer : ColoredTableCellRenderer() {
        override fun customizeCellRenderer(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ) {
            border = JBUI.Borders.empty(0, if (column == 0) 8 else 4, 0, 4)
            setTextAlign(SwingConstants.LEFT)
            icon = null
            font = table.font

            val item = visibleRows.getOrNull(row)
            var text = value?.toString().orEmpty()
            when (item) {
                is RepositoryRow -> {
                    font = table.font.deriveFont(java.awt.Font.BOLD)
                    icon = if (column == 0) AllIcons.Nodes.Folder else null
                    if (column != 0) {
                        text = ""
                    }
                }
                is WorkingTreeRow -> {
                    if (column == 0) {
                        icon = if (item.worktree.isCurrent) AllIcons.Actions.Checked else AllIcons.Empty
                        if (item.worktree.isMain) {
                            font = table.font.deriveFont(java.awt.Font.BOLD)
                        }
                    }
                }
                null -> Unit
            }
            SpeedSearchUtil.appendFragmentsForSpeedSearch(table, text, SimpleTextAttributes.REGULAR_ATTRIBUTES, true, this)
        }
    }

    companion object {
        private const val DETACHED_BRANCH = "detached"

        internal fun installToolWindowPopupForTests(component: JComponent): PopupHandler? {
            return installToolWindowPopup(component)
        }

        private fun installToolWindowPopup(component: JComponent): PopupHandler? {
            val action = ActionManager.getInstance().getAction(GitWorktreesToolWindowFactory.POPUP_ACTION_GROUP_ID)
            val actionGroup = action as? ActionGroup ?: return null
            return PopupHandler.installPopupMenu(
                component,
                actionGroup,
                GitWorktreesToolWindowFactory.POPUP_ACTION_GROUP_ID,
            )
        }
    }
}
