package dev.dengchao.idea.plugin.git.worktrees.commands

import com.intellij.openapi.util.Key
import git4idea.commands.GitLineEventDetector
import java.util.regex.Pattern

class GitBranchAlreadyUsedByWorktreeDetector : GitLineEventDetector {
    private val pattern = Pattern.compile("fatal:\\s*'(.+)'\\s+is already (?:checked out|used by worktree) at\\s+'(.+)'")

    override var isDetected: Boolean = false
        private set

    var branchName: String? = null
        private set

    var worktreePath: String? = null
        private set

    override fun onLineAvailable(line: String, outputType: Key<*>) {
        val matcher = pattern.matcher(line)
        if (!matcher.matches()) return

        isDetected = true
        branchName = matcher.group(1)
        worktreePath = matcher.group(2)
    }
}
