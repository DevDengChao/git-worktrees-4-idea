package dev.dengchao.idea.plugin.git.worktrees.actions

import com.intellij.CommonBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.vcs.git.shared.actions.GitSingleRefActions
import dev.dengchao.idea.plugin.git.worktrees.Gw4iBundle
import dev.dengchao.idea.plugin.git.worktrees.model.WorktreeInfo
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesOperationsService
import git4idea.GitLocalBranch
import git4idea.actions.branch.GitBranchActionsDataKeys
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

        when (showDecisionDialog(project, context.branch.name, context.worktree.path)) {
            Decision.CANCEL -> Unit
            Decision.DELETE_WORKTREE_ONLY -> {
                if (!service.removeWorktree(context.repository, context.worktree.path, notifyResult = false)) return
                VcsNotifier.getInstance(project).notifySuccess(
                    "gw4i.worktree.delete.only.success",
                    "",
                    Gw4iBundle.message(
                        "GitWorktrees.notification.worktree.delete.only.success",
                        context.worktree.path,
                        context.branch.name,
                    ),
                )
            }

            Decision.DELETE_WORKTREE_AND_BRANCH -> {
                if (!service.removeWorktree(context.repository, context.worktree.path, notifyResult = false)) return
                service.deleteBranch(context.repository, context.branch.name, force = true, notifyResult = true)
            }
        }
    }

    private fun showDecisionDialog(project: Project, branchName: String, worktreePath: String): Decision {
        val result = Messages.showDialog(
            project,
            Gw4iBundle.message("GitWorktrees.dialog.delete.used.by.worktree.message", branchName, worktreePath),
            Gw4iBundle.message("GitWorktrees.dialog.delete.used.by.worktree.title"),
            arrayOf(
                Gw4iBundle.message("GitWorktrees.dialog.delete.used.by.worktree.worktree.and.branch"),
                Gw4iBundle.message("GitWorktrees.dialog.delete.used.by.worktree.worktree.only"),
                CommonBundle.getCancelButtonText(),
            ),
            0,
            Messages.getWarningIcon(),
        )

        return when (result) {
            0 -> Decision.DELETE_WORKTREE_AND_BRANCH
            1 -> Decision.DELETE_WORKTREE_ONLY
            else -> Decision.CANCEL
        }
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

    private enum class Decision {
        CANCEL,
        DELETE_WORKTREE_ONLY,
        DELETE_WORKTREE_AND_BRANCH,
    }
}
