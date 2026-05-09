# Git Worktrees | GW4I

<!-- Plugin description -->
## English

Manage linked Git worktrees from inside IntelliJ IDEA.

**Git Worktrees | GW4I** adds a focused Worktrees tab to the Git tool window, so you can see every linked worktree across one or many Git roots without leaving the IDE.

![Git Worktrees overview](https://raw.githubusercontent.com/DevDengChao/git-worktrees-4-idea/main/design/marketing/screenshots/00-overview.png)

### Highlights

- **Filter by Worktree, Branch, or Location.** Narrow a large worktree list directly from the table header.
- **Sort one or more columns inside every repository group.** Toggle ascending, descending, and unsorted order per column while keeping roots readable.
- **Use Speed Search with visible match highlights.** Type to jump to matching rows and see matched fragments highlighted in the table.
- **Work naturally in multi-root projects.** Repository rows group worktrees from `console`, `api`, `android`, or any other Git root in the project.
- **Collapse repository groups when you need a tighter view.** Use the chevron, double-click a repository row, or the context menu to hide or show its worktrees.
- **Open, checkout, refresh, and delete worktrees from the toolbar or context menu.**
- **Keep normal Git branch workflows worktree-aware.** Git Branch and Git Log branch-label menus can offer Checkout Anyway or Delete Branch / Worktree when a branch is already used by another worktree.
- **Stay oriented inside the IDE.** The Git menu entry carries a subtle `By GW4I` marker, and the panel shows a small provider note at the toolbar edge.
- **Keep deletion responsive on Windows.** Git unregisters worktrees first, then leftover non-empty directories are cleaned up as best-effort background work.

### Screenshots

#### Header Filters

![Header filters](https://raw.githubusercontent.com/DevDengChao/git-worktrees-4-idea/main/design/marketing/screenshots/01-header-filters.png)

Filter each column independently. The filter is case-insensitive, and repository groups disappear when none of their worktrees match.

#### Column Sorting

![Column sorting](https://raw.githubusercontent.com/DevDengChao/git-worktrees-4-idea/main/design/marketing/screenshots/02-column-sorting.png)

Each sort button cycles through ascending, descending, and unsorted states. Multiple active sort buttons are applied in order inside each repository group, so roots remain readable.

#### Speed Search Highlighting

![Speed Search highlights](https://raw.githubusercontent.com/DevDengChao/git-worktrees-4-idea/main/design/marketing/screenshots/03-speed-search-highlights.png)

Use IntelliJ table Speed Search to jump through visible worktree rows, with matched fragments highlighted in place.

#### Multi-root Projects

![Multi-root groups](https://raw.githubusercontent.com/DevDengChao/git-worktrees-4-idea/main/design/marketing/screenshots/04-multi-root-groups.png)

Repository rows keep large projects organized. Each Git root has its own worktree rows, current worktree marker, branch names, and filesystem locations.

### Features

- Open the Worktrees tab from the Git main menu or the Git tool window drop-down.
- Reuse a closable `Worktrees` tab inside the Version Control / Git tool window; fall back to the standalone `Git Worktrees` tool window when needed.
- List linked worktrees using `git worktree list --porcelain`.
- Show `Worktree`, `Branch`, and `Location` columns.
- Render the main worktree in bold and mark the currently opened worktree with a check icon.
- Display detached worktrees as `detached`.
- Collapse or expand repository rows from the chevron, double-click, or context menu.
- Keep toolbar and popup actions in sync: Refresh, Checkout, Open, and Delete Worktree.
- Open the selected worktree as a project by double-clicking or using Open.
- Checkout a selected worktree branch in the current repository with `--ignore-other-worktrees`.
- Keep Checkout visible with an explanatory disabled hint when the selected row cannot be checked out, such as detached HEAD or the current worktree.
- Ask before force checkout when local changes or untracked files would be overwritten.
- Run checkout and delete operations in background tasks, then reload the panel when the operation completes.
- Delete a single worktree, or delete multiple selected worktrees in one batch.
- Choose whether to delete only the worktree or delete both the worktree and its branch.
- Protect main worktrees from delete actions.
- Replace the native Git Branch checkout/delete actions only when the selected branch is already used by a linked worktree, keeping the regular Git menu behavior for ordinary branches.
- Enhance Git Log branch-label menus so Checkout and Delete Branch actions handle linked-worktree branches, including branch names with `/`.
- Resolve Git Log worktree branch conflicts from Git metadata and action data, avoiding slow full worktree listing during menu updates.
- Mark the Worktrees tab entry with `By GW4I` in the Git menu and show a compact provider note in the panel toolbar.
- Refresh each affected repository after Git operations and show success or failure notifications.
- Defer best-effort leftover directory cleanup after single and bulk deletion, reducing visible task time on Windows.

### Requirements

- IntelliJ Platform IDE build `252` or newer.
- Bundled Git plugin (`Git4Idea`) enabled.

## 中文

在 IntelliJ IDEA 里集中查看、过滤、切换和删除 Git worktree。

**Git Worktrees | GW4I** 会在 Git 工具窗口中加入一个专门的 Worktrees 标签页，让你不用离开 IDE，就能查看一个或多个 Git root 下的全部 linked worktree。

![Git Worktrees 概览](https://raw.githubusercontent.com/DevDengChao/git-worktrees-4-idea/main/design/marketing/screenshots/00-overview.png)

### 功能亮点

- **按 Worktree、Branch、Location 过滤。** 在表头输入关键词，就能快速收窄大量 worktree。
- **在每个仓库分组内按一列或多列排序。** 每列排序按钮支持升序、降序和取消排序，同时保持 Git root 分组清晰。
- **支持 Speed Search 和命中高亮。** 直接键入关键词即可定位匹配行，并在表格里高亮命中片段。
- **自然支持多 root 项目。** `console`、`api`、`android` 等多个 Git root 会按仓库分组展示。
- **需要更紧凑视图时可以折叠仓库分组。** 可通过左侧箭头、双击仓库行或右键菜单隐藏和展开该仓库下的 worktree。
- **通过工具栏或右键菜单打开、切换、刷新和删除 worktree。**
- **让常用 Git 分支流程感知 worktree。** 当分支已被另一个 worktree 使用时，Git Branch 菜单和 Git Log 分支标签菜单会提供 Checkout Anyway 或 Delete Branch / Worktree。
- **在 IDE 里清楚标识来源。** Git 菜单入口会显示轻量的 `By GW4I` 标记，面板工具栏边缘也会显示简洁的来源说明。
- **让 Windows 下的删除操作保持响应。** 先完成 Git unregister，再把残留的非空目录放到后台尽力清理。

### 截图

#### 表头过滤

![表头过滤](https://raw.githubusercontent.com/DevDengChao/git-worktrees-4-idea/main/design/marketing/screenshots/01-header-filters.png)

每列都可以独立过滤。过滤不区分大小写；如果某个仓库分组下没有任何匹配的 worktree，该分组会自动隐藏。

#### 列排序

![列排序](https://raw.githubusercontent.com/DevDengChao/git-worktrees-4-idea/main/design/marketing/screenshots/02-column-sorting.png)

每个排序按钮会在升序、降序和未排序之间切换。多个已启用的排序按钮会按顺序在各自仓库分组内生效，因此多 root 结构仍然清晰。

#### 快速搜索高亮

![快速搜索高亮](https://raw.githubusercontent.com/DevDengChao/git-worktrees-4-idea/main/design/marketing/screenshots/03-speed-search-highlights.png)

使用 IntelliJ 表格 Speed Search 快速跳转到匹配行，命中的文字片段会直接在表格中高亮。

#### 多 root 项目

![多 root 分组](https://raw.githubusercontent.com/DevDengChao/git-worktrees-4-idea/main/design/marketing/screenshots/04-multi-root-groups.png)

仓库分组行会把大型项目整理清楚。每个 Git root 都有自己的 worktree 行、当前 worktree 标记、分支名和文件系统路径。

### 功能

- 从 Git 主菜单或 Git 工具窗口下拉菜单打开 Worktrees 标签页。
- 优先复用 Version Control / Git 工具窗口内可关闭的 `Worktrees` 标签页；必要时回退到独立的 `Git Worktrees` 工具窗口。
- 使用 `git worktree list --porcelain` 列出 linked worktree。
- 展示 `Worktree`、`Branch` 和 `Location` 三列。
- 主 worktree 加粗显示，当前打开的 worktree 显示勾选图标。
- detached HEAD worktree 显示为 `detached`。
- 可通过左侧箭头、双击或右键菜单折叠和展开仓库行。
- 工具栏和右键菜单保持一致：Refresh、Checkout、Open、Delete Worktree。
- 双击或点击 Open，可把选中的 worktree 作为项目打开。
- 使用 `--ignore-other-worktrees` 在当前仓库切换到选中 worktree 的分支。
- 当选中项不能 Checkout 时，保留 Checkout 入口并显示禁用原因，例如 detached HEAD 或当前 worktree。
- 当本地变更或未跟踪文件可能被覆盖时，先弹窗确认再执行 force checkout。
- Checkout 和 Delete 会在后台任务里执行，操作结束后自动重新加载面板。
- 支持删除单个 worktree，也支持一次批量删除多个选中的 worktree。
- 删除时可选择只删除 worktree，或同时删除 worktree 和对应分支。
- 主 worktree 不会出现在可删除操作中。
- 只在选中分支已被 linked worktree 使用时替换原生 Git Branch checkout/delete 动作，普通分支仍保留 IDE 原有行为。
- 增强 Git Log 分支标签菜单，让 Checkout 和 Delete Branch 能处理 linked-worktree 分支，包括带 `/` 的分支名。
- 通过 Git metadata 和 action data 解析 Git Log 里的 worktree 分支冲突，避免菜单更新时完整扫描 worktree 列表。
- 在 Git 菜单的 Worktrees 入口显示 `By GW4I`，并在面板工具栏显示紧凑的来源说明。
- Git 操作后刷新受影响仓库，并通过通知提示成功或失败。
- 单个和批量删除后都把残留目录清理放到后台尽力执行，降低 Windows 下可见删除任务的等待时间。

### 环境要求

- IntelliJ Platform IDE build `252` 或更新版本。
- 需要启用 IDE 内置 Git 插件 (`Git4Idea`)。
<!-- Plugin description end -->

## 开发

面板、动作或 Git 操作服务有改动时，运行以下定向回归：

```powershell
./gradlew.bat test --tests dev.dengchao.idea.plugin.git.worktrees.GitWorktreesPanelTest --tests dev.dengchao.idea.plugin.git.worktrees.GitWorktreesActionsTest --tests dev.dengchao.idea.plugin.git.worktrees.GitWorktreesOperationsServiceTest
```

合并功能改动前，运行完整测试：

```powershell
./gradlew.bat test
```
