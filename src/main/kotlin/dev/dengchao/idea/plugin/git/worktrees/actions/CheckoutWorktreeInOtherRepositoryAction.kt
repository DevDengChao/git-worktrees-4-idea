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

        e.presentation.isEnabledAndVisible = isEnabledFor(worktree, repository)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getRequiredData(CommonDataKeys.PROJECT)
        val worktree = e.getData(GitWorktreesDataKeys.SELECTED_WORKTREE) ?: return
        val repository = getCurrentRepository(project) ?: return

        if (!isEnabledFor(worktree, repository)) return

        val branchName = worktree.branchName ?: return
        GitWorktreesOperationsService.getInstance(project)
            .checkoutWorktreeBranch(repository, branchName)
    }

    private fun isEnabledFor(worktree: WorktreeInfo?, repository: GitRepository?): Boolean {
        if (worktree == null || repository == null) return false
        if (worktree.isCurrent) return false  // Already checked out here

        val branchName = worktree.branchName ?: return false  // Detached HEAD not supported
        return repository.currentBranchName != branchName
    }

    private fun getCurrentRepository(project: Project): GitRepository? {
        val service = GitWorktreesOperationsService.getInstance(project)
        val repositories = service.repositories()
        // For single-repo projects, return the only repo
        // For multi-repo projects, return the top-level one
        return repositories.firstOrNull()
    }
}
