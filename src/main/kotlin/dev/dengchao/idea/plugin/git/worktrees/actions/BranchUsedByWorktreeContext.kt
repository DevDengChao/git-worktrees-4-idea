package dev.dengchao.idea.plugin.git.worktrees.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.VcsRef
import dev.dengchao.idea.plugin.git.worktrees.model.WorktreeInfo
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesOperationsService
import git4idea.GitLocalBranch
import git4idea.log.GitRefManager
import git4idea.repo.GitRepository

internal data class BranchUsedByWorktreeContext(
    val repository: GitRepository,
    val branchName: String,
    val worktree: WorktreeInfo,
)

internal object BranchUsedByWorktreeContextResolver {

    fun fromBranchPopupEvent(e: AnActionEvent): BranchUsedByWorktreeContext? {
        val project = e.project ?: return null
        val branch = e.getData(GitBranchActionDataKeys.SELECTED_REF) as? GitLocalBranch ?: return null
        val repository = e.getData(GitBranchActionDataKeys.SELECTED_REPOSITORY)
            ?: e.getData(GitBranchActionDataKeys.AFFECTED_REPOSITORIES)?.singleOrNull()
            ?: return null
        return find(projectService = GitWorktreesOperationsService.getInstance(project), repository, branch.name)
    }

    fun fromGitLogEvent(
        e: AnActionEvent,
        repository: GitRepository,
    ): List<BranchUsedByWorktreeContext> {
        val project = e.project ?: return emptyList()
        val service = GitWorktreesOperationsService.getInstance(project)
        return e.getData(VcsLogDataKeys.VCS_LOG_REFS)
            .orEmpty()
            .asSequence()
            .filter { it.isLocalBranchIn(repository) }
            .mapNotNull { find(service, repository, it.name) }
            .distinctBy { it.branchName }
            .toList()
    }

    private fun find(
        projectService: GitWorktreesOperationsService,
        repository: GitRepository,
        branchName: String,
    ): BranchUsedByWorktreeContext? {
        if (repository.currentBranchName == branchName) return null
        val worktree = projectService.findLinkedWorktreeForBranch(repository, branchName) ?: return null
        return BranchUsedByWorktreeContext(repository, branchName, worktree)
    }

    private fun VcsRef.isLocalBranchIn(repository: GitRepository): Boolean {
        return root == repository.root && type == GitRefManager.LOCAL_BRANCH
    }
}

