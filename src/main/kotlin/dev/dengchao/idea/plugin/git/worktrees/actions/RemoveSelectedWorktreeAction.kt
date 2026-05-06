package dev.dengchao.idea.plugin.git.worktrees.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.MessageDialogBuilder
import dev.dengchao.idea.plugin.git.worktrees.Gw4iBundle
import dev.dengchao.idea.plugin.git.worktrees.services.BulkRemoveWorktreesTarget
import dev.dengchao.idea.plugin.git.worktrees.services.DeleteWorktreeBranchDecision
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesOperationsService
import dev.dengchao.idea.plugin.git.worktrees.ui.GitWorktreesDataKeys

class RemoveSelectedWorktreeAction : DumbAwareAction(
    Gw4iBundle.message("action.GitWorktrees.Remove.text"),
    Gw4iBundle.message("action.GitWorktrees.Remove.description"),
    null,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val selectedWorktrees = e.getData(GitWorktreesDataKeys.SELECTED_WORKTREES).orEmpty()
        if (selectedWorktrees.isNotEmpty() && e.getData(GitWorktreesDataKeys.SELECTED_WORKTREE) == null) {
            e.presentation.isEnabledAndVisible = selectedWorktrees.any { !it.worktree.isMain }
            return
        }

        val repository = e.getData(GitWorktreesDataKeys.CURRENT_REPOSITORY)
        val worktree = e.getData(GitWorktreesDataKeys.SELECTED_WORKTREE)
        e.presentation.isEnabledAndVisible = repository != null && worktree != null && !worktree.isMain
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val panel = e.getData(GitWorktreesDataKeys.PANEL)
        val service = GitWorktreesOperationsService.getInstance(project)
        val selectedWorktrees = e.getData(GitWorktreesDataKeys.SELECTED_WORKTREES).orEmpty()
        if (selectedWorktrees.isNotEmpty() && e.getData(GitWorktreesDataKeys.SELECTED_WORKTREE) == null) {
            val targets = selectedWorktrees
                .filterNot { it.worktree.isMain }
                .map { BulkRemoveWorktreesTarget(it.repository, it.worktree) }
            if (targets.isEmpty()) return

            val decision = showBulkRemovalDialog(project, targets.size)
            if (decision == DeleteWorktreeBranchDecision.CANCEL) return

            service.removeWorktreesWithBranchDecisionAsync(
                targets,
                decision,
                afterCompletion = { panel?.reload() },
            )
            return
        }

        val repository = e.getData(GitWorktreesDataKeys.CURRENT_REPOSITORY) ?: return
        val worktree = e.getData(GitWorktreesDataKeys.SELECTED_WORKTREE) ?: return

        // Check if the worktree's branch is being used only by this worktree
        // If branch name exists and could be deleted, offer three options
        val branchName = worktree.branchName
        if (branchName != null && !worktree.isMain) {
            // Check if we should offer to delete the branch too
            val decision = showRemovalDialog(project, branchName, worktree.path)
            when (decision) {
                DeleteWorktreeBranchDecision.CANCEL -> return
                DeleteWorktreeBranchDecision.DELETE_WORKTREE_ONLY -> {
                    // Only remove worktree, keep branch
                    service.removeWorktreeWithBranchDecisionAsync(
                        repository,
                        branchName,
                        worktree.path,
                        decision,
                        afterCompletion = { panel?.reload() },
                    )
                }
                DeleteWorktreeBranchDecision.DELETE_WORKTREE_AND_BRANCH -> {
                    // Remove both worktree and branch
                    service.removeWorktreeWithBranchDecisionAsync(
                        repository,
                        branchName,
                        worktree.path,
                        decision,
                        afterCompletion = { panel?.reload() },
                    )
                }
            }
        } else {
            // Main worktree or detached - just remove the worktree
            val confirmed = MessageDialogBuilder.yesNo(
                Gw4iBundle.message("GitWorktrees.dialog.remove.worktree.title"),
                Gw4iBundle.message("GitWorktrees.dialog.remove.worktree.message", worktree.path),
            )
                .yesText(Gw4iBundle.message("GitWorktrees.dialog.remove.worktree.yes"))
                .ask(project)

            if (!confirmed) return

            service.removeWorktreeAsync(
                repository,
                worktree.path,
                afterCompletion = { panel?.reload() },
            )
        }
    }

    private fun showRemovalDialog(
        project: com.intellij.openapi.project.Project,
        branchName: String,
        worktreePath: String,
    ): DeleteWorktreeBranchDecision {
        val result = com.intellij.openapi.ui.Messages.showDialog(
            project,
            Gw4iBundle.message("GitWorktrees.dialog.remove.worktree.used.branch.message", branchName, worktreePath),
            Gw4iBundle.message("GitWorktrees.dialog.remove.worktree.used.branch.title"),
            arrayOf(
                Gw4iBundle.message("GitWorktrees.dialog.remove.worktree.used.branch.delete.both"),
                Gw4iBundle.message("GitWorktrees.dialog.remove.worktree.used.branch.delete.worktree.only"),
                com.intellij.CommonBundle.getCancelButtonText(),
            ),
            0,
            com.intellij.openapi.ui.Messages.getWarningIcon(),
        )

        return when (result) {
            0 -> DeleteWorktreeBranchDecision.DELETE_WORKTREE_AND_BRANCH
            1 -> DeleteWorktreeBranchDecision.DELETE_WORKTREE_ONLY
            else -> DeleteWorktreeBranchDecision.CANCEL
        }
    }

    private fun showBulkRemovalDialog(
        project: com.intellij.openapi.project.Project,
        worktreeCount: Int,
    ): DeleteWorktreeBranchDecision {
        val result = com.intellij.openapi.ui.Messages.showDialog(
            project,
            Gw4iBundle.message("GitWorktrees.dialog.remove.worktrees.message", worktreeCount),
            Gw4iBundle.message("GitWorktrees.dialog.remove.worktrees.title"),
            arrayOf(
                Gw4iBundle.message("GitWorktrees.dialog.remove.worktrees.delete.worktrees.only"),
                Gw4iBundle.message("GitWorktrees.dialog.remove.worktrees.delete.with.branches"),
                com.intellij.CommonBundle.getCancelButtonText(),
            ),
            0,
            com.intellij.openapi.ui.Messages.getWarningIcon(),
        )

        return when (result) {
            0 -> DeleteWorktreeBranchDecision.DELETE_WORKTREE_ONLY
            1 -> DeleteWorktreeBranchDecision.DELETE_WORKTREE_AND_BRANCH
            else -> DeleteWorktreeBranchDecision.CANCEL
        }
    }
}
