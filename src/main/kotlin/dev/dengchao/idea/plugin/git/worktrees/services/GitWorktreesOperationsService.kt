package dev.dengchao.idea.plugin.git.worktrees.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vcs.VcsNotifier
import dev.dengchao.idea.plugin.git.worktrees.Gw4iBundle
import dev.dengchao.idea.plugin.git.worktrees.model.WorktreeInfo
import java.nio.file.Files
import java.nio.file.Path
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

data class BulkRemoveWorktreesTarget(
    val repository: GitRepository,
    val worktree: WorktreeInfo,
)

data class BulkRemoveWorktreesResult(
    val removedWorktrees: Int,
    val deletedBranches: Int,
    val leftoverCleanupPaths: List<String> = emptyList(),
)

@Service(Service.Level.PROJECT)
class GitWorktreesOperationsService(private val project: Project) {

    companion object {
        private const val CHECKOUT_FAILED_ID = "gw4i.checkout.failed"
        private const val WORKTREE_DELETE_FAILED_ID = "gw4i.worktree.delete.failed"
        private const val BRANCH_DELETE_FAILED_ID = "gw4i.branch.delete.failed"

        fun getInstance(project: Project): GitWorktreesOperationsService {
            return project.getService(GitWorktreesOperationsService::class.java)
        }

        fun uniqueTopLevelRepository(repositories: List<GitRepository>): GitRepository? {
            if (repositories.isEmpty()) return null
            if (repositories.size == 1) return repositories.first()

            val topLevelRepositories = repositories.filter { candidate ->
                repositories.none { other ->
                    other != candidate && VfsUtilCore.isAncestor(other.root, candidate.root, true)
                }
            }
            return topLevelRepositories.singleOrNull()
        }

        fun parseWorktrees(repository: GitRepository, lines: List<String>): List<WorktreeInfo> {
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

            return entries.sortedWith(compareByDescending<WorktreeInfo> { it.isMain }.thenBy { it.name })
        }

        private fun normalizePath(path: String): String {
            return path.replace('\\', '/').trimEnd('/')
        }
    }

    private var repositoriesProvider: () -> List<GitRepository> = ::defaultRepositories
    private var worktreesProvider: (GitRepository) -> List<WorktreeInfo> = ::defaultWorktrees
    private var branchDeletionDialogProvider: (String, String) -> DeleteWorktreeBranchDecision =
        ::showBranchUsedByWorktreeDialog
    private var checkoutConflictDialogProvider: (String) -> Boolean = ::showCheckoutConflictDialog
    private var checkoutRunner: (GitRepository, String, Boolean) -> CheckoutResult = ::runCheckout
    private var removeWorktreeRunner: (GitRepository, String) -> GitCommandResult = ::runRemoveWorktree
    private var deleteBranchRunner: (GitRepository, String, Boolean) -> GitCommandResult = ::runDeleteBranch
    private var backgroundTaskRunner: (String, () -> Unit, () -> Unit) -> Unit = ::queueBackgroundTask
    private var leftoverCleanupRunner: (List<String>) -> Unit = ::runLeftoverCleanupOnPooledThread

    fun repositories(): List<GitRepository> {
        return repositoriesProvider()
    }

    fun worktrees(repository: GitRepository): List<WorktreeInfo> {
        return worktreesProvider(repository)
    }

    internal fun overrideProvidersForTests(
        repositoriesProvider: () -> List<GitRepository>,
        worktreesProvider: (GitRepository) -> List<WorktreeInfo> = ::defaultWorktrees,
        parentDisposable: Disposable,
    ) {
        this.repositoriesProvider = repositoriesProvider
        this.worktreesProvider = worktreesProvider
        Disposer.register(parentDisposable) {
            this.repositoriesProvider = ::defaultRepositories
            this.worktreesProvider = ::defaultWorktrees
        }
    }

    internal fun overrideBranchDeletionDialogForTests(
        dialogProvider: (String, String) -> DeleteWorktreeBranchDecision,
        parentDisposable: Disposable,
    ) {
        this.branchDeletionDialogProvider = dialogProvider
        Disposer.register(parentDisposable) {
            this.branchDeletionDialogProvider = ::showBranchUsedByWorktreeDialog
        }
    }

    internal fun overrideCheckoutConflictDialogForTests(
        dialogProvider: (String) -> Boolean,
        parentDisposable: Disposable,
    ) {
        this.checkoutConflictDialogProvider = dialogProvider
        Disposer.register(parentDisposable) {
            this.checkoutConflictDialogProvider = ::showCheckoutConflictDialog
        }
    }

    internal fun overrideGitOperationsForTests(
        checkoutRunner: (GitRepository, String, Boolean) -> CheckoutResult = ::runCheckout,
        removeWorktreeRunner: (GitRepository, String) -> GitCommandResult = ::runRemoveWorktree,
        deleteBranchRunner: (GitRepository, String, Boolean) -> GitCommandResult = ::runDeleteBranch,
        parentDisposable: Disposable,
    ) {
        this.checkoutRunner = checkoutRunner
        this.removeWorktreeRunner = removeWorktreeRunner
        this.deleteBranchRunner = deleteBranchRunner
        Disposer.register(parentDisposable) {
            this.checkoutRunner = ::runCheckout
            this.removeWorktreeRunner = ::runRemoveWorktree
            this.deleteBranchRunner = ::runDeleteBranch
        }
    }

    internal fun overrideTaskRunnersForTests(
        backgroundTaskRunner: (String, () -> Unit, () -> Unit) -> Unit = ::queueBackgroundTask,
        leftoverCleanupRunner: (List<String>) -> Unit = ::runLeftoverCleanupOnPooledThread,
        parentDisposable: Disposable,
    ) {
        this.backgroundTaskRunner = backgroundTaskRunner
        this.leftoverCleanupRunner = leftoverCleanupRunner
        Disposer.register(parentDisposable) {
            this.backgroundTaskRunner = ::queueBackgroundTask
            this.leftoverCleanupRunner = ::runLeftoverCleanupOnPooledThread
        }
    }

    private fun defaultRepositories(): List<GitRepository> {
        val repositoryManager = GitRepositoryManager.getInstance(project)
        return repositoryManager.sortByDependency(repositoryManager.repositories)
    }

    private fun defaultWorktrees(repository: GitRepository): List<WorktreeInfo> {
        val result = runListWorktrees(repository)
        if (!result.success()) return emptyList()

        return parseWorktrees(repository, result.output)
    }

    fun uniqueTopLevelRepository(): GitRepository? {
        return uniqueTopLevelRepository(repositories())
    }

    fun findLinkedWorktreeForBranch(repository: GitRepository, branchName: String): WorktreeInfo? {
        return worktrees(repository).firstOrNull { it.branchName == branchName && !it.isCurrent }
    }

    fun checkoutBranchIgnoringOtherWorktrees(repository: GitRepository, branchName: String): Boolean {
        return checkoutBranchWithConflictHandling(repository, branchName)
    }

    fun checkoutBranchIgnoringOtherWorktreesAsync(
        repository: GitRepository,
        branchName: String,
        notifyResult: Boolean = true,
        afterCompletion: () -> Unit = {},
    ) {
        checkoutWorktreeBranchAsync(repository, branchName, notifyResult, afterCompletion)
    }

    fun removeWorktree(repository: GitRepository, worktreePath: String, notifyResult: Boolean = true): Boolean {
        val result = removeWorktreeRunner(repository, worktreePath)
        if (!result.success() && !tryCleanupUnregisteredWorktree(repository, worktreePath, result)) {
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
        val result = deleteBranchRunner(repository, branchName, force)
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

    fun checkoutWorktreeBranchAsync(
        repository: GitRepository,
        branchName: String,
        notifyResult: Boolean = true,
        afterCompletion: () -> Unit = {},
    ) {
        backgroundTaskRunner(
            Gw4iBundle.message("GitWorktrees.task.checkout.branch.title"),
            {
                checkoutWorktreeBranch(repository, branchName, notifyResult)
            },
            {
                afterCompletion()
            },
        )
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
        val decision = askBranchDeletionDecision(branchName, worktreePath)
        return removeWorktreeWithBranchDecision(
            repository = repository,
            branchName = branchName,
            worktreePath = worktreePath,
            decision = decision,
        )
    }

    fun askBranchDeletionDecision(branchName: String, worktreePath: String): DeleteWorktreeBranchDecision {
        return branchDeletionDialogProvider(branchName, worktreePath)
    }

    fun removeWorktreeWithBranchDecision(
        repository: GitRepository,
        branchName: String,
        worktreePath: String,
        decision: DeleteWorktreeBranchDecision,
        notifyResult: Boolean = true,
    ): Boolean {
        if (decision == DeleteWorktreeBranchDecision.CANCEL) {
            return false
        }

        if (!removeWorktree(repository, worktreePath, notifyResult = false)) {
            return false
        }

        if (decision == DeleteWorktreeBranchDecision.DELETE_WORKTREE_AND_BRANCH) {
            return deleteBranch(repository, branchName, force = true, notifyResult = notifyResult)
        }

        if (notifyResult) {
            VcsNotifier.getInstance(project).notifySuccess(
                "gw4i.worktree.delete.success",
                "",
                Gw4iBundle.message("GitWorktrees.notification.worktree.delete.only.success", worktreePath, branchName),
            )
        }
        return true
    }

    fun removeWorktreesWithBranchDecision(
        targets: List<BulkRemoveWorktreesTarget>,
        decision: DeleteWorktreeBranchDecision,
        notifyResult: Boolean = true,
        cleanupLeftoversImmediately: Boolean = true,
    ): BulkRemoveWorktreesResult {
        if (decision == DeleteWorktreeBranchDecision.CANCEL) {
            return BulkRemoveWorktreesResult(removedWorktrees = 0, deletedBranches = 0)
        }

        var removedWorktrees = 0
        var deletedBranches = 0
        val affectedRepositories = linkedSetOf<GitRepository>()
        val leftoverCleanupPaths = mutableListOf<String>()
        targets.filterNot { it.worktree.isMain }.forEach { target ->
            val removalResult = removeWorktreeForBulk(target.repository, target.worktree.path)
            if (!removalResult.removed) {
                return@forEach
            }

            removedWorktrees++
            affectedRepositories += target.repository
            removalResult.leftoverCleanupPath?.let { leftoverCleanupPaths += it }
            val branchName = target.worktree.branchName
            if (decision == DeleteWorktreeBranchDecision.DELETE_WORKTREE_AND_BRANCH && branchName != null) {
                if (deleteBranchForBulk(target.repository, branchName, force = true)) {
                    deletedBranches++
                    affectedRepositories += target.repository
                }
            }
        }

        affectedRepositories.forEach(::refreshRepository)
        if (cleanupLeftoversImmediately) {
            cleanupLeftoverWorktrees(leftoverCleanupPaths)
        }
        if (notifyResult && removedWorktrees > 0) {
            VcsNotifier.getInstance(project).notifySuccess(
                "gw4i.worktrees.delete.success",
                "",
                Gw4iBundle.message("GitWorktrees.notification.worktrees.delete.success", removedWorktrees, deletedBranches),
            )
        }
        return BulkRemoveWorktreesResult(removedWorktrees, deletedBranches, leftoverCleanupPaths)
    }

    fun removeWorktreeAsync(
        repository: GitRepository,
        worktreePath: String,
        notifyResult: Boolean = true,
        afterCompletion: () -> Unit = {},
    ) {
        backgroundTaskRunner(
            Gw4iBundle.message("GitWorktrees.task.remove.worktree.title"),
            {
                removeWorktree(repository, worktreePath, notifyResult)
            },
            {
                afterCompletion()
            },
        )
    }

    fun removeWorktreeWithBranchDecisionAsync(
        repository: GitRepository,
        branchName: String,
        worktreePath: String,
        decision: DeleteWorktreeBranchDecision,
        notifyResult: Boolean = true,
        afterCompletion: () -> Unit = {},
    ) {
        backgroundTaskRunner(
            Gw4iBundle.message("GitWorktrees.task.remove.worktree.title"),
            {
                removeWorktreeWithBranchDecision(repository, branchName, worktreePath, decision, notifyResult)
            },
            {
                afterCompletion()
            },
        )
    }

    fun removeWorktreesWithBranchDecisionAsync(
        targets: List<BulkRemoveWorktreesTarget>,
        decision: DeleteWorktreeBranchDecision,
        notifyResult: Boolean = true,
        afterCompletion: () -> Unit = {},
    ) {
        var leftoverCleanupPaths: List<String> = emptyList()

        backgroundTaskRunner(
            Gw4iBundle.message("GitWorktrees.task.remove.worktree.title"),
            {
                leftoverCleanupPaths = removeWorktreesWithBranchDecision(
                    targets,
                    decision,
                    notifyResult,
                    cleanupLeftoversImmediately = false,
                ).leftoverCleanupPaths
            },
            {
                afterCompletion()
                cleanupLeftoverWorktreesAsync(leftoverCleanupPaths)
            },
        )
    }

    private fun checkoutBranchWithConflictHandling(
        repository: GitRepository,
        branchName: String,
        notifyResult: Boolean = true,
    ): Boolean {
        val initialResult = checkoutRunner(repository, branchName, false)
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

        if (!askCheckoutConflictConfirmation(branchName)) return false

        val forceResult = checkoutRunner(repository, branchName, true)
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

    private fun askCheckoutConflictConfirmation(branchName: String): Boolean {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            return checkoutConflictDialogProvider(branchName)
        }

        var confirmed = false
        application.invokeAndWait {
            confirmed = checkoutConflictDialogProvider(branchName)
        }
        return confirmed
    }

    private fun showCheckoutConflictDialog(branchName: String): Boolean {
        return MessageDialogBuilder.yesNo(
            Gw4iBundle.message("GitWorktrees.dialog.checkout.worktree.title"),
            Gw4iBundle.message("GitWorktrees.dialog.checkout.worktree.message", branchName),
        )
            .yesText(Gw4iBundle.message("GitWorktrees.dialog.checkout.worktree.yes"))
            .ask(project)
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

    private fun removeWorktreeForBulk(repository: GitRepository, worktreePath: String): BulkWorktreeRemoval {
        val result = removeWorktreeRunner(repository, worktreePath)
        if (result.success()) return BulkWorktreeRemoval(removed = true)
        if (!isDirectoryNotEmptyFailure(result)) {
            notifyDeleteWorktreeFailed(result)
            return BulkWorktreeRemoval(removed = false)
        }

        val path = Path.of(worktreePath)
        if (!Files.exists(path)) return BulkWorktreeRemoval(removed = true)
        if (!Files.isDirectory(path)) {
            notifyDeleteWorktreeFailed(result)
            return BulkWorktreeRemoval(removed = false)
        }

        return BulkWorktreeRemoval(removed = true, leftoverCleanupPath = worktreePath)
    }

    private fun deleteBranchForBulk(repository: GitRepository, branchName: String, force: Boolean): Boolean {
        val result = deleteBranchRunner(repository, branchName, force)
        if (!result.success()) {
            notifyDeleteBranchFailed(result)
            return false
        }
        return true
    }

    private fun cleanupLeftoverWorktreesAsync(leftoverCleanupPaths: List<String>) {
        val uniquePaths = uniqueCleanupPaths(leftoverCleanupPaths)
        if (uniquePaths.isEmpty()) return

        leftoverCleanupRunner(uniquePaths)
    }

    private fun queueBackgroundTask(title: String, runTask: () -> Unit, onFinished: () -> Unit) {
        object : Task.Backgroundable(project, title, true) {
            override fun run(indicator: ProgressIndicator) {
                runTask()
            }

            override fun onFinished() {
                onFinished()
            }
        }.queue()
    }

    private fun runLeftoverCleanupOnPooledThread(leftoverCleanupPaths: List<String>) {
        ApplicationManager.getApplication().executeOnPooledThread {
            cleanupLeftoverWorktrees(leftoverCleanupPaths)
        }
    }

    private fun cleanupLeftoverWorktrees(leftoverCleanupPaths: List<String>) {
        leftoverCleanupPaths.forEach { cleanupLeftoverWorktree(it) }
    }

    private fun uniqueCleanupPaths(leftoverCleanupPaths: List<String>): List<String> {
        return leftoverCleanupPaths.distinctBy { normalizePath(it).lowercase() }
    }

    private fun cleanupLeftoverWorktree(leftoverCleanupPath: String): Boolean {
        val path = Path.of(leftoverCleanupPath)
        if (!Files.exists(path)) return true
        if (!Files.isDirectory(path)) return false

        return try {
            NioFiles.deleteRecursively(path)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun tryCleanupUnregisteredWorktree(
        repository: GitRepository,
        worktreePath: String,
        result: GitCommandResult,
    ): Boolean {
        if (!isDirectoryNotEmptyFailure(result)) return false

        val stillRegistered = worktrees(repository).any { normalizePath(it.path) == normalizePath(worktreePath) }
        if (stillRegistered) return false

        val path = Path.of(worktreePath)
        if (!Files.exists(path)) return true
        if (!Files.isDirectory(path)) return false

        return cleanupLeftoverWorktree(worktreePath)
    }

    private fun isDirectoryNotEmptyFailure(result: GitCommandResult): Boolean {
        return result.errorOutput.any { it.contains("Directory not empty", ignoreCase = true) }
    }

    internal data class CheckoutResult(
        val commandResult: GitCommandResult,
        val hasConflictingChanges: Boolean,
    )

    private data class BulkWorktreeRemoval(
        val removed: Boolean,
        val leftoverCleanupPath: String? = null,
    )
}
