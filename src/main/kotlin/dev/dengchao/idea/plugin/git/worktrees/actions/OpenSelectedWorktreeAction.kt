package dev.dengchao.idea.plugin.git.worktrees.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import dev.dengchao.idea.plugin.git.worktrees.Gw4iBundle
import dev.dengchao.idea.plugin.git.worktrees.ui.GitWorktreesDataKeys

class OpenSelectedWorktreeAction : DumbAwareAction(
    Gw4iBundle.message("action.GitWorktrees.Open.text"),
    Gw4iBundle.message("action.GitWorktrees.Open.description"),
    AllIcons.Actions.MenuOpen,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.getData(GitWorktreesDataKeys.SELECTED_WORKTREE) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        e.getData(GitWorktreesDataKeys.PANEL)?.openSelectedWorktree()
    }
}
