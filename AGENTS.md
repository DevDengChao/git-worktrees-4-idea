# Project Notes for Agents

- Follow `~/AGENTS.md` before working in this repository.
- Check for `.mcp.json` before starting; this repository currently has no project-level MCP config.
- For Git Worktrees panel selection changes, keep single-target data keys (`CURRENT_REPOSITORY`, `SELECTED_WORKTREE`) available only for exactly one selected row. Use `SELECTED_WORKTREES` when actions need to handle multiple selected worktree rows, and ignore repository header rows for bulk operations.
- For Git Worktrees panel list context menus, resolve `GitWorktrees.ToolWindow.Popup` to an `ActionGroup` during panel setup and install it with the direct `ActionGroup` `PopupHandler.installPopupMenu` overload; the string-id overload can resolve to `null` when the menu is invoked in the 2025.2 runtime.
- For panel/action/service changes, run the targeted suite with:
  `./gradlew.bat test --tests dev.dengchao.idea.plugin.git.worktrees.GitWorktreesPanelTest --tests dev.dengchao.idea.plugin.git.worktrees.GitWorktreesActionsTest --tests dev.dengchao.idea.plugin.git.worktrees.GitWorktreesOperationsServiceTest`
- Before merging feature work, run `./gradlew.bat test`.
