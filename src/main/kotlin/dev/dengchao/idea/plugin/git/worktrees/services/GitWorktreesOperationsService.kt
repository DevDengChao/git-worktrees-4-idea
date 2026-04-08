package dev.dengchao.idea.plugin.git.worktrees.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.vcs.VcsNotifier
import dev.dengchao.idea.plugin.git.worktrees.Gw4iBundle
import dev.dengchao.idea.plugin.git.worktrees.model.WorktreeInfo
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandler
import git4idea.commands.GitLocalChangesWouldBeOverwrittenDetector
import git4idea.commands.GitUntrackedFilesOverwrittenByOperationDetector
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

/**
 * Decision made by user when trying to delete a branch that is used by a worktree.
 */
enum class DeleteWorktreeBranchDecision {
    CANCEL,
    DELETE_WORKTREE_ONLY,
    DELETE_WORKTREE_AND_BRANCH
}

@Service(Service.Level.PROJECT)
class GitWorktreesOperationsService(private val project: Project) {

    companion object {
        private const val CHECKOUT_FAILED_ID = "gw4i.checkout.failed"
        private const val WORKTREE_DELETE_FAILED_ID = "gw4i.worktree.delete.failed"
        private const val BRANCH_DELETE_FAILED_ID = "gw4i.branch.delete.failed"

        fun getInstance(project: Project): GitWorktreesOperationsService {
            return project.getService(GitWorktreesOperationsService::class.java)
        }
    }

    fun repositories(): List<GitRepository> {
        val repositoryManager = GitRepositoryManager.getInstance(project)
        return repositoryManager.sortByDependency(repositoryManager.repositories)
    }

    fun worktrees(repository: GitRepository): List<WorktreeInfo> {
        val result = runListWorktrees(repository)
        if (!result.success()) return emptyList()

        return parseWorktrees(repository, result.output)
    }

    fun findLinkedWorktreeForBranch(repository: GitRepository, branchName: String): WorktreeInfo? {
        return worktrees(repository).firstOrNull { it.branchName == branchName && !it.isCurrent }
    }

    fun checkoutBranchIgnoringOtherWorktrees(repository: GitRepository, branchName: String): Boolean {
        return checkoutBranchWithConflictHandling(repository, branchName)
    }

    fun removeWorktree(repository: GitRepository, worktreePath: String, notifyResult: Boolean = true): Boolean {
        val result = runRemoveWorktree(repository, worktreePath)
        if (!result.success()) {
            notifyDeleteWorktreeFailed(result)
            return false
        }

        refreshRepository(repository)
        if (notifyResult) {
            VcsNotifier.getInstance(project).notifySuccess(
                "gw4i.worktree.delete.success",
                "",
                Gw4iBundle.message("GitWorktrees.notification.worktree.delete.success", worktreePath),
            )
        }
        return true
    }

    fun deleteBranch(
        repository: GitRepository,
        branchName: String,
        force: Boolean = true,
        notifyResult: Boolean = true,
    ): Boolean {
        val result = runDeleteBranch(repository, branchName, force)
        if (!result.success()) {
            notifyDeleteBranchFailed(result)
            return false
        }

        refreshRepository(repository)
        if (notifyResult) {
            VcsNotifier.getInstance(project).notifySuccess(
                "gw4i.branch.delete.success",
                "",
                Gw4iBundle.message("GitWorktrees.notification.branch.delete.success", branchName),
            )
        }
        return true
    }

    fun refreshRepository(repository: GitRepository) {
        repository.update()
    }

    /**
     * Checkout a worktree's branch in the current repository.
     * Offers to force checkout if there are conflicting local changes.
     */
    fun checkoutWorktreeBranch(
        repository: GitRepository,
        branchName: String,
        notifyResult: Boolean = true,
    ): Boolean {
        return checkoutBranchWithConflictHandling(repository, branchName, notifyResult)
    }

    /**
     * Handle branch deletion when it's used by a worktree.
     * Shows a dialog offering three options:
     * - Cancel
     * - Delete worktree only (keep the branch)
     * - Delete both worktree and branch
     */
    fun handleBranchDeletionWithWorktree(
        repository: GitRepository,
        branchName: String,
        worktreePath: String,
    ): Boolean {
        val decision = showBranchUsedByWorktreeDialog(branchName, worktreePath)
        if (decision == DeleteWorktreeBranchDecision.CANCEL) {
            return false
        }

        // First, remove the worktree
        val removeWorktreeResult = runRemoveWorktree(repository, worktreePath)
        if (!removeWorktreeResult.success()) {
            notifyDeleteWorktreeFailed(removeWorktreeResult)
            return false
        }

        // If user chose to delete both, delete the branch too
        if (decision == DeleteWorktreeBranchDecision.DELETE_WORKTREE_AND_BRANCH) {
            return deleteBranch(repository, branchName, force = true, notifyResult = true)
        }

        // DELETE_WORKTREE_ONLY: worktree removed, branch kept
        refreshRepository(repository)
        VcsNotifier.getInstance(project).notifySuccess(
            "gw4i.worktree.delete.success",
            "",
            Gw4iBundle.message("GitWorktrees.notification.worktree.delete.success", worktreePath),
        )
        return true
    }

    private fun checkoutBranchWithConflictHandling(
        repository: GitRepository,
        branchName: String,
        notifyResult: Boolean = true,
    ): Boolean {
        val initialResult = runCheckout(repository, branchName, force = false)
        if (initialResult.commandResult.success()) {
            refreshRepository(repository)
            if (notifyResult) {
                VcsNotifier.getInstance(project).notifySuccess(
                    "gw4i.checkout.success",
                    "",
                    Gw4iBundle.message("GitWorktrees.notification.checkout.success", branchName),
                )
            }
            return true
        }

        if (!initialResult.hasConflictingChanges) {
            notifyCheckoutFailed(initialResult.commandResult)
            return false
        }

        val confirmed = MessageDialogBuilder.yesNo(
            Gw4iBundle.message("GitWorktrees.dialog.checkout.worktree.title"),
            Gw4iBundle.message("GitWorktrees.dialog.checkout.worktree.message", branchName),
        )
            .yesText(Gw4iBundle.message("GitWorktrees.dialog.checkout.worktree.yes"))
            .ask(project)

        if (!confirmed) return false

        val forceResult = runCheckout(repository, branchName, force = true)
        if (!forceResult.commandResult.success()) {
            notifyCheckoutFailed(forceResult.commandResult)
            return false
        }

        refreshRepository(repository)
        if (notifyResult) {
            VcsNotifier.getInstance(project).notifySuccess(
                "gw4i.checkout.success",
                "",
                Gw4iBundle.message("GitWorktrees.notification.checkout.success", branchName),
            )
        }
        return true
    }

    private fun showBranchUsedByWorktreeDialog(
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
            0, // default button
            com.intellij.openapi.ui.Messages.getWarningIcon(),
        )

        return when (result) {
            0 -> DeleteWorktreeBranchDecision.DELETE_WORKTREE_AND_BRANCH  // YES
            1 -> DeleteWorktreeBranchDecision.DELETE_WORKTREE_ONLY        // NO
            else -> DeleteWorktreeBranchDecision.CANCEL                    // CANCEL
        }
    }

    private fun runListWorktrees(repository: GitRepository): GitCommandResult {
        val handler = GitLineHandler(repository.project, repository.root, GitCommand.WORKTREE)
        handler.setSilent(false)
        handler.setStdoutSuppressed(false)
        handler.setStderrSuppressed(false)
        handler.addParameters("list", "--porcelain")
        return Git.getInstance().runCommand(handler)
    }

    private fun runCheckout(repository: GitRepository, branchName: String, force: Boolean): CheckoutResult {
        val localChangesDetector = GitLocalChangesWouldBeOverwrittenDetector(
            repository.root,
            GitLocalChangesWouldBeOverwrittenDetector.Operation.CHECKOUT,
        )
        val untrackedFilesDetector = GitUntrackedFilesOverwrittenByOperationDetector(repository.root)

        val handler = GitLineHandler(repository.project, repository.root, GitCommand.CHECKOUT)
        handler.setSilent(false)
        handler.setStdoutSuppressed(false)
        if (force) {
            handler.addParameters("--force")
        }
        handler.addParameters("--ignore-other-worktrees", branchName)
        handler.endOptions()
        handler.addLineListener(localChangesDetector)
        handler.addLineListener(untrackedFilesDetector)

        val result = Git.getInstance().runCommand(handler)
        val hasConflict = localChangesDetector.isDetected || untrackedFilesDetector.isDetected
        return CheckoutResult(result, hasConflict)
    }

    private fun runRemoveWorktree(repository: GitRepository, worktreePath: String): GitCommandResult {
        val handler = GitLineHandler(repository.project, repository.root, GitCommand.WORKTREE)
        handler.setSilent(false)
        handler.setStdoutSuppressed(false)
        handler.setStderrSuppressed(false)
        handler.addParameters("remove", worktreePath, "--force")
        return Git.getInstance().runCommand(handler)
    }

    private fun runDeleteBranch(repository: GitRepository, branchName: String, force: Boolean): GitCommandResult {
        val handler = GitLineHandler(repository.project, repository.root, GitCommand.BRANCH)
        handler.setSilent(false)
        handler.setStdoutSuppressed(false)
        handler.setStderrSuppressed(false)
        handler.addParameters(if (force) "-D" else "-d", branchName)
        return Git.getInstance().runCommand(handler)
    }

    private fun parseWorktrees(repository: GitRepository, lines: List<String>): List<WorktreeInfo> {
        val entries = mutableListOf<WorktreeInfo>()

        var path: String? = null
        var branchName: String? = null
        var isLocked = false
        var isPrunable = false

        fun flush() {
            val currentPath = path ?: return
            val isCurrent = normalizePath(currentPath) == normalizePath(repository.root.path)
            val isMain = entries.isEmpty()
            entries += WorktreeInfo(
                path = currentPath,
                branchName = branchName,
                isMain = isMain,
                isCurrent = isCurrent,
                isLocked = isLocked,
                isPrunable = isPrunable,
            )

            path = null
            branchName = null
            isLocked = false
            isPrunable = false
        }

        lines.forEach { line ->
            when {
                line.isBlank() -> flush()
                line.startsWith("worktree ") -> {
                    flush()
                    path = line.removePrefix("worktree ").trim()
                }

                line.startsWith("branch ") -> {
                    val fullRef = line.removePrefix("branch ").trim()
                    branchName = fullRef.removePrefix("refs/heads/")
                }

                line == "detached" -> branchName = null
                line.startsWith("locked") -> isLocked = true
                line.startsWith("prunable") -> isPrunable = true
            }
        }
        flush()

        return entries
    }

    private fun normalizePath(path: String): String {
        return path.replace('\\', '/').trimEnd('/')
    }

    private fun notifyCheckoutFailed(result: GitCommandResult) {
        VcsNotifier.getInstance(project).notifyError(
            CHECKOUT_FAILED_ID,
            Gw4iBundle.message("GitWorktrees.notification.checkout.failed.title"),
            result.errorOutputAsHtmlString,
            true,
        )
    }

    private fun notifyDeleteWorktreeFailed(result: GitCommandResult) {
        VcsNotifier.getInstance(project).notifyError(
            WORKTREE_DELETE_FAILED_ID,
            Gw4iBundle.message("GitWorktrees.notification.worktree.delete.failed.title"),
            result.errorOutputAsHtmlString,
            true,
        )
    }

    private fun notifyDeleteBranchFailed(result: GitCommandResult) {
        VcsNotifier.getInstance(project).notifyError(
            BRANCH_DELETE_FAILED_ID,
            Gw4iBundle.message("GitWorktrees.notification.branch.delete.failed.title"),
            result.errorOutputAsHtmlString,
            true,
        )
    }

    private data class CheckoutResult(
        val commandResult: GitCommandResult,
        val hasConflictingChanges: Boolean,
    )
}
