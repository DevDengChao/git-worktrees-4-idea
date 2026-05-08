package dev.dengchao.idea.plugin.git.worktrees.ui;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import dev.dengchao.idea.plugin.git.worktrees.Gw4iBundle;
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesContentService;
import org.jetbrains.annotations.NotNull;

public final class GitWorktreesToolWindowFactory implements ToolWindowFactory, DumbAware {
    public static final String TOOLWINDOW_ID = "Git Worktrees";
    public static final String TOOLBAR_ACTION_GROUP_ID = "GitWorktrees.ToolWindow.Toolbar";
    public static final String POPUP_ACTION_GROUP_ID = "GitWorktrees.ToolWindow.Popup";

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        toolWindow.setTitle(Gw4iBundle.message("toolwindow.GitWorktrees.title"));
        GitWorktreesContentService.Companion.getInstance(project).openFromLegacyToolWindow(toolWindow);
    }

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return true;
    }
}
