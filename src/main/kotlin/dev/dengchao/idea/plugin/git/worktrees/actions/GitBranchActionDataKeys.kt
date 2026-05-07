package dev.dengchao.idea.plugin.git.worktrees.actions

import com.intellij.openapi.actionSystem.DataKey
import git4idea.GitReference
import git4idea.repo.GitRepository

internal object GitBranchActionDataKeys {
    val SELECTED_REF: DataKey<GitReference> = DataKey.create("Git.Selected.Ref")
    val SELECTED_REPOSITORY: DataKey<GitRepository> = DataKey.create("Git.Selected.Repository")
    val AFFECTED_REPOSITORIES: DataKey<List<GitRepository>> = DataKey.create("Git.Repositories")
}
