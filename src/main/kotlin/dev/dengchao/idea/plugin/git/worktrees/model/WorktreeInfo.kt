package dev.dengchao.idea.plugin.git.worktrees.model

data class WorktreeInfo(
    val path: String,
    val branchName: String?,
    val isMain: Boolean,
    val isCurrent: Boolean,
    val isLocked: Boolean,
    val isPrunable: Boolean,
) {
    val name: String
        get() {
            val trimmedPath = path.trimEnd('/', '\\')
            if (trimmedPath.isEmpty()) return path

            val separatorIndex = maxOf(trimmedPath.lastIndexOf('/'), trimmedPath.lastIndexOf('\\'))
            return if (separatorIndex >= 0) trimmedPath.substring(separatorIndex + 1) else trimmedPath
        }
}
