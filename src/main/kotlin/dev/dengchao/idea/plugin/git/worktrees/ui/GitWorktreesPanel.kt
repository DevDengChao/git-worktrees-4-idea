package dev.dengchao.idea.plugin.git.worktrees.ui

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.icons.AllIcons
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
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
import dev.dengchao.idea.plugin.git.worktrees.settings.GitWorktreesProjectConfigurable
import dev.dengchao.idea.plugin.git.worktrees.settings.GitWorktreesProjectSettings
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.SwingConstants
import javax.swing.Icon
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.table.AbstractTableModel
import javax.swing.table.JTableHeader
import javax.swing.table.TableColumnModel

class GitWorktreesPanel(private val project: Project) : SimpleToolWindowPanel(true, true), UiDataProvider, Disposable {

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

        fun value(repository: GitRepository, worktree: WorktreeInfo, showRelativeLocations: Boolean): String {
            return when (this) {
                WORKTREE_ID -> worktree.name
                BRANCH_NAME -> worktree.branchName ?: DETACHED_BRANCH
                LOCATION -> if (showRelativeLocations) relativeWorktreeLocation(repository, worktree) else worktree.path
            }
        }
    }

    private val service = GitWorktreesOperationsService.getInstance(project)
    private val settings = GitWorktreesProjectSettings.getInstance(project)
    private val tableModel = WorktreesTableModel()
    private val table = StickyWorktreesTable()
    private val filters = linkedMapOf<Column, String>().apply {
        Column.entries.forEach { put(it, "") }
    }
    private val sortRules = mutableListOf<SortRule>()
    private val filterFields = mutableMapOf<Column, JBTextField>()
    private val sortButtons = mutableMapOf<Column, JButton>()
    private val collapsedRepositoryRoots = mutableSetOf<String>()
    private var snapshots: List<RepositoryWorktreesSnapshot> = emptyList()
    private var visibleRows: List<WorktreeTableRow> = emptyList()
    private var repositoryRowIndices: List<Int> = emptyList()
    private var testViewport: Rectangle? = null

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
            val repository = repositorySnapshot.repository
            val worktreeRows = repositorySnapshot.worktrees
                .filter { worktree -> matchesFilters(repository, worktree) }
                .let { worktrees -> sortWorktrees(repository, worktrees) }
                .map { worktree -> WorkingTreeRow(repository, worktree) }

            if (worktreeRows.isEmpty() && hasActiveFilter()) {
                emptyList()
            } else {
                val repositoryRow = RepositoryRow(repository)
                if (isRepositoryCollapsed(repository)) {
                    listOf(repositoryRow)
                } else {
                    listOf(repositoryRow) + worktreeRows
                }
            }
        }

        tableModel.fireTableDataChanged()
        repositoryRowIndices = visibleRows.mapIndexedNotNull { index, row ->
            index.takeIf { row is RepositoryRow }
        }
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

    internal fun stickyRepositoryLabelForTests(): String? {
        val stickyRow = stickyRepositoryRowState() ?: return null
        val repositoryRow = visibleRows.getOrNull(stickyRow.rowIndex) as? RepositoryRow ?: return null
        return repositoryRow.presentationName(project)
    }

    internal fun stickyRepositoryYOffsetForTests(): Int? {
        val stickyRow = stickyRepositoryRowState() ?: return null
        val viewportY = testViewport?.y ?: table.visibleRect.y
        return stickyRow.y - viewportY
    }

    internal fun scrollToRowTopForTests(row: Int) {
        val rowBounds = table.getCellRect(row, 0, true)
        testViewport = Rectangle(0, rowBounds.y, maxOf(table.width, 1), table.rowHeight)
        table.scrollRectToVisible(Rectangle(0, rowBounds.y, 1, rowBounds.height))
    }

    internal fun scrollToYForTests(y: Int) {
        testViewport = Rectangle(0, y, maxOf(table.width, 1), table.rowHeight)
        table.scrollRectToVisible(Rectangle(0, y, 1, table.rowHeight))
    }

    internal fun rowTopForTests(row: Int): Int {
        return table.getCellRect(row, 0, true).y
    }

    internal fun rowHeightForTests(): Int = table.rowHeight

    internal fun setFilterForTests(column: Column, value: String) {
        setFilter(column, value, updateField = true)
    }

    internal fun toggleSortForTests(column: Column) {
        toggleSort(column)
    }

    fun toggleSelectedRepositoryExpanded() {
        val repository = (singleSelectedRow() as? RepositoryRow)?.repository ?: return
        val repositoryRoot = repositoryRootKey(repository)
        if (!collapsedRepositoryRoots.add(repositoryRoot)) {
            collapsedRepositoryRoots.remove(repositoryRoot)
        }
        rebuildVisibleRows(selectedPaths = emptySet())
        selectRepository(repositoryRoot)
    }

    fun isSelectedRepositoryCollapsed(): Boolean {
        val repository = (singleSelectedRow() as? RepositoryRow)?.repository ?: return false
        return isRepositoryCollapsed(repository)
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
        openWorktreeProject(Path.of(worktree.path))
    }

    override fun uiDataSnapshot(sink: DataSink) {
        sink[GitWorktreesDataKeys.PANEL] = this
        sink[GitWorktreesDataKeys.CURRENT_REPOSITORY] = selectedRepository()
        sink[GitWorktreesDataKeys.SELECTED_WORKTREE] = selectedWorktree()
        sink[GitWorktreesDataKeys.SELECTED_WORKTREES] = selectedWorktrees()
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
        toolbar.setOrientation(SwingConstants.HORIZONTAL)
        setToolbar(
            JPanel(BorderLayout()).apply {
                isOpaque = false
                add(toolbar.component, BorderLayout.WEST)
                add(providerMetaPanel(), BorderLayout.EAST)
            },
        )
    }

    private fun providerMetaPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(providerNoteLabel(), BorderLayout.WEST)
            add(providerSettingsButton(), BorderLayout.EAST)
        }
    }

    private fun providerNoteLabel(): JLabel {
        return JLabel(Gw4iBundle.message("toolwindow.GitWorktrees.provider.note")).apply {
            foreground = UIUtil.getContextHelpForeground()
            font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
            border = JBUI.Borders.emptyRight(4)
        }
    }

    private fun providerSettingsButton(): JButton {
        return JButton(AllIcons.General.Settings).apply {
            toolTipText = Gw4iBundle.message("toolwindow.GitWorktrees.provider.settings.tooltip")
            isOpaque = false
            isContentAreaFilled = false
            isBorderPainted = false
            isFocusPainted = false
            isFocusable = false
            border = JBUI.Borders.emptyLeft(4)
            addActionListener {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, GitWorktreesProjectConfigurable.ID)
            }
        }
    }

    private fun initTable() {
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        table.emptyText.text = Gw4iBundle.message("toolwindow.GitWorktrees.empty")
        table.setDefaultRenderer(Any::class.java, WorktreeTableCellRenderer())
        table.setShowGrid(false)
        table.intercellSpacing = Dimension(0, 0)
        table.rowHeight = JBUI.scale(24)
        table.autoResizeMode = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
        table.tableHeader = WorktreesTableHeader(table.columnModel).apply {
            reorderingAllowed = false
        }

        TableSpeedSearch.installOn(table) { _, cell -> speedSearchText(cell) }
        installToolWindowPopup(table)

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 1 && isRepositoryChevronClick(e)) {
                    toggleSelectedRepositoryExpanded()
                    return
                }

                if (e.clickCount == 2) {
                    when (visibleRows.getOrNull(table.rowAtPoint(e.point))) {
                        is RepositoryRow -> toggleSelectedRepositoryExpanded()
                        is WorkingTreeRow -> openSelectedWorktree()
                        null -> Unit
                    }
                }
            }
        })

        val scrollPane = ScrollPaneFactory.createScrollPane(table)
        scrollPane.setColumnHeaderView(table.tableHeader)
        setContent(scrollPane)
    }

    private fun isRepositoryChevronClick(event: MouseEvent): Boolean {
        val row = table.rowAtPoint(event.point)
        if (visibleRows.getOrNull(row) !is RepositoryRow) return false
        if (table.columnAtPoint(event.point) != 0) return false

        val cellBounds = table.getCellRect(row, 0, true)
        val chevronWidth = JBUI.scale(24)
        return event.x in cellBounds.x until cellBounds.x + chevronWidth
    }

    private fun speedSearchText(cell: Cell): String? {
        val row = visibleRows.getOrNull(cell.row) as? WorkingTreeRow ?: return null
        val column = Column.entries.getOrNull(cell.column) ?: return null
        return column.value(row.repository, row.worktree, settings.effectiveShowRelativeLocations())
    }

    private fun createHeaderCell(column: Column): JPanel {
        val title = JLabel(Gw4iBundle.message(column.titleKey)).apply {
            border = JBUI.Borders.empty(0, 0, 0, 6)
        }
        val filterField = JBTextField().apply {
            emptyText.text = Gw4iBundle.message(column.filterKey)
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

        val sortButton = JButton(sortButtonIcon(column)).apply {
            text = ""
            isFocusable = false
            isBorderPainted = false
            isContentAreaFilled = false
            isOpaque = false
            margin = JBUI.emptyInsets()
            preferredSize = Dimension(JBUI.scale(28), JBUI.scale(24))
            toolTipText = Gw4iBundle.message("toolwindow.GitWorktrees.sort.button.tooltip")
            addActionListener {
                toggleSort(column)
            }
        }
        sortButtons[column] = sortButton

        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(2, 4, 2, 4)
            add(title, BorderLayout.WEST)
            add(filterField, BorderLayout.CENTER)
            add(sortButton, BorderLayout.EAST)
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

    private fun matchesFilters(repository: GitRepository, worktree: WorktreeInfo): Boolean {
        val showRelativeLocations = settings.effectiveShowRelativeLocations()
        return filters.all { (column, filter) ->
            val normalizedFilter = filter.trim()
            normalizedFilter.isEmpty() || column.value(repository, worktree, showRelativeLocations).contains(normalizedFilter, ignoreCase = true)
        }
    }

    private fun sortWorktrees(repository: GitRepository, worktrees: List<WorktreeInfo>): List<WorktreeInfo> {
        if (sortRules.isEmpty()) return worktrees
        val showRelativeLocations = settings.effectiveShowRelativeLocations()

        return worktrees.sortedWith { left, right ->
            sortRules.firstNotNullOfOrNull { rule ->
                val comparison = rule.column.value(repository, left, showRelativeLocations)
                    .compareTo(rule.column.value(repository, right, showRelativeLocations), ignoreCase = true)
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

    private fun isRepositoryCollapsed(repository: GitRepository): Boolean {
        return repositoryRootKey(repository) in collapsedRepositoryRoots
    }

    private fun repositoryRootKey(repository: GitRepository): String {
        return repository.root.path
    }

    private fun selectRepository(repositoryRoot: String) {
        val row = visibleRows.indexOfFirst { item ->
            item is RepositoryRow && repositoryRootKey(item.repository) == repositoryRoot
        }
        if (row >= 0) {
            table.setRowSelectionInterval(row, row)
        }
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
            sortButtons[column]?.icon = sortButtonIcon(column)
        }
    }

    private fun sortButtonIcon(column: Column): Icon {
        return when (sortRules.firstOrNull { it.column == column }?.direction) {
            SortDirection.ASCENDING -> AllIcons.General.ArrowUp
            SortDirection.DESCENDING -> AllIcons.General.ArrowDown
            null -> AllIcons.Ide.UpDown
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

    private fun stickyRepositoryRowState(): StickyRepositoryRowState? {
        if (visibleRows.isEmpty()) return null
        val visibleRect = testViewport ?: table.visibleRect.takeIf { it.height > 0 } ?: return null

        val topRow = table.rowAtPoint(visibleRect.location)
        if (topRow < 0 || visibleRows.getOrNull(topRow) is RepositoryRow) return null

        val stickyRowIndex = repositoryRowIndices.lastOrNull { row -> row <= topRow } ?: return null
        val nextRepositoryRow = repositoryRowIndices.firstOrNull { row -> row > stickyRowIndex }
        val stickyRowHeight = table.getCellRect(stickyRowIndex, 0, true).height

        var stickyY = visibleRect.y
        if (nextRepositoryRow != null) {
            val nextRowY = table.getCellRect(nextRepositoryRow, 0, true).y
            // Keep the sticky row pinned to viewport top. Once the next repository row enters the top sticky area,
            // shift the current sticky row upward by up to one row height so the next row pushes it out naturally.
            stickyY = minOf(stickyY, nextRowY - stickyRowHeight)
        }
        return StickyRepositoryRowState(stickyRowIndex, stickyY, stickyRowHeight)
    }

    private sealed interface WorktreeTableRow {
        val repository: GitRepository
    }

    private data class RepositoryRow(override val repository: GitRepository) : WorktreeTableRow {
        fun presentationName(project: Project): String {
            val basePath = project.basePath ?: return repository.root.name
            val relativePath = FileUtil.getRelativePath(basePath, repository.root.path, '/')
            return relativePath
                ?.takeIf { it.isNotBlank() && it != "." && it != ".." && !it.startsWith("../") }
                ?: repository.root.name
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

    private data class StickyRepositoryRowState(
        val rowIndex: Int,
        val y: Int,
        val height: Int,
    )

    private enum class SortDirection {
        ASCENDING,
        DESCENDING,
    }

    private inner class WorktreesTableHeader(columnModel: TableColumnModel) : JTableHeader(columnModel) {
        private val headerCells: List<JPanel> = Column.entries.map(::createHeaderCell)

        init {
            layout = null
            border = JBUI.Borders.customLine(UIUtil.getBoundsColor(), 0, 0, 1, 0)
            headerCells.forEach { add(it) }
        }

        override fun getPreferredSize(): Dimension {
            val preferredSize = super.getPreferredSize()
            return Dimension(preferredSize.width, JBUI.scale(36))
        }

        override fun doLayout() {
            super.doLayout()
            var x = 0
            headerCells.forEachIndexed { index, headerCell ->
                val width = columnModel.getColumn(index).width
                headerCell.bounds = Rectangle(x, 0, width, height)
                x += width
            }
        }
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
                    when (Column.entries[columnIndex]) {
                        Column.WORKTREE_ID -> row.presentationName(project)
                        Column.BRANCH_NAME -> ""
                        Column.LOCATION -> row.presentationLocation()
                    }
                }
                is WorkingTreeRow -> Column.entries[columnIndex]
                    .value(row.repository, row.worktree, settings.effectiveShowRelativeLocations())
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
                    icon = if (column == 0) repositoryRowIcon(item.repository) else null
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

    private inner class StickyWorktreesTable : JBTable(tableModel) {
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val stickyRow = stickyRepositoryRowState() ?: return
            val graphics = g as? Graphics2D ?: return
            paintStickyRepositoryRow(graphics, stickyRow)
        }

        private fun paintStickyRepositoryRow(
            graphics: Graphics2D,
            stickyRow: StickyRepositoryRowState,
        ) {
            val visibleRect = visibleRect
            if (visibleRect.height <= 0) return

            val previousClip = graphics.clip
            try {
                graphics.setClip(Rectangle(visibleRect.x, visibleRect.y, visibleRect.width, stickyRow.height))
                for (column in 0 until columnCount) {
                    val sourceRect = getCellRect(stickyRow.rowIndex, column, true)
                    val paintRect = Rectangle(sourceRect.x, stickyRow.y, sourceRect.width, stickyRow.height)
                    val renderer = getCellRenderer(stickyRow.rowIndex, column)
                    val component = prepareRenderer(renderer, stickyRow.rowIndex, column)
                    SwingUtilities.paintComponent(
                        graphics,
                        component,
                        this,
                        paintRect.x,
                        paintRect.y,
                        paintRect.width,
                        paintRect.height,
                    )
                }
            } finally {
                graphics.clip = previousClip
            }
        }
    }

    companion object {
        private const val DETACHED_BRANCH = "detached"
        private const val RELATIVE_CURRENT_DIR = "."
        private const val RELATIVE_PARENT_DIR = ".."
        private const val RELATIVE_PARENT_PREFIX = "../"
        internal const val ACTION_DESCRIPTION_PROPERTY = "action.description"
        private var openWorktreeProject: (Path) -> Unit = { path ->
            ProjectUtil.openOrImport(path)
        }

        internal fun overrideOpenWorktreeProjectForTests(
            opener: (Path) -> Unit,
            parentDisposable: Disposable,
        ) {
            val previousOpener = openWorktreeProject
            openWorktreeProject = opener
            com.intellij.openapi.util.Disposer.register(parentDisposable) {
                openWorktreeProject = previousOpener
            }
        }

        internal fun installToolWindowPopupForTests(component: JComponent): PopupHandler? {
            return installToolWindowPopup(component)
        }

        internal fun applyDisabledActionTooltipsForTests(component: JComponent) {
            applyDisabledActionTooltips(component)
        }

        private fun installToolWindowPopup(component: JComponent): PopupHandler? {
            val action = ActionManager.getInstance().getAction(GitWorktreesToolWindowFactory.POPUP_ACTION_GROUP_ID)
            val actionGroup = action as? ActionGroup ?: return null
            return PopupHandler.installPopupMenu(
                component,
                actionGroup,
                GitWorktreesToolWindowFactory.POPUP_ACTION_GROUP_ID,
                DisabledActionTooltipListener,
            )
        }

        private object DisabledActionTooltipListener : PopupMenuListener {
            override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {
                val popup = e.source as? JPopupMenu ?: return
                applyDisabledActionTooltips(popup)
                SwingUtilities.invokeLater {
                    applyDisabledActionTooltips(popup)
                }
            }

            override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) = Unit

            override fun popupMenuCanceled(e: PopupMenuEvent) = Unit
        }

        private fun applyDisabledActionTooltips(component: JComponent) {
            if (component is JMenuItem) {
                val description = disabledActionDescription(component)
                component.toolTipText = description?.takeIf { !component.isEnabled && it.isNotBlank() }
            }
            component.components
                .filterIsInstance<JComponent>()
                .forEach(::applyDisabledActionTooltips)
        }

        private fun disabledActionDescription(component: JMenuItem): String? {
            val clientDescription = component.getClientProperty(ACTION_DESCRIPTION_PROPERTY) as? String
            if (!clientDescription.isNullOrBlank()) return clientDescription

            var currentClass: Class<*>? = component.javaClass
            while (currentClass != null) {
                val field = currentClass.declaredFields.firstOrNull { it.name == "description" }
                if (field != null) {
                    field.isAccessible = true
                    return field.get(component) as? String
                }
                currentClass = currentClass.superclass
            }
            return null
        }

        private fun relativeWorktreeLocation(repository: GitRepository, worktree: WorktreeInfo): String {
            val relativePath = FileUtil.getRelativePath(repository.root.path, worktree.path, '/')
            return relativePath
                ?.takeIf {
                    it.isNotBlank() &&
                        it != RELATIVE_CURRENT_DIR &&
                        it != RELATIVE_PARENT_DIR &&
                        !it.startsWith(RELATIVE_PARENT_PREFIX)
                }
                ?: worktree.path
        }
    }

    private fun repositoryRowIcon(repository: GitRepository): Icon {
        return if (isRepositoryCollapsed(repository)) {
            AllIcons.General.ArrowRight
        } else {
            AllIcons.General.ArrowDown
        }
    }
}
