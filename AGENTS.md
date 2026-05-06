# Project Notes for Agents

- Follow `~/AGENTS.md` before working in this repository.
- Check for `.mcp.json` before starting; this repository currently has no project-level MCP config.
- For Git Worktrees panel selection changes, keep single-target data keys (`CURRENT_REPOSITORY`, `SELECTED_WORKTREE`) available only for exactly one selected row. Use `SELECTED_WORKTREES` when actions need to handle multiple selected worktree rows, and ignore repository header rows for bulk operations.
- For Git Worktrees panel list context menus, resolve `GitWorktrees.ToolWindow.Popup` to an `ActionGroup` during panel setup and install it with the direct `ActionGroup` `PopupHandler.installPopupMenu` overload; the string-id overload can resolve to `null` when the menu is invoked in the 2025.2 runtime.
- The Git Worktrees panel now uses a table, not `JBList`: UI robot tests should locate `JBTable`/`JTable`, and popup installation tests should install against a table-like `JComponent`.
- For the table panel, keep repository rows as grouping context only. `Worktree id` is `WorktreeInfo.name`; branch displays `branchName ?: "detached"`; sort and filter are applied within each repository group.
- For bulk worktree deletion, do not refresh the repository or synchronously clean leftover directories after every individual worktree/branch command. Batch Git operations first, refresh each affected repository once, and defer physical leftover cleanup when possible because Windows directory cleanup can dominate the perceived delete time.
- Deferred leftover directory cleanup after bulk deletion should not create another visible `Deleting Worktree` `Task.Backgroundable`; keep the user-visible delete task scoped to Git unregister/branch deletion, and run leftover file cleanup as best-effort pooled work with de-duplicated paths.
- For panel/action/service changes, run the targeted suite with:
  `./gradlew.bat test --tests dev.dengchao.idea.plugin.git.worktrees.GitWorktreesPanelTest --tests dev.dengchao.idea.plugin.git.worktrees.GitWorktreesActionsTest --tests dev.dengchao.idea.plugin.git.worktrees.GitWorktreesOperationsServiceTest`
- Before merging feature work, run `./gradlew.bat test`.
