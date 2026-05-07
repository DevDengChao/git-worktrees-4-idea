package dev.dengchao.idea.plugin.git.worktrees.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import dev.dengchao.idea.plugin.git.worktrees.Gw4iBundle
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesOperationsService

class CheckoutBranchUsedByAnotherWorktreeAction : DumbAwareAction(
    Gw4iBundle.message("action.GitWorktrees.Branch.CheckoutIgnoreOtherWorktrees.text"),
    Gw4iBundle.message("action.GitWorktrees.Branch.CheckoutIgnoreOtherWorktrees.description"),
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

        if (!service.askCheckoutUsedByWorktreeConfirmation(context.branchName, context.worktree.path)) return

        service.checkoutBranchIgnoringOtherWorktreesAsync(context.repository, context.branchName)
    }

    private fun resolveContext(e: AnActionEvent): BranchUsedByWorktreeContext? =
        BranchUsedByWorktreeContextResolver.fromBranchPopupEvent(e)
}
