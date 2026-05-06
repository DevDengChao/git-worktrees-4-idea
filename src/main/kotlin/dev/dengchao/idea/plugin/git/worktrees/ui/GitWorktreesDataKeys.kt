package dev.dengchao.idea.plugin.git.worktrees.ui

import com.intellij.openapi.actionSystem.DataKey
import dev.dengchao.idea.plugin.git.worktrees.model.WorktreeInfo
import git4idea.repo.GitRepository

object GitWorktreesDataKeys {
    data class SelectedGitWorktree(
        val repository: GitRepository,
        val worktree: WorktreeInfo,
    )

    @JvmField
    val PANEL: DataKey<GitWorktreesPanel> = DataKey.create("GW4I_PANEL")

    @JvmField
    val CURRENT_REPOSITORY: DataKey<GitRepository> = DataKey.create("GW4I_CURRENT_REPOSITORY")

    @JvmField
    val SELECTED_WORKTREE: DataKey<WorktreeInfo> = DataKey.create("GW4I_SELECTED_WORKTREE")

    @JvmField
    val SELECTED_WORKTREES: DataKey<List<SelectedGitWorktree>> = DataKey.create("GW4I_SELECTED_WORKTREES")
}
