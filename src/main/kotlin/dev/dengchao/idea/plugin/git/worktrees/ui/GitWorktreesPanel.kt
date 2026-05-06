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
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import dev.dengchao.idea.plugin.git.worktrees.Gw4iBundle
import dev.dengchao.idea.plugin.git.worktrees.model.WorktreeInfo
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesOperationsService
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import java.nio.file.Path
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.ListSelectionModel

class GitWorktreesPanel(private val project: Project) : SimpleToolWindowPanel(true, true), DataProvider, Disposable {

    private val service = GitWorktreesOperationsService.getInstance(project)
    private val model = DefaultListModel<WorktreeListItem>()
    private val list = JBList(model)

    init {
        initToolbar()
        initList()
        subscribeToRepositoryUpdates()
        reload()
    }

    override fun dispose() = Unit

    fun reload() {
        val selectedPath = selectedWorktree()?.path
        object : Task.Backgroundable(project, Gw4iBundle.message("GitWorktrees.task.load.worktrees.title"), true) {
            private var snapshot: List<RepositoryWorktreesSnapshot> = emptyList()

            override fun run(indicator: ProgressIndicator) {
                snapshot = loadSnapshot()
            }

            override fun onSuccess() {
                applySnapshot(snapshot, selectedPath)
            }
        }.queue()
    }

    internal fun reloadSynchronouslyForTests() {
        applySnapshot(loadSnapshot(), selectedWorktree()?.path)
    }

    private fun applySnapshot(snapshot: List<RepositoryWorktreesSnapshot>, selectedPath: String?) {
        model.removeAllElements()
        snapshot.forEach { repositorySnapshot ->
            model.addElement(RepositoryItem(repositorySnapshot.repository))
            repositorySnapshot.worktrees.forEach { worktree ->
                model.addElement(WorkingTreeItem(repositorySnapshot.repository, worktree))
            }
        }

        if (model.isEmpty) return
        val targetIndex = (0 until model.size)
            .firstOrNull { index ->
                val item = model[index] as? WorkingTreeItem ?: return@firstOrNull false
                item.worktree.path == selectedPath
            }
            ?: firstWorktreeIndex()

        list.selectedIndex = targetIndex ?: 0
    }

    private fun loadSnapshot(): List<RepositoryWorktreesSnapshot> {
        return service.repositories().map { repository ->
            RepositoryWorktreesSnapshot(repository, service.worktrees(repository))
        }
    }

    internal fun selectRowForTests(index: Int) {
        list.selectedIndex = index
    }

    fun selectedRepository(): GitRepository? {
        return list.selectedValue?.repository
    }

    fun selectedWorktree(): WorktreeInfo? {
        return (list.selectedValue as? WorkingTreeItem)?.worktree
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

    private fun initList() {
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.emptyText.text = Gw4iBundle.message("toolwindow.GitWorktrees.empty")
        list.cellRenderer = WorktreeRenderer(project)

        PopupHandler.installPopupMenu(
            list,
            GitWorktreesToolWindowFactory.POPUP_ACTION_GROUP_ID,
            GitWorktreesToolWindowFactory.POPUP_ACTION_GROUP_ID,
        )

        list.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    openSelectedWorktree()
                }
            }
        })

        setContent(ScrollPaneFactory.createScrollPane(list))
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

    private fun firstWorktreeIndex(): Int? {
        return (0 until model.size).firstOrNull { index -> model[index] is WorkingTreeItem }
    }

    private sealed interface WorktreeListItem {
        val repository: GitRepository
    }

    private data class RepositoryItem(override val repository: GitRepository) : WorktreeListItem {
        fun presentationName(project: Project): String {
            val basePath = project.basePath ?: return repository.root.name
            val relativePath = FileUtil.getRelativePath(basePath, repository.root.path, '/')
            return relativePath?.takeIf { it.isNotBlank() && it != "." } ?: repository.root.name
        }
    }

    private data class WorkingTreeItem(
        override val repository: GitRepository,
        val worktree: WorktreeInfo,
    ) : WorktreeListItem

    private data class RepositoryWorktreesSnapshot(
        val repository: GitRepository,
        val worktrees: List<WorktreeInfo>,
    )

    private class WorktreeRenderer(private val project: Project) : ColoredListCellRenderer<WorktreeListItem>() {
        override fun customizeCellRenderer(
            list: JList<out WorktreeListItem?>,
            value: WorktreeListItem?,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean,
        ) {
            when (value) {
                is RepositoryItem -> {
                    icon = AllIcons.Nodes.Folder
                    append(value.presentationName(project), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                    append("  ")
                    append(FileUtil.getLocationRelativeToUserHome(value.repository.root.path), SimpleTextAttributes.GRAY_ATTRIBUTES)
                    border = JBUI.Borders.empty(2, 4, 2, 4)
                }

                is WorkingTreeItem -> {
                    icon = if (value.worktree.isCurrent) AllIcons.Actions.Checked else AllIcons.Empty
                    append(value.worktree.name, if (value.worktree.isMain) SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES else SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    append("  ")
                    append(value.worktree.branchName ?: "detached", SimpleTextAttributes.GRAY_ATTRIBUTES)
                    border = JBUI.Borders.empty(2, 16, 2, 4)
                }

                null -> Unit
            }
        }
    }
}
