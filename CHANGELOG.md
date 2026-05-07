<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Git Worktrees Changelog

## [Unreleased]

## [1.0.0] - 2026-05-07

### Added

- Add the Git Worktrees plugin for IntelliJ Platform IDEs with bundled Git support.
- Add a Worktrees tab inside the Version Control / Git tool window, with a standalone Git Worktrees tool window fallback.
- List linked worktrees from all project Git roots using `git worktree list --porcelain`.
- Show Worktree, Branch, and Location columns, including main worktree emphasis, detached branch labels, and a current-location checkmark.
- Add per-column filters for Worktree, Branch, and Location.
- Add per-column ascending, descending, and unsorted sort controls that apply within each repository group.
- Add IntelliJ table Speed Search support with visible match highlighting on worktree rows.
- Add repository group rows for multi-root projects, including chevron, double-click, and context-menu collapse and expand controls.
- Add toolbar and context-menu actions for Refresh, Checkout, Open, and Delete Worktree.
- Add Checkout support for linked worktree branches using `--ignore-other-worktrees`, including local-change conflict confirmation and disabled reason tooltips.
- Add Open support for opening a selected worktree as an IDE project.
- Add single and bulk Delete Worktree support, including optional branch deletion and main worktree protection.
- Add Git Branch menu integrations for branches already used by another worktree: Checkout Anyway and Delete Branch / Worktree.
- Run checkout and delete operations in background tasks and report success or failure through IDE notifications.
- Batch bulk worktree deletion and defer best-effort leftover directory cleanup to reduce visible delete time on Windows.
- Add JetBrains plugin icons, New UI tool window icons, marketing screenshots, and bilingual English/Chinese README and Marketplace copy.

### Changed

- Configure the plugin for IntelliJ Platform build `252` and newer.
- Configure GitHub release publishing through signed JetBrains Marketplace upload using repository secrets.
