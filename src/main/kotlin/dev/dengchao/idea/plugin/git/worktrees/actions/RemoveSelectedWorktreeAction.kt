package dev.dengchao.idea.plugin.git.worktrees.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.MessageDialogBuilder
import dev.dengchao.idea.plugin.git.worktrees.Gw4iBundle
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesOperationsService
import dev.dengchao.idea.plugin.git.worktrees.ui.GitWorktreesDataKeys

class RemoveSelectedWorktreeAction : DumbAwareAction(
    Gw4iBundle.message("action.GitWorktrees.Remove.text"),
    Gw4iBundle.message("action.GitWorktrees.Remove.description"),
    null,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val worktree = e.getData(GitWorktreesDataKeys.SELECTED_WORKTREE)
        e.presentation.isEnabledAndVisible = worktree != null && !worktree.isMain
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val repository = e.getData(GitWorktreesDataKeys.CURRENT_REPOSITORY) ?: return
        val worktree = e.getData(GitWorktreesDataKeys.SELECTED_WORKTREE) ?: return

        val confirmed = MessageDialogBuilder.yesNo(
            Gw4iBundle.message("GitWorktrees.dialog.remove.worktree.title"),
            Gw4iBundle.message("GitWorktrees.dialog.remove.worktree.message", worktree.path),
        )
            .yesText(Gw4iBundle.message("GitWorktrees.dialog.remove.worktree.yes"))
            .ask(project)

        if (!confirmed) return

        GitWorktreesOperationsService.getInstance(project).removeWorktree(repository, worktree.path)
        e.getData(GitWorktreesDataKeys.PANEL)?.reload()
    }
}
