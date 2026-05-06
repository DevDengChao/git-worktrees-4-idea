# Git Worktrees Marketing Copy / Git Worktrees 宣传文案

This file keeps the English and Chinese copy together so the same material can be reused for README, JetBrains Marketplace, release notes, and external posts.

本文件把英文和中文文案放在一起，方便同时复用于 README、JetBrains Marketplace、发布说明和外部宣传内容。

## Overview / 概览

![Git Worktrees overview](screenshots/00-overview.png)

**English:** Git Worktrees brings linked worktree management into the Git tool window. It gives teams a single table for checking which worktrees exist, which branch each one uses, and where each checkout lives on disk.

**中文：** Git Worktrees 把 linked worktree 管理放进 Git 工具窗口。团队可以在一张表里看到当前有哪些 worktree、每个 worktree 对应哪个分支，以及它们分别位于哪个磁盘路径。

## UI Navigation / 界面入口

**English:** Open the Worktrees tab from the Git main menu or the Git tool window drop-down. The plugin creates or selects a closable `Worktrees` tab inside the Version Control / Git tool window, while the standalone `Git Worktrees` tool window remains available as a fallback.

**中文：** 可以从 Git 主菜单或 Git 工具窗口下拉菜单打开 Worktrees 标签页。插件会在 Version Control / Git 工具窗口中创建或选中一个可关闭的 `Worktrees` 标签页，同时保留独立 `Git Worktrees` 工具窗口作为回退入口。

## Filtering and Search / 过滤与搜索

![Header filters](screenshots/01-header-filters.png)

**English:** Header filters let users narrow the table by Worktree, Branch, or Location. Filtering is case-insensitive, and repository groups with no matching worktrees are hidden so the visible list stays focused.

**中文：** 表头过滤可以按 Worktree、Branch 或 Location 收窄列表。过滤不区分大小写；如果某个仓库分组下没有匹配项，该分组会被隐藏，让可见列表保持聚焦。

![Column sorting](screenshots/02-column-sorting.png)

**English:** Each column has a sort button that cycles through ascending, descending, and unsorted states. Sorting is applied within each repository group, keeping multi-root projects organized.

**中文：** 每列都有排序按钮，可在升序、降序和未排序之间切换。排序只在各自仓库分组内生效，因此多 root 项目仍能保持清晰结构。

![Speed Search highlights](screenshots/03-speed-search-highlights.png)

**English:** IntelliJ table Speed Search works on worktree rows and highlights matched fragments in place. Users can type a branch prefix, worktree name, or path fragment and jump directly to the relevant row.

**中文：** IntelliJ 表格 Speed Search 会作用于 worktree 行，并在原位置高亮命中的文字片段。用户可以输入分支前缀、worktree 名称或路径片段，直接跳到相关行。

## Worktree Operations / Worktree 操作

**English:** The toolbar and context menu expose the daily actions: Refresh, Checkout, Open, and Delete Worktree. Double-clicking a worktree also opens it as a project.

**中文：** 工具栏和右键菜单提供日常操作：Refresh、Checkout、Open 和 Delete Worktree。双击 worktree 也可以把它作为项目打开。

**English:** Checkout uses `--ignore-other-worktrees` when switching to a branch used by another linked checkout. If local changes or untracked files would be overwritten, the plugin asks before force checkout.

**中文：** 当切换到已被另一个 linked checkout 使用的分支时，Checkout 会使用 `--ignore-other-worktrees`。如果本地变更或未跟踪文件可能被覆盖，插件会先弹窗确认再执行 force checkout。

**English:** Delete Worktree supports both single-row and multi-row deletion. Users can keep the branch, or delete the worktree and branch together. Main worktrees are protected from delete actions.

**中文：** Delete Worktree 支持单行删除和多选批量删除。用户可以只删除 worktree，也可以同时删除 worktree 和对应分支。主 worktree 会受到保护，不会被删除操作命中。

## Branch Menu Integration / 分支菜单集成

**English:** Git Worktrees adds branch-aware actions to the IDE Git Branch menu. When a selected branch is already used by another worktree, users can Checkout Anyway or Delete Branch / Worktree without leaving the normal branch workflow.

**中文：** Git Worktrees 会向 IDE 的 Git Branch 菜单加入感知 worktree 的动作。当选中的分支已经被另一个 worktree 使用时，用户可以在原有分支流程里直接选择 Checkout Anyway 或 Delete Branch / Worktree。

## Multi-root Projects / 多 root 项目

![Multi-root groups](screenshots/04-multi-root-groups.png)

**English:** Repository rows group worktrees from every Git root in the project. This keeps monorepos and multi-module projects readable, whether the roots are named `console`, `api`, `android`, or something project-specific.

**中文：** 仓库分组行会把项目中每个 Git root 的 worktree 分开展示。无论 root 名为 `console`、`api`、`android`，还是项目自己的目录名，多仓库和多模块项目都能保持可读。

## Safety and Performance / 安全与性能

**English:** Git operations refresh affected repositories and report success or failure through IDE notifications. Bulk deletion batches Git commands first, refreshes each repository once, and performs leftover directory cleanup as best-effort pooled work to avoid long visible delete tasks on Windows.

**中文：** Git 操作完成后会刷新受影响仓库，并通过 IDE 通知反馈成功或失败。批量删除会先集中执行 Git 命令，每个仓库只刷新一次，并把残留目录清理放到后台尽力执行，避免 Windows 下可见删除任务时间过长。

## Short Marketplace Summary / Marketplace 短简介

**English:** Manage linked Git worktrees directly from IntelliJ IDEA: browse all roots, filter and sort worktrees, use Speed Search highlighting, open or checkout branches, and safely delete worktrees with optional branch cleanup.

**中文：** 在 IntelliJ IDEA 中直接管理 linked Git worktree：浏览所有 Git root，过滤和排序 worktree，使用 Speed Search 高亮，打开或切换分支，并安全删除 worktree，可选择同步清理分支。
