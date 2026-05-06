# Git Worktrees

Manage linked Git worktrees from inside IntelliJ IDEA.

在 IntelliJ IDEA 里集中查看、过滤、切换和删除 Git worktree。

<!-- Plugin description -->
**Git Worktrees** adds a focused Worktrees tab to the Git tool window, so you can see every linked worktree across one or many Git roots without leaving the IDE.

**Git Worktrees** 会在 Git 工具窗口中加入一个专门的 Worktrees 标签页，让你不用离开 IDE，就能查看一个或多个 Git root 下的全部 linked worktree。

![Git Worktrees overview](design/marketing/screenshots/00-overview.png)

## Highlights / 功能亮点

- **Filter by Worktree, Branch, or Location.** Narrow a large worktree list directly from the table header.
- **按 Worktree、Branch、Location 过滤。** 在表头输入关键词，就能快速收窄大量 worktree。
- **Sort each column inside every repository group.** Toggle ascending, descending, and unsorted order per column.
- **在每个仓库分组内按列排序。** 每列排序按钮支持升序、降序和取消排序。
- **Use Speed Search with visible match highlights.** Type to jump to matching rows and see matched fragments highlighted in the table.
- **支持 Speed Search 和命中高亮。** 直接键入关键词即可定位匹配行，并在表格里高亮命中片段。
- **Work naturally in multi-root projects.** Repository rows group worktrees from `console`, `api`, `android`, or any other Git root in the project.
- **自然支持多 root 项目。** `console`、`api`、`android` 等多个 Git root 会按仓库分组展示。
- **Open, checkout, refresh, and delete worktrees from the toolbar or context menu.**
- **通过工具栏或右键菜单打开、切换、刷新和删除 worktree。**
- **Handle branches already used by another worktree.** Extra Git Branch menu actions let you checkout anyway with `--ignore-other-worktrees`, or delete the branch together with its worktree.
- **处理已被其他 worktree 占用的分支。** Git Branch 菜单会提供 `--ignore-other-worktrees` 切换和“删除分支 / worktree”的入口。
<!-- Plugin description end -->

## Screenshots / 截图

### Header Filters / 表头过滤

![Header filters](design/marketing/screenshots/01-header-filters.png)

Filter each column independently. The filter is case-insensitive, and repository groups disappear when none of their worktrees match.

每列都可以独立过滤。过滤不区分大小写；如果某个仓库分组下没有任何匹配的 worktree，该分组会自动隐藏。

### Column Sorting / 列排序

![Column sorting](design/marketing/screenshots/02-column-sorting.png)

Each sort button cycles through ascending, descending, and unsorted states. Sorting stays inside each repository group, so roots remain readable.

每个排序按钮会在升序、降序和未排序之间切换。排序只在各自仓库分组内生效，因此多 root 结构仍然清晰。

### Speed Search Highlighting / 快速搜索高亮

![Speed Search highlights](design/marketing/screenshots/03-speed-search-highlights.png)

Use IntelliJ table Speed Search to jump through visible worktree rows, with matched fragments highlighted in place.

使用 IntelliJ 表格 Speed Search 快速跳转到匹配行，命中的文字片段会直接在表格中高亮。

### Multi-root Projects / 多 root 项目

![Multi-root groups](design/marketing/screenshots/04-multi-root-groups.png)

Repository rows keep large projects organized. Each Git root has its own worktree rows, current worktree marker, branch names, and filesystem locations.

仓库分组行会把大型项目整理清楚。每个 Git root 都有自己的 worktree 行、当前 worktree 标记、分支名和文件系统路径。

## Features / 功能

- Open the Worktrees tab from the Git main menu or the Git tool window drop-down.
- 从 Git 主菜单或 Git 工具窗口下拉菜单打开 Worktrees 标签页。
- Reuse a closable `Worktrees` tab inside the Version Control / Git tool window; fall back to the standalone `Git Worktrees` tool window when needed.
- 优先复用 Version Control / Git 工具窗口内可关闭的 `Worktrees` 标签页；必要时回退到独立的 `Git Worktrees` 工具窗口。
- List linked worktrees using `git worktree list --porcelain`.
- 使用 `git worktree list --porcelain` 列出 linked worktree。
- Show `Worktree`, `Branch`, and `Location` columns.
- 展示 `Worktree`、`Branch` 和 `Location` 三列。
- Render the main worktree in bold and mark the currently opened worktree with a check icon.
- 主 worktree 加粗显示，当前打开的 worktree 显示勾选图标。
- Display detached worktrees as `detached`.
- detached HEAD worktree 显示为 `detached`。
- Keep toolbar and popup actions in sync: Refresh, Checkout, Open, and Delete Worktree.
- 工具栏和右键菜单保持一致：Refresh、Checkout、Open、Delete Worktree。
- Open the selected worktree as a project by double-clicking or using Open.
- 双击或点击 Open，可把选中的 worktree 作为项目打开。
- Checkout a selected worktree branch in the current repository with `--ignore-other-worktrees`.
- 使用 `--ignore-other-worktrees` 在当前仓库切换到选中 worktree 的分支。
- Ask before force checkout when local changes or untracked files would be overwritten.
- 当本地变更或未跟踪文件可能被覆盖时，先弹窗确认再执行 force checkout。
- Delete a single worktree, or delete multiple selected worktrees in one batch.
- 支持删除单个 worktree，也支持一次批量删除多个选中的 worktree。
- Choose whether to delete only the worktree or delete both the worktree and its branch.
- 删除时可选择只删除 worktree，或同时删除 worktree 和对应分支。
- Protect main worktrees from delete actions.
- 主 worktree 不会出现在可删除操作中。
- Refresh each affected repository after Git operations and show success or failure notifications.
- Git 操作后刷新受影响仓库，并通过通知提示成功或失败。
- Defer best-effort leftover directory cleanup after bulk deletion, reducing visible task time on Windows.
- 批量删除后把残留目录清理放到后台尽力执行，降低 Windows 下可见删除任务的等待时间。

## Requirements / 环境要求

- IntelliJ Platform IDE build `252` or newer.
- IntelliJ Platform IDE build `252` 或更新版本。
- Bundled Git plugin (`Git4Idea`) enabled.
- 需要启用 IDE 内置 Git 插件 (`Git4Idea`)。

## Development / 开发

Run the focused regression suite for panel, action, and operation changes:

面板、动作或 Git 操作服务有改动时，运行以下定向回归：

```powershell
./gradlew.bat test --tests dev.dengchao.idea.plugin.git.worktrees.GitWorktreesPanelTest --tests dev.dengchao.idea.plugin.git.worktrees.GitWorktreesActionsTest --tests dev.dengchao.idea.plugin.git.worktrees.GitWorktreesOperationsServiceTest
```

Before merging feature work, run the full test suite:

合并功能改动前，运行完整测试：

```powershell
./gradlew.bat test
```
