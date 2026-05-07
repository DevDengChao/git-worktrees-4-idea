package dev.dengchao.idea.plugin.git.worktrees.actions

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger

internal class GitLogWorktreeActionInstaller : AppLifecycleListener {
    override fun appFrameCreated(commandLineArgs: List<String>) {
        GitLogWorktreeActionReplacement.install()
    }

    override fun appStarted() {
        GitLogWorktreeActionReplacement.install()
    }
}

internal object GitLogWorktreeActionReplacement {
    private val LOG = Logger.getInstance(GitLogWorktreeActionReplacement::class.java)

    private const val CHECKOUT_GROUP_ID = "Git.CheckoutGroup"
    private const val BRANCH_OPERATION_GROUP_ID = "Git.BranchOperationGroup"

    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val actionManager = ActionManager.getInstance()
            replace(
                actionManager,
                CHECKOUT_GROUP_ID,
            ) { nativeAction -> GitLogWorktreeCheckoutGroup(nativeAction) }
            replace(
                actionManager,
                BRANCH_OPERATION_GROUP_ID,
            ) { nativeAction -> GitLogWorktreeBranchOperationGroup(nativeAction) }
            installed = true
        }
    }

    internal fun installForTests(actionManager: ActionManager = ActionManager.getInstance()) {
        replace(
            actionManager,
            CHECKOUT_GROUP_ID,
        ) { nativeAction -> GitLogWorktreeCheckoutGroup(nativeAction) }
        replace(
            actionManager,
            BRANCH_OPERATION_GROUP_ID,
        ) { nativeAction -> GitLogWorktreeBranchOperationGroup(nativeAction) }
    }

    internal fun resetForTests() {
        installed = false
    }

    private fun replace(
        actionManager: ActionManager,
        actionId: String,
        replacement: (AnAction) -> AnAction,
    ) {
        val nativeAction = actionManager.getAction(actionId)
        if (nativeAction == null) {
            if (!ApplicationManager.getApplication().isUnitTestMode) {
                LOG.warn("Cannot replace missing Git action: $actionId")
            }
            return
        }
        if (nativeAction is GitLogWorktreeAwareAction) return

        actionManager.replaceAction(actionId, replacement(nativeAction))
    }
}

internal interface GitLogWorktreeAwareAction
