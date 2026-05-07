package dev.dengchao.idea.plugin.git.worktrees.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import dev.dengchao.idea.plugin.git.worktrees.Gw4iBundle
import dev.dengchao.idea.plugin.git.worktrees.model.WorktreeInfo
import dev.dengchao.idea.plugin.git.worktrees.services.DeleteWorktreeBranchDecision
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesOperationsService
import git4idea.GitLocalBranch
import git4idea.repo.GitRepository

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
        val context = resolveContext(e) ?: return
        val service = GitWorktreesOperationsService.getInstance(project)

        val decision = service.askBranchDeletionDecision(context.branch.name, context.worktree.path)
        if (decision == DeleteWorktreeBranchDecision.CANCEL) return

        service.removeWorktreeWithBranchDecisionAsync(
            context.repository,
            context.branch.name,
            context.worktree.path,
            decision,
        )
    }

    private fun resolveContext(e: AnActionEvent): BranchContext? {
        val project = e.project ?: return null
        val branch = e.getData(GitBranchActionDataKeys.SELECTED_REF) as? GitLocalBranch ?: return null
        val repository = e.getData(GitBranchActionDataKeys.SELECTED_REPOSITORY)
            ?: e.getData(GitBranchActionDataKeys.AFFECTED_REPOSITORIES)?.singleOrNull()
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
