package dev.dengchao.idea.plugin.git.worktrees.services

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import dev.dengchao.idea.plugin.git.worktrees.ui.GitWorktreesToolWindowFactory

class GitWorktreesToolWindowListener(private val project: Project) : ToolWindowManagerListener {

    override fun stateChanged(toolWindowManager: ToolWindowManager) {
        if (project.isDisposed) return

        val legacyToolWindow = toolWindowManager.getToolWindow(GitWorktreesToolWindowFactory.TOOLWINDOW_ID) ?: return
        if (!legacyToolWindow.isVisible) return

        val vcsToolWindow = toolWindowManager.getToolWindow(ToolWindowId.VCS)
        if (vcsToolWindow != null) {
            legacyToolWindow.hide(null)
            GitWorktreesContentService.getInstance(project).openOrSelectWorktreesTab()
        }
    }
}
