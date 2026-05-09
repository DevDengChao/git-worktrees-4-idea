package dev.dengchao.idea.plugin.git.worktrees.actions

import dev.dengchao.idea.plugin.git.worktrees.services.DeleteWorktreeBranchDecision
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesOperationsService

internal object GitWorktreesBranchActions {
    fun checkout(context: BranchUsedByWorktreeContext) {
        if (!PaidActionGuard.checkAndNotify(context.repository.project)) return
        val service = GitWorktreesOperationsService.getInstance(context.repository.project)
        if (!service.askCheckoutUsedByWorktreeConfirmation(context.branchName, context.worktree.path)) return

        service.checkoutBranchIgnoringOtherWorktreesAsync(context.repository, context.branchName)
    }

    fun delete(context: BranchUsedByWorktreeContext) {
        if (!PaidActionGuard.checkAndNotify(context.repository.project)) return
        val service = GitWorktreesOperationsService.getInstance(context.repository.project)
        val decision = service.askBranchDeletionDecision(context.branchName, context.worktree.path)
        if (decision == DeleteWorktreeBranchDecision.CANCEL) return

        service.removeWorktreeWithBranchDecisionAsync(
            context.repository,
            context.branchName,
            context.worktree.path,
            decision,
        )
    }
}
