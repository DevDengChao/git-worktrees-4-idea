package dev.dengchao.idea.plugin.git.worktrees.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionWithDelegate
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAware
import git4idea.GitReference
import git4idea.actions.ref.GitSingleRefAction
import git4idea.repo.GitRepository

class GitWorktreeBranchActionGroup(
    private val nativeAction: AnAction,
) : ActionGroup(), DumbAware, ActionWithDelegate<AnAction>, GitLogWorktreeAwareAction {
    private val nativeGroup = nativeAction as? ActionGroup

    init {
        copyFrom(nativeAction)
    }

    override fun getDelegate(): AnAction = nativeAction

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        return nativeGroup?.getChildren(e)
            ?.map(::replaceNativeBranchAction)
            ?.toTypedArray()
            ?: AnAction.EMPTY_ARRAY
    }

    private fun replaceNativeBranchAction(action: AnAction): AnAction {
        val rootAction = ActionUtil.getDelegateChainRootAction(action)
        return when (rootAction.javaClass.name) {
            "git4idea.actions.ref.GitCheckoutAction" -> CheckoutSelectedBranchIgnoringOtherWorktreesAction(action)
            "git4idea.actions.ref.GitDeleteRefAction" -> DeleteSelectedBranchUsedByWorktreeAction(action)
            else -> action
        }
    }
}

private class CheckoutSelectedBranchIgnoringOtherWorktreesAction(
    private val nativeAction: AnAction,
) : GitSingleRefAction<GitReference>({ nativeAction.templatePresentation.text.orEmpty() }) {
    init {
        copyPresentationFrom(nativeAction)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun updateIfEnabledAndVisible(
        e: AnActionEvent,
        project: com.intellij.openapi.project.Project,
        repositories: List<GitRepository>,
        reference: GitReference,
    ) {
        ActionUtil.updateAction(nativeAction, e)
        val context = BranchUsedByWorktreeContextResolver.fromBranchPopupEvent(e) ?: return
        e.presentation.isEnabledAndVisible = true
        e.presentation.text = nativeAction.templatePresentation.text
        e.presentation.description = nativeAction.templatePresentation.description
    }

    override fun actionPerformed(
        e: AnActionEvent,
        project: com.intellij.openapi.project.Project,
        repositories: List<GitRepository>,
        reference: GitReference,
    ) {
        val context = BranchUsedByWorktreeContextResolver.fromBranchPopupEvent(e)
        if (context == null) {
            ActionUtil.performAction(nativeAction, e)
            return
        }

        GitWorktreesBranchActions.checkout(context)
    }
}

private class DeleteSelectedBranchUsedByWorktreeAction(
    private val nativeAction: AnAction,
) : GitSingleRefAction<GitReference>({ nativeAction.templatePresentation.text.orEmpty() }) {
    init {
        copyPresentationFrom(nativeAction)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun updateIfEnabledAndVisible(
        e: AnActionEvent,
        project: com.intellij.openapi.project.Project,
        repositories: List<GitRepository>,
        reference: GitReference,
    ) {
        ActionUtil.updateAction(nativeAction, e)
        val context = BranchUsedByWorktreeContextResolver.fromBranchPopupEvent(e) ?: return
        e.presentation.isEnabledAndVisible = true
        e.presentation.text = nativeAction.templatePresentation.text
        e.presentation.description = nativeAction.templatePresentation.description
    }

    override fun actionPerformed(
        e: AnActionEvent,
        project: com.intellij.openapi.project.Project,
        repositories: List<GitRepository>,
        reference: GitReference,
    ) {
        val context = BranchUsedByWorktreeContextResolver.fromBranchPopupEvent(e)
        if (context == null) {
            ActionUtil.performAction(nativeAction, e)
            return
        }

        GitWorktreesBranchActions.delete(context)
    }
}

private fun AnAction.copyPresentationFrom(action: AnAction) {
    templatePresentation.text = action.templatePresentation.text
    templatePresentation.description = action.templatePresentation.description
    templatePresentation.icon = action.templatePresentation.icon
}
