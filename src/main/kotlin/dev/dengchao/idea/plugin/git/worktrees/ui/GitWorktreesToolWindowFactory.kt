package dev.dengchao.idea.plugin.git.worktrees.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import dev.dengchao.idea.plugin.git.worktrees.Gw4iBundle
import git4idea.repo.GitRepositoryManager

class GitWorktreesToolWindowFactory : ToolWindowFactory, DumbAware {

    companion object {
        const val TOOLWINDOW_ID = "Git Worktrees"
        const val TOOLBAR_ACTION_GROUP_ID = "GitWorktrees.ToolWindow.Toolbar"
        const val POPUP_ACTION_GROUP_ID = "GitWorktrees.ToolWindow.Popup"
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = GitWorktreesPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        content.setDisposer(panel)

        toolWindow.title = Gw4iBundle.message("toolwindow.GitWorktrees.title")
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        return GitRepositoryManager.getInstance(project).repositories.isNotEmpty()
    }
}
