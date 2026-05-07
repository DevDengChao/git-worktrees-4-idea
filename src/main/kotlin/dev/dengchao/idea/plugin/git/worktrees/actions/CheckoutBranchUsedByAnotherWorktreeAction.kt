package dev.dengchao.idea.plugin.git.worktrees.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import dev.dengchao.idea.plugin.git.worktrees.Gw4iBundle

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
        val context = BranchUsedByWorktreeContextResolver.fromBranchPopupEvent(e) ?: return
        GitWorktreesBranchActions.checkout(context)
    }

    private fun resolveContext(e: AnActionEvent): BranchUsedByWorktreeContext? =
        BranchUsedByWorktreeContextResolver.fromBranchPopupEvent(e)
}
