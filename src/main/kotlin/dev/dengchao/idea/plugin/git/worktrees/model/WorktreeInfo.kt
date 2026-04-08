package dev.dengchao.idea.plugin.git.worktrees.model

import java.nio.file.Paths

data class WorktreeInfo(
    val path: String,
    val branchName: String?,
    val isMain: Boolean,
    val isCurrent: Boolean,
    val isLocked: Boolean,
    val isPrunable: Boolean,
) {
    val name: String
        get() = Paths.get(path).fileName?.toString() ?: path
}
