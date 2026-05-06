package dev.dengchao.idea.plugin.git.worktrees.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import dev.dengchao.idea.plugin.git.worktrees.Gw4iBundle
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesContentService

class ShowGitWorktreesToolWindowAction : DumbAwareAction(
    Gw4iBundle.message("action.GitWorktrees.ShowToolWindow.text"),
    Gw4iBundle.message("action.GitWorktrees.ShowToolWindow.description"),
    null,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        openWorktreesTab(project)
    }

    companion object {
        private var openWorktreesTab: (Project) -> Unit = { project ->
            GitWorktreesContentService.getInstance(project).openOrSelectWorktreesTab()
        }

        internal fun overrideOpenWorktreesTabForTests(
            opener: (Project) -> Unit,
            parentDisposable: Disposable,
        ) {
            openWorktreesTab = opener
            Disposer.register(parentDisposable) {
                openWorktreesTab = { project ->
                    GitWorktreesContentService.getInstance(project).openOrSelectWorktreesTab()
                }
            }
        }
    }
}
