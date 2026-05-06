package dev.dengchao.idea.plugin.git.worktrees.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import dev.dengchao.idea.plugin.git.worktrees.Gw4iBundle
import dev.dengchao.idea.plugin.git.worktrees.ui.GitWorktreesDataKeys

class ToggleSelectedRepositoryAction : DumbAwareAction(
    Gw4iBundle.message("action.GitWorktrees.ToggleRepositoryCollapsed.collapse.text"),
    Gw4iBundle.message("action.GitWorktrees.ToggleRepositoryCollapsed.collapse.description"),
    null,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val panel = e.getData(GitWorktreesDataKeys.PANEL)
        val hasSingleRepositoryRow = panel?.selectedRepository() != null &&
            panel.selectedWorktree() == null &&
            panel.selectedWorktrees().isEmpty()

        e.presentation.isEnabledAndVisible = hasSingleRepositoryRow
        if (hasSingleRepositoryRow) {
            val collapsed = panel.isSelectedRepositoryCollapsed()
            e.presentation.text = Gw4iBundle.message(
                if (collapsed) {
                    "action.GitWorktrees.ToggleRepositoryCollapsed.expand.text"
                } else {
                    "action.GitWorktrees.ToggleRepositoryCollapsed.collapse.text"
                },
            )
            e.presentation.description = Gw4iBundle.message(
                if (collapsed) {
                    "action.GitWorktrees.ToggleRepositoryCollapsed.expand.description"
                } else {
                    "action.GitWorktrees.ToggleRepositoryCollapsed.collapse.description"
                },
            )
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        e.getData(GitWorktreesDataKeys.PANEL)?.toggleSelectedRepositoryExpanded()
    }
}
