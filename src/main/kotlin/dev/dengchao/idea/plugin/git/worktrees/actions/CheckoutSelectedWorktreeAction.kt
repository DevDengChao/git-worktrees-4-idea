package dev.dengchao.idea.plugin.git.worktrees.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import dev.dengchao.idea.plugin.git.worktrees.Gw4iBundle
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesOperationsService
import dev.dengchao.idea.plugin.git.worktrees.ui.GitWorktreesDataKeys

class CheckoutSelectedWorktreeAction : DumbAwareAction(
    Gw4iBundle.message("action.GitWorktrees.Checkout.text"),
    Gw4iBundle.message("action.GitWorktrees.Checkout.description"),
    AllIcons.Actions.CheckOut,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val repository = e.getData(GitWorktreesDataKeys.CURRENT_REPOSITORY)
        val worktree = e.getData(GitWorktreesDataKeys.SELECTED_WORKTREE)
        val branchName = worktree?.branchName

        e.presentation.isEnabledAndVisible =
            repository != null && worktree != null && branchName != null && !worktree.isCurrent && repository.currentBranchName != branchName
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val repository = e.getData(GitWorktreesDataKeys.CURRENT_REPOSITORY) ?: return
        val worktree = e.getData(GitWorktreesDataKeys.SELECTED_WORKTREE) ?: return
        val branchName = worktree.branchName ?: return

        GitWorktreesOperationsService.getInstance(project)
            .checkoutBranchIgnoringOtherWorktrees(repository, branchName)
        e.getData(GitWorktreesDataKeys.PANEL)?.reload()
    }
}
