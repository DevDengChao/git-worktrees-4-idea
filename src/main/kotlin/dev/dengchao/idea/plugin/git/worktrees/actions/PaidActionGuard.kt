package dev.dengchao.idea.plugin.git.worktrees.actions

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import dev.dengchao.idea.plugin.git.worktrees.Gw4iBundle
import dev.dengchao.idea.plugin.git.worktrees.licensing.Gw4iLicense

private const val MARKETPLACE_URL =
    "https://plugins.jetbrains.com/plugin/26972-git-worktrees--gw4i"

/**
 * Guards paid GW4I actions.
 *
 * Call [checkAndNotify] at the top of [actionPerformed] for every paid action.
 * When the user is not licensed, a dialog explains the limitation and returns
 * `false` so the caller can bail out early.
 *
 * Paid features: Checkout, Delete Worktree, Git Branch / Git Log worktree-aware
 * branch checkout / delete.
 *
 * Free features: list, filter, sort, search, open, refresh, collapse/expand.
 */
object PaidActionGuard {
    private var notifyOverride: ((Project?) -> Unit)? = null

    /**
     * Returns `true` when the action is allowed to proceed (licensed or unknown).
     * Returns `false` when the user is blocked and shows an explanation dialog.
     */
    fun checkAndNotify(project: Project?): Boolean {
        if (Gw4iLicense.isAllowed()) return true
        notify(project)
        return false
    }

    private fun notify(project: Project?) {
        val override = notifyOverride
        if (override != null) {
            override(project)
            return
        }
        val result = Messages.showDialog(
            project,
            Gw4iBundle.message("gw4i.licensing.paid.feature.message"),
            Gw4iBundle.message("gw4i.licensing.paid.feature.title"),
            arrayOf(
                Gw4iBundle.message("gw4i.licensing.paid.feature.learn.more"),
                Gw4iBundle.message("gw4i.licensing.paid.feature.close"),
            ),
            0,
            Messages.getInformationIcon(),
        )
        if (result == 0) {
            BrowserUtil.browse(MARKETPLACE_URL)
        }
    }

    /** Override the notification shown to users in tests. */
    internal fun overrideNotifyForTests(notify: (Project?) -> Unit, parentDisposable: Disposable) {
        notifyOverride = notify
        Disposer.register(parentDisposable) { notifyOverride = null }
    }
}
