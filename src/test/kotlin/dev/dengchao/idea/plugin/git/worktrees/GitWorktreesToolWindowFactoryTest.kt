package dev.dengchao.idea.plugin.git.worktrees

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import dev.dengchao.idea.plugin.git.worktrees.actions.CheckoutSelectedWorktreeAction
import dev.dengchao.idea.plugin.git.worktrees.model.WorktreeInfo
import dev.dengchao.idea.plugin.git.worktrees.ui.GitWorktreesPanel
import dev.dengchao.idea.plugin.git.worktrees.ui.GitWorktreesToolWindowFactory
import dev.dengchao.idea.plugin.git.worktrees.ui.GitWorktreesDataKeys
import java.util.function.Function
import javax.swing.JMenuItem
import javax.swing.JTable
import kotlinx.coroutines.runBlocking
import org.junit.Test

class GitWorktreesToolWindowFactoryTest : LightPlatform4TestCase() {

    @Test
    fun `test tool window remains available before repositories initialize`() {
        val factory = GitWorktreesToolWindowFactory()

        assertTrue(factory.shouldBeAvailable(project))
    }

    @Test
    fun `test tool window popup action group is registered`() {
        val action = ActionManager.getInstance().getAction(GitWorktreesToolWindowFactory.POPUP_ACTION_GROUP_ID)

        assertNotNull(action)
        assertTrue(action is ActionGroup)
    }

    @Test
    fun `test tool window popup does not include Checkout Here`() {
        val actionGroup = ActionManager.getInstance().getAction(GitWorktreesToolWindowFactory.POPUP_ACTION_GROUP_ID) as ActionGroup
        val childIds = actionGroup.getChildren(null)
            .filterNot { it is Separator }
            .mapNotNull { ActionManager.getInstance().getId(it) }

        assertEquals(
            listOf(
                "GitWorktrees.ToggleRepositoryCollapsed",
                "GitWorktrees.Checkout",
                "GitWorktrees.Open",
                "GitWorktrees.Remove",
                "GitWorktrees.Refresh",
            ),
            childIds,
        )
        assertFalse(childIds.contains("GitWorktrees.CheckoutInOtherRepo"))
    }

    @Test
    fun `test show action is registered in VCS log tab dropdown`() {
        val actionManager = ActionManager.getInstance()
        val dropdown = actionManager.getAction("Vcs.Log.ToolWindow.TabActions.DropDown") as? ActionGroup
        val showAction = actionManager.getAction("GitWorktrees.ShowToolWindow")

        assertNotNull(dropdown)
        assertNotNull(showAction)
        assertTrue(dropdown!!.getChildren(null).contains(showAction))
    }

    @Test
    fun `test show action exposes GW4I secondary menu text`() {
        val action = ActionManager.getInstance().getAction("GitWorktrees.ShowToolWindow")
        val event = TestActionEvent.createTestEvent(CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
            sink[PlatformDataKeys.PROJECT] = project
        })

        action.update(event)

        assertEquals(
            Gw4iBundle.message("action.GitWorktrees.ShowToolWindow.secondary.text"),
            event.presentation.getClientProperty(ActionUtil.SECONDARY_TEXT),
        )
    }

    @Test
    fun `test stripe tool window opens Worktrees tab in Git tool window`() {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val vcsToolWindow = toolWindowManager.getToolWindow(ToolWindowId.VCS)
            ?: toolWindowManager.registerToolWindow(RegisterToolWindowTask.notClosable(ToolWindowId.VCS, ToolWindowAnchor.BOTTOM))
        val legacyToolWindow = toolWindowManager.getToolWindow(GitWorktreesToolWindowFactory.TOOLWINDOW_ID)
            ?: toolWindowManager.registerToolWindow(
                RegisterToolWindowTask.notClosable(GitWorktreesToolWindowFactory.TOOLWINDOW_ID, ToolWindowAnchor.RIGHT),
            )
        val factory = GitWorktreesToolWindowFactory()

        factory.createToolWindowContent(project, legacyToolWindow)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertNotNull(vcsToolWindow.contentManager.findContent("Worktrees"))
        assertEquals(0, legacyToolWindow.contentManager.contentCount)
    }

    @Test
    fun `test initialized stripe tool window redirects when shown again`() {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val vcsToolWindow = toolWindowManager.getToolWindow(ToolWindowId.VCS)
            ?: toolWindowManager.registerToolWindow(RegisterToolWindowTask.notClosable(ToolWindowId.VCS, ToolWindowAnchor.BOTTOM))
        val legacyToolWindow = toolWindowManager.getToolWindow(GitWorktreesToolWindowFactory.TOOLWINDOW_ID)
            ?: toolWindowManager.registerToolWindow(
                RegisterToolWindowTask.notClosable(GitWorktreesToolWindowFactory.TOOLWINDOW_ID, ToolWindowAnchor.RIGHT),
            )
        val factory = GitWorktreesToolWindowFactory()
        runBlocking {
            factory.manage(legacyToolWindow, toolWindowManager)
        }

        @Suppress("DEPRECATION")
        project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC)
            .toolWindowShown(GitWorktreesToolWindowFactory.TOOLWINDOW_ID, legacyToolWindow)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertNotNull(vcsToolWindow.contentManager.findContent("Worktrees"))
        assertEquals(0, legacyToolWindow.contentManager.contentCount)
    }

    @Test
    fun `test tool window popup is installed with resolved action group`() {
        val action = ActionManager.getInstance().getAction(GitWorktreesToolWindowFactory.POPUP_ACTION_GROUP_ID) as ActionGroup
        val table = JTable()

        val handler = GitWorktreesPanel.installToolWindowPopupForTests(table)

        assertNotNull(handler)
        assertTrue(table.mouseListeners.contains(handler))
        assertSame(action, capturedActionGroup(handler!!, null))
    }

    @Test
    fun `test disabled checkout menu item shows hover tooltip with disabled reason`() {
        val checkoutAction = CheckoutSelectedWorktreeAction()
        val worktree = WorktreeInfo(
            path = "/tmp/detached-tree",
            branchName = null,
            isMain = false,
            isCurrent = false,
            isLocked = false,
            isPrunable = false,
        )
        val event = TestActionEvent.createTestEvent(CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
            sink[PlatformDataKeys.PROJECT] = project
            sink[GitWorktreesDataKeys.CURRENT_REPOSITORY] = gitRepository(currentBranchName = "master")
            sink[GitWorktreesDataKeys.SELECTED_WORKTREE] = worktree
        })
        checkoutAction.update(event)
        val menuItem = JMenuItem(event.presentation.text).apply {
            name = event.presentation.text
            isEnabled = event.presentation.isEnabled
            putClientProperty(GitWorktreesPanel.ACTION_DESCRIPTION_PROPERTY, event.presentation.description)
        }

        GitWorktreesPanel.applyDisabledActionTooltipsForTests(menuItem)

        assertFalse(menuItem.isEnabled)
        assertEquals(
            Gw4iBundle.message("action.GitWorktrees.Checkout.disabled.detached"),
            menuItem.toolTipText,
        )
    }

    private fun capturedActionGroup(handler: Any, actionManager: ActionManager?): ActionGroup? {
        var currentClass: Class<*>? = handler.javaClass
        while (currentClass != null) {
            currentClass.declaredFields.firstOrNull { ActionGroup::class.java.isAssignableFrom(it.type) }?.let { field ->
                field.isAccessible = true
                return field.get(handler) as ActionGroup
            }
            currentClass.declaredFields.firstOrNull { Function::class.java.isAssignableFrom(it.type) }?.let { field ->
                field.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val groupFunction = field.get(handler) as Function<ActionManager?, ActionGroup?>
                return groupFunction.apply(actionManager)
            }
            currentClass = currentClass.superclass
        }
        return null
    }

    private fun gitRepository(currentBranchName: String?): git4idea.repo.GitRepository {
        val root = object : com.intellij.openapi.vfs.VirtualFile() {
            override fun getName(): String = "project"
            override fun getFileSystem() = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
            override fun getPath(): String = "/project"
            override fun isWritable(): Boolean = true
            override fun isDirectory(): Boolean = true
            override fun isValid(): Boolean = true
            override fun getParent(): com.intellij.openapi.vfs.VirtualFile? = null
            override fun getChildren(): Array<com.intellij.openapi.vfs.VirtualFile> = emptyArray()
            override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long) = throw UnsupportedOperationException()
            override fun contentsToByteArray(): ByteArray = ByteArray(0)
            override fun getTimeStamp(): Long = 0
            override fun getLength(): Long = 0
            override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) = Unit
            override fun getInputStream() = throw UnsupportedOperationException()
        }
        val handler = java.lang.reflect.InvocationHandler { proxy, method, args ->
            when (method.name) {
                "getProject" -> project
                "getRoot" -> root
                "getPresentableUrl" -> "/project"
                "getCurrentBranchName" -> currentBranchName
                "isFresh" -> false
                "isDisposed" -> false
                "update" -> Unit
                "dispose" -> Unit
                "toLogString" -> "/project"
                "toString" -> "GitRepository(/project)"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        }
        return java.lang.reflect.Proxy.newProxyInstance(
            git4idea.repo.GitRepository::class.java.classLoader,
            arrayOf(git4idea.repo.GitRepository::class.java),
            handler,
        ) as git4idea.repo.GitRepository
    }
}
