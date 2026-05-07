package dev.dengchao.idea.plugin.git.worktrees.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionWithDelegate
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataSnapshotProvider
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.vcs.log.VcsLogDataKeys
import dev.dengchao.idea.plugin.git.worktrees.Gw4iBundle
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesOperationsService
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitLogBranchOperationsActionGroup

class GitLogWorktreeBranchOperationGroup private constructor(
    private val nativeAction: AnAction?,
    private val nativeGroup: ActionGroup?,
) : ActionGroup(), DumbAware, ActionWithDelegate<AnAction>, GitLogWorktreeAwareAction {
    constructor() : this(GitLogBranchOperationsActionGroup())

    internal constructor(nativeAction: AnAction?) : this(
        nativeAction,
        nativeAction as? ActionGroup,
    )

    init {
        nativeAction?.let(::copyFrom)
    }

    override fun getDelegate(): AnAction = nativeAction ?: this

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        if (e == null) return AnAction.EMPTY_ARRAY
        val project = e.project ?: return AnAction.EMPTY_ARRAY
        val selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION) ?: return AnAction.EMPTY_ARRAY
        val commit = selection.commits.singleOrNull() ?: return AnAction.EMPTY_ARRAY
        val repository = GitWorktreesOperationsService.getInstance(project)
            .repositories()
            .firstOrNull { it.root == commit.root }
            ?: return AnAction.EMPTY_ARRAY
        val nativeChildren = nativeGroup?.getChildren(e) ?: AnAction.EMPTY_ARRAY
        val contexts = BranchUsedByWorktreeContextResolver.fromGitLogEvent(e, repository)
            .associateBy { it.branchName }
        if (nativeChildren.isEmpty() && contexts.isNotEmpty()) {
            // Lightweight tests do not create the full VCS Log action context that the native group needs.
            // Keep this fallback narrow so production still delegates ordinary branches to Git4Idea.
            return contexts.values.map(::createFallbackBranchGroup).toTypedArray()
        }
        if (contexts.isEmpty()) return nativeChildren

        return nativeChildren.map { replaceDeleteAction(it, contexts, currentContext = null, e) }.toTypedArray()
    }

    private fun createFallbackBranchGroup(context: BranchUsedByWorktreeContext): ActionGroup {
        return copyGroup(
            DefaultBranchGroup(context.branchName),
            listOf(DeleteLinkedWorktreeBranchAction(context)),
        )
    }

    private fun replaceDeleteAction(
        action: AnAction,
        contexts: Map<String, BranchUsedByWorktreeContext>,
        currentContext: BranchUsedByWorktreeContext?,
        e: AnActionEvent?,
    ): AnAction {
        val actionText = action.templatePresentation.text.orEmpty()
        val context = currentContext
            ?: findContextForActionSnapshot(action, e)
            ?: findContextForBranchGroup(actionText, contexts)
        if (context != null && isDeleteBranchAction(action)) {
            return DeleteLinkedWorktreeBranchAction(context)
        }

        if (action !is ActionGroup) return action

        return copyGroup(
            action,
            action.getChildren(e).map { replaceDeleteAction(it, contexts, context, e) },
        )
    }

    private fun isDeleteBranchAction(action: AnAction): Boolean {
        val rootAction = ActionUtil.getDelegateChainRootAction(action)
        return rootAction.javaClass.name == "git4idea.actions.ref.GitDeleteRefAction"
    }

    private fun findContextForActionSnapshot(
        action: AnAction,
        e: AnActionEvent?,
    ): BranchUsedByWorktreeContext? {
        return (action as? DataSnapshotProvider)
            ?.let { BranchUsedByWorktreeContextResolver.fromActionSnapshot(e, it) }
    }

    private fun findContextForBranchGroup(
        actionText: String,
        contexts: Map<String, BranchUsedByWorktreeContext>,
    ): BranchUsedByWorktreeContext? {
        return contexts.entries.firstOrNull { (branchName, _) ->
            actionText == "Branch '$branchName'" || actionText == branchName
        }?.value
    }
}

private class DefaultBranchGroup(branchName: String) : ActionGroup("Branch '$branchName'", true), DumbAware {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> = AnAction.EMPTY_ARRAY
}

internal class DeleteLinkedWorktreeBranchAction(
    private val context: BranchUsedByWorktreeContext,
) : DumbAwareAction(
    Gw4iBundle.message("action.GitWorktrees.Branch.DeleteUsedByWorktree.text"),
    Gw4iBundle.message("action.GitWorktrees.Branch.DeleteUsedByWorktree.description"),
    null,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        GitWorktreesBranchActions.delete(context)
    }
}
