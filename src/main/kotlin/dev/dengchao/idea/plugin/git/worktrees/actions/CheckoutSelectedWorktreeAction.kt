package dev.dengchao.idea.plugin.git.worktrees.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import dev.dengchao.idea.plugin.git.worktrees.Gw4iBundle
import dev.dengchao.idea.plugin.git.worktrees.model.WorktreeInfo
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesOperationsService
import dev.dengchao.idea.plugin.git.worktrees.ui.GitWorktreesDataKeys
import git4idea.repo.GitRepository

class CheckoutSelectedWorktreeAction : DumbAwareAction(
    Gw4iBundle.message("action.GitWorktrees.Checkout.text"),
    Gw4iBundle.message("action.GitWorktrees.Checkout.description"),
    AllIcons.Actions.CheckOut,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val repository = e.getData(GitWorktreesDataKeys.CURRENT_REPOSITORY)
        val worktree = e.getData(GitWorktreesDataKeys.SELECTED_WORKTREE)
        val disabledReason = checkoutDisabledReason(repository, worktree)

        e.presentation.isVisible = repository != null && worktree != null
        e.presentation.isEnabled = disabledReason == null
        e.presentation.description =
            disabledReason ?: Gw4iBundle.message("action.GitWorktrees.Checkout.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (!PaidActionGuard.checkAndNotify(project)) return
        val repository = e.getData(GitWorktreesDataKeys.CURRENT_REPOSITORY) ?: return
        val worktree = e.getData(GitWorktreesDataKeys.SELECTED_WORKTREE) ?: return
        val branchName = worktree.branchName ?: return
        val panel = e.getData(GitWorktreesDataKeys.PANEL)

        GitWorktreesOperationsService.getInstance(project)
            .checkoutBranchIgnoringOtherWorktreesAsync(
                repository,
                branchName,
                afterCompletion = { panel?.reload() },
            )
    }

    private fun checkoutDisabledReason(repository: GitRepository?, worktree: WorktreeInfo?): String? {
        if (repository == null || worktree == null) return Gw4iBundle.message("action.GitWorktrees.Checkout.disabled.no.selection")
        if (worktree.isCurrent) return Gw4iBundle.message("action.GitWorktrees.Checkout.disabled.current.worktree")

        worktree.branchName ?: return Gw4iBundle.message("action.GitWorktrees.Checkout.disabled.detached")
        return null
    }
}
