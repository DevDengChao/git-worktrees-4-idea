package dev.dengchao.idea.plugin.git.worktrees.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionWithDelegate
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.vcs.log.VcsLogDataKeys
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesOperationsService
import git4idea.branch.GitBrancher
import git4idea.i18n.GitBundle
import git4idea.log.GitRefManager
import git4idea.repo.GitRepository

class GitLogWorktreeCheckoutGroup private constructor(
    private val nativeAction: AnAction?,
    private val nativeGroup: ActionGroup?,
) : ActionGroup(
    GitBundle.message("git.log.action.checkout.group"),
    false,
), DumbAware, ActionWithDelegate<AnAction>, GitLogWorktreeAwareAction {
    constructor() : this(null)

    internal constructor(nativeAction: AnAction?) : this(
        nativeAction,
        nativeAction as? ActionGroup,
    )

    init {
        templatePresentation.isHideGroupIfEmpty = true
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
        val contexts = BranchUsedByWorktreeContextResolver.fromGitLogEvent(e, repository)
            .associateBy { it.branchName }
        val nativeChildren = nativeChildren(e, repository, commit.hash.toShortString())
        if (contexts.isEmpty()) return nativeChildren

        return nativeChildren.map { replaceCheckoutAction(it, contexts, e) }.toTypedArray()
    }

    private fun nativeChildren(
        e: AnActionEvent,
        repository: GitRepository,
        shortHash: String,
    ): Array<AnAction> {
        nativeGroup?.let { return it.getChildren(e) }

        val refNames = localBranchNames(e, repository)
        val hasBranchItems = refNames.isNotEmpty()
        return arrayOf(
            DefaultActionGroup(
                GitBundle.message("git.log.action.checkout.group"),
                refNames.map { CheckoutGitLogBranchAction(repository, it) } +
                    listOf(checkoutRevisionAction(shortHash, hasBranchItems)),
            ).apply { isPopup = hasBranchItems },
        )
    }

    private fun localBranchNames(e: AnActionEvent, repository: GitRepository): List<String> {
        val currentBranchName = repository.currentBranchName
        return e.getData(VcsLogDataKeys.VCS_LOG_REFS)
            .orEmpty()
            .filter { it.root == repository.root && it.type == GitRefManager.LOCAL_BRANCH }
            .map { it.name }
            .filterNot { it == currentBranchName }
            .distinct()
    }

    private fun replaceCheckoutAction(
        action: AnAction,
        contexts: Map<String, BranchUsedByWorktreeContext>,
        e: AnActionEvent?,
    ): AnAction {
        val context = contexts[action.templatePresentation.text.orEmpty()]
        if (context != null) return CheckoutLinkedWorktreeBranchAction(context)

        if (action !is ActionGroup) return action

        return copyGroup(action, action.getChildren(e).map { replaceCheckoutAction(it, contexts, e) })
    }
}

private class CheckoutGitLogBranchAction(
    private val repository: GitRepository,
    branchName: String,
) : DumbAwareAction(branchName) {
    private val branchName = branchName

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        GitBrancher.getInstance(repository.project).checkout(branchName, false, listOf(repository), null)
    }
}

private fun checkoutRevisionAction(shortHash: String, hasBranchItems: Boolean): AnAction {
    val action = ActionUtil.wrap("Git.CheckoutRevision")
    action.templatePresentation.text = if (hasBranchItems) {
        GitBundle.message("git.log.action.checkout.revision.short.text", shortHash)
    } else {
        GitBundle.message("git.log.action.checkout.revision.full.text", shortHash)
    }
    return action
}

internal class CheckoutLinkedWorktreeBranchAction(
    private val context: BranchUsedByWorktreeContext,
) : DumbAwareAction(context.branchName) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = context.repository.project
        val service = GitWorktreesOperationsService.getInstance(project)
        if (!service.askCheckoutUsedByWorktreeConfirmation(context.branchName, context.worktree.path)) return

        service.checkoutBranchIgnoringOtherWorktreesAsync(context.repository, context.branchName)
    }
}

internal fun copyGroup(
    original: ActionGroup,
    children: List<AnAction>,
): DefaultActionGroup {
    val text = original.templatePresentation.text
    val group = if (text == null) DefaultActionGroup(children) else DefaultActionGroup(text, children)
    group.isPopup = original.isPopup
    group.templatePresentation.description = original.templatePresentation.description
    group.templatePresentation.icon = original.templatePresentation.icon
    return group
}
