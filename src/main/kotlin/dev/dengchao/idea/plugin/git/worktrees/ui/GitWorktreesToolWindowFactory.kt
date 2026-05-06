package dev.dengchao.idea.plugin.git.worktrees.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import dev.dengchao.idea.plugin.git.worktrees.Gw4iBundle
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesContentService

class GitWorktreesToolWindowFactory : ToolWindowFactory, DumbAware {

    companion object {
        const val TOOLWINDOW_ID = "Git Worktrees"
        const val TOOLBAR_ACTION_GROUP_ID = "GitWorktrees.ToolWindow.Toolbar"
        const val POPUP_ACTION_GROUP_ID = "GitWorktrees.ToolWindow.Popup"
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.title = Gw4iBundle.message("toolwindow.GitWorktrees.title")
        GitWorktreesContentService.getInstance(project).openFromLegacyToolWindow(toolWindow)
    }

    override suspend fun manage(toolWindow: ToolWindow, toolWindowManager: ToolWindowManager) {
        val project = toolWindow.project
        project.messageBus.connect(toolWindow.disposable).subscribe(
            ToolWindowManagerListener.TOPIC,
            object : ToolWindowManagerListener {
                override fun toolWindowShown(toolWindow: ToolWindow) {
                    redirectIfLegacyToolWindow(toolWindow.id, toolWindow)
                }

                @Deprecated("Override retained for the 2025.2 runtime event signature.")
                override fun toolWindowShown(id: String, toolWindow: ToolWindow) {
                    redirectIfLegacyToolWindow(id, toolWindow)
                }

                private fun redirectIfLegacyToolWindow(id: String, toolWindow: ToolWindow) {
                    if (id == TOOLWINDOW_ID) {
                        GitWorktreesContentService.getInstance(project).openFromLegacyToolWindow(toolWindow)
                    }
                }
            },
        )
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        return true
    }
}
