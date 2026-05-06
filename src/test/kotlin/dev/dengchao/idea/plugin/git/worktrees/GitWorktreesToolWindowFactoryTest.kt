package dev.dengchao.idea.plugin.git.worktrees

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.testFramework.PlatformTestUtil
import dev.dengchao.idea.plugin.git.worktrees.ui.GitWorktreesPanel
import dev.dengchao.idea.plugin.git.worktrees.ui.GitWorktreesToolWindowFactory
import java.util.function.Function
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
    fun `test show action is registered in VCS log tab dropdown`() {
        val actionManager = ActionManager.getInstance()
        val dropdown = actionManager.getAction("Vcs.Log.ToolWindow.TabActions.DropDown") as? ActionGroup
        val showAction = actionManager.getAction("GitWorktrees.ShowToolWindow")

        assertNotNull(dropdown)
        assertNotNull(showAction)
        assertTrue(dropdown!!.getChildren(null).contains(showAction))
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
}
