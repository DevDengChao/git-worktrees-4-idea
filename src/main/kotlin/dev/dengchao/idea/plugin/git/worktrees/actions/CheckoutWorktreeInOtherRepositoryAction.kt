package dev.dengchao.idea.plugin.git.worktrees.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import dev.dengchao.idea.plugin.git.worktrees.Gw4iBundle
import dev.dengchao.idea.plugin.git.worktrees.model.WorktreeInfo
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesOperationsService
import dev.dengchao.idea.plugin.git.worktrees.ui.GitWorktreesDataKeys
import git4idea.repo.GitRepository

/**
 * Action to checkout a worktree's branch in the current repository.
 * This allows switching to a branch that is currently used by another worktree,
 * using --ignore-other-worktrees flag.
 */
class CheckoutWorktreeInOtherRepositoryAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val worktree = e.getData(GitWorktreesDataKeys.SELECTED_WORKTREE)
        val repository = e.getData(CommonDataKeys.PROJECT)?.let { getCurrentRepository(it) }
        val disabledReason = checkoutDisabledReason(worktree, repository)

        e.presentation.isVisible = worktree != null
        e.presentation.isEnabled = disabledReason == null
        e.presentation.description =
            disabledReason ?: Gw4iBundle.message("action.GitWorktrees.CheckoutInOtherRepo.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val worktree = e.getData(GitWorktreesDataKeys.SELECTED_WORKTREE) ?: return
        val repository = getCurrentRepository(project) ?: return

        if (!isEnabledFor(worktree, repository)) return

        val branchName = worktree.branchName ?: return
        GitWorktreesOperationsService.getInstance(project)
            .checkoutWorktreeBranch(repository, branchName)
    }

    private fun isEnabledFor(worktree: WorktreeInfo?, repository: GitRepository?): Boolean {
        return checkoutDisabledReason(worktree, repository) == null
    }

    private fun checkoutDisabledReason(worktree: WorktreeInfo?, repository: GitRepository?): String? {
        if (worktree == null) return Gw4iBundle.message("action.GitWorktrees.CheckoutInOtherRepo.disabled.no.selection")
        if (repository == null) {
            return Gw4iBundle.message("action.GitWorktrees.CheckoutInOtherRepo.disabled.multiple.repositories")
        }
        if (worktree.isCurrent) return Gw4iBundle.message("action.GitWorktrees.CheckoutInOtherRepo.disabled.current.worktree")

        val branchName = worktree.branchName
            ?: return Gw4iBundle.message("action.GitWorktrees.CheckoutInOtherRepo.disabled.detached")
        if (repository.currentBranchName == branchName) {
            return Gw4iBundle.message("action.GitWorktrees.CheckoutInOtherRepo.disabled.branch.current", branchName)
        }

        return null
    }

    private fun getCurrentRepository(project: Project): GitRepository? {
        return GitWorktreesOperationsService.getInstance(project).uniqueTopLevelRepository()
    }
}
