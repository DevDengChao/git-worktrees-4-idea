package dev.dengchao.idea.plugin.git.worktrees.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataSnapshotProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.VcsRef
import dev.dengchao.idea.plugin.git.worktrees.model.WorktreeInfo
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesOperationsService
import git4idea.GitLocalBranch
import git4idea.log.GitRefManager
import git4idea.repo.GitRepository
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name

internal data class BranchUsedByWorktreeContext(
    val repository: GitRepository,
    val branchName: String,
    val worktree: WorktreeInfo,
)

internal object BranchUsedByWorktreeContextResolver {

    fun fromBranchPopupEvent(
        e: AnActionEvent,
        allowServiceFallback: Boolean = false,
    ): BranchUsedByWorktreeContext? {
        val project = e.project ?: return null
        val branch = e.getData(GitBranchActionDataKeys.SELECTED_REF) as? GitLocalBranch ?: return null
        val repository = e.getData(GitBranchActionDataKeys.SELECTED_REPOSITORY)
            ?: e.getData(GitBranchActionDataKeys.AFFECTED_REPOSITORIES)?.singleOrNull()
            ?: return null
        return find(
            projectService = GitWorktreesOperationsService.getInstance(project),
            repository = repository,
            branchName = branch.name,
            allowServiceFallback = allowServiceFallback,
        )
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
            .mapNotNull { find(service, repository, it.name, allowServiceFallback = isUnitTestMode()) }
            .distinctBy { it.branchName }
            .toList()
    }

    fun fromActionSnapshot(
        e: AnActionEvent?,
        action: DataSnapshotProvider,
    ): BranchUsedByWorktreeContext? {
        if (e == null) return null
        val project = e.project ?: return null
        val actionContext = CustomizedDataContext.withSnapshot(e.dataContext, action)
        val branch = actionContext.getData(GitBranchActionDataKeys.SELECTED_REF) as? GitLocalBranch ?: return null
        val repository = actionContext.getData(GitBranchActionDataKeys.SELECTED_REPOSITORY)
            ?: actionContext.getData(GitBranchActionDataKeys.AFFECTED_REPOSITORIES)?.singleOrNull()
            ?: return null
        return find(
            GitWorktreesOperationsService.getInstance(project),
            repository,
            branch.name,
            allowServiceFallback = isUnitTestMode(),
        )
    }

    private fun find(
        projectService: GitWorktreesOperationsService,
        repository: GitRepository,
        branchName: String,
        allowServiceFallback: Boolean = true,
    ): BranchUsedByWorktreeContext? {
        if (repository.currentBranchName == branchName) return null
        GitWorktreeMetadataResolver.findLinkedWorktreeForBranch(repository, branchName)?.let { worktree ->
            return BranchUsedByWorktreeContext(repository, branchName, worktree)
        }
        if (!allowServiceFallback) return null
        val worktree = projectService.findLinkedWorktreeForBranch(repository, branchName) ?: return null
        return BranchUsedByWorktreeContext(repository, branchName, worktree)
    }

    private fun VcsRef.isLocalBranchIn(repository: GitRepository): Boolean {
        return root == repository.root && type == GitRefManager.LOCAL_BRANCH
    }

    private fun isUnitTestMode(): Boolean = ApplicationManager.getApplication().isUnitTestMode
}

internal object GitWorktreeMetadataResolver {
    fun findLinkedWorktreeForBranch(repository: GitRepository, branchName: String): WorktreeInfo? {
        val rootPath = repository.root.path
        val commonGitDir = resolveCommonGitDir(rootPath) ?: return null
        val worktreesDir = commonGitDir.resolve("worktrees")
        if (!worktreesDir.isDirectory()) return null
        val currentRoot = normalizePath(rootPath)

        return runCatching {
            Files.list(worktreesDir).use { entries ->
                entries
                    .filter { it.isDirectory() }
                    .map { metadataDir -> worktreeFromMetadata(metadataDir, branchName, currentRoot) }
                    .filter { it != null }
                    .findFirst()
                    .orElse(null)
            }
        }.getOrNull()
    }

    private fun worktreeFromMetadata(
        metadataDir: Path,
        branchName: String,
        currentRoot: String,
    ): WorktreeInfo? {
        if (readHeadBranch(metadataDir.resolve("HEAD")) != branchName) return null
        val gitDir = readPath(metadataDir.resolve("gitdir"), metadataDir) ?: return null
        val worktreePath = if (gitDir.name == ".git") gitDir.parent ?: gitDir else gitDir
        val normalizedPath = normalizePath(worktreePath.toString())
        if (normalizedPath == currentRoot) return null

        return WorktreeInfo(
            path = normalizedPath,
            branchName = branchName,
            isMain = false,
            isCurrent = false,
            isLocked = metadataDir.resolve("locked").exists(),
            isPrunable = metadataDir.resolve("prunable").exists(),
        )
    }

    private fun resolveCommonGitDir(rootPath: String): Path? {
        val gitFileOrDir = Path.of(rootPath).resolve(".git")
        return when {
            gitFileOrDir.isDirectory() -> gitFileOrDir
            gitFileOrDir.exists() -> {
                val gitDir = readGitDirPointer(gitFileOrDir) ?: return null
                val commonDir = readPath(gitDir.resolve("commondir"), gitDir)
                commonDir ?: gitDir
            }

            else -> null
        }
    }

    private fun readGitDirPointer(gitFile: Path): Path? {
        val pointer = runCatching { Files.readString(gitFile).trim() }.getOrNull() ?: return null
        val rawPath = pointer.removePrefix("gitdir:").trim()
        if (rawPath.isBlank()) return null
        return resolvePath(gitFile.parent, rawPath)
    }

    private fun readPath(file: Path, baseDir: Path): Path? {
        val rawPath = runCatching { Files.readString(file).trim() }.getOrNull() ?: return null
        if (rawPath.isBlank()) return null
        return resolvePath(baseDir, rawPath)
    }

    private fun resolvePath(baseDir: Path, rawPath: String): Path {
        val path = Path.of(rawPath)
        return if (path.isAbsolute) path.normalize() else baseDir.resolve(path).normalize()
    }

    private fun readHeadBranch(headFile: Path): String? {
        val head = runCatching { Files.readString(headFile).trim() }.getOrNull() ?: return null
        return head.removePrefix("ref: refs/heads/").takeIf { it != head && it.isNotBlank() }
    }

    private fun normalizePath(path: String): String {
        return path.replace('\\', '/').trimEnd('/')
    }
}

