package dev.dengchao.idea.plugin.git.worktrees.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.vcs.git.shared.actions.GitSingleRefActions
import dev.dengchao.idea.plugin.git.worktrees.Gw4iBundle
import dev.dengchao.idea.plugin.git.worktrees.model.WorktreeInfo
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesOperationsService
import git4idea.GitLocalBranch
import git4idea.actions.branch.GitBranchActionsDataKeys
import git4idea.repo.GitRepository

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
        val context = resolveContext(e) ?: return

        val confirmed = MessageDialogBuilder.yesNo(
            Gw4iBundle.message("GitWorktrees.dialog.checkout.used.by.worktree.title"),
            Gw4iBundle.message(
                "GitWorktrees.dialog.checkout.used.by.worktree.message",
                context.branch.name,
                context.worktree.path,
            ),
        )
            .yesText(Gw4iBundle.message("GitWorktrees.dialog.checkout.used.by.worktree.yes"))
            .ask(project)

        if (!confirmed) return

        GitWorktreesOperationsService.getInstance(project)
            .checkoutBranchIgnoringOtherWorktreesAsync(context.repository, context.branch.name)
    }

    private fun resolveContext(e: AnActionEvent): BranchContext? {
        val project = e.project ?: return null
        val branch = e.getData(GitSingleRefActions.SELECTED_REF_DATA_KEY) as? GitLocalBranch ?: return null
        val repository = e.getData(GitBranchActionsDataKeys.SELECTED_REPOSITORY)
            ?: e.getData(GitBranchActionsDataKeys.AFFECTED_REPOSITORIES)?.singleOrNull()
            ?: return null
        if (repository.currentBranchName == branch.name) return null

        val worktree = GitWorktreesOperationsService.getInstance(project)
            .findLinkedWorktreeForBranch(repository, branch.name)
            ?: return null
        return BranchContext(repository, branch, worktree)
    }

    private data class BranchContext(
        val repository: GitRepository,
        val branch: GitLocalBranch,
        val worktree: WorktreeInfo,
    )
}
