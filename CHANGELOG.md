<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Git Worktrees | GW4I Changelog

## [Unreleased]

## [1.1.0] - 2026-05-07

### Changed

- Rename the Marketplace plugin display name to `Git Worktrees | GW4I` to avoid a Marketplace name collision while keeping the existing plugin ID, tool window, and in-IDE Worktrees entry stable.

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
- Add Git Log branch-label menu integrations so Checkout and Delete Branch can handle branches that are already used by linked worktrees, including branch names with `/`.
- Add runtime wrapping for native Git Branch checkout/delete actions only when a branch is used by another worktree, keeping ordinary branch actions unchanged.
- Add Git metadata and action data based worktree conflict resolution for branch menus, avoiding full worktree listing during menu updates.
- Add a `By GW4I` secondary marker to the Git menu entry and a compact provider note in the Worktrees panel toolbar.
- Run checkout and delete operations in background tasks and report success or failure through IDE notifications.
- Batch bulk worktree deletion and defer best-effort leftover directory cleanup to reduce visible delete time on Windows.
- Defer leftover directory cleanup for single worktree deletion after Git unregisters the worktree, so branch deletion and repository refresh finish before best-effort physical cleanup.
- Add JetBrains plugin icons, New UI tool window icons, marketing screenshots, and bilingual English/Chinese README and Marketplace copy.

### Changed

- Configure the plugin for IntelliJ Platform build `252` and newer.
- Configure GitHub release publishing through signed JetBrains Marketplace upload using repository secrets.
