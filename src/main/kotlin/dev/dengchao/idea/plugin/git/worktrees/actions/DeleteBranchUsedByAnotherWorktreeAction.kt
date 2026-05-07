package dev.dengchao.idea.plugin.git.worktrees.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import dev.dengchao.idea.plugin.git.worktrees.Gw4iBundle
import dev.dengchao.idea.plugin.git.worktrees.services.DeleteWorktreeBranchDecision
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesOperationsService

class DeleteBranchUsedByAnotherWorktreeAction : DumbAwareAction(
    Gw4iBundle.message("action.GitWorktrees.Branch.DeleteUsedByWorktree.text"),
    Gw4iBundle.message("action.GitWorktrees.Branch.DeleteUsedByWorktree.description"),
    null,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = resolveContext(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val context = BranchUsedByWorktreeContextResolver.fromBranchPopupEvent(e) ?: return
        val service = GitWorktreesOperationsService.getInstance(project)

        val decision = service.askBranchDeletionDecision(context.branchName, context.worktree.path)
        if (decision == DeleteWorktreeBranchDecision.CANCEL) return

        service.removeWorktreeWithBranchDecisionAsync(
            context.repository,
            context.branchName,
            context.worktree.path,
            decision,
        )
    }

    private fun resolveContext(e: AnActionEvent): BranchUsedByWorktreeContext? =
        BranchUsedByWorktreeContextResolver.fromBranchPopupEvent(e)
}
