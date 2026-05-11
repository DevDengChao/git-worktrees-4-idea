package dev.dengchao.idea.plugin.git.worktrees.startup

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesContentService

class GitWorktreesStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        GitWorktreesContentService.getInstance(project).restoreWorktreesTabIfNeeded()
    }
}
