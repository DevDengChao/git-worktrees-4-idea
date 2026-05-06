package dev.dengchao.idea.plugin.git.worktrees

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.ui.components.JBList
import dev.dengchao.idea.plugin.git.worktrees.ui.GitWorktreesPanel
import dev.dengchao.idea.plugin.git.worktrees.ui.GitWorktreesToolWindowFactory
import java.util.function.Function
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
    fun `test tool window popup is installed with resolved action group`() {
        val action = ActionManager.getInstance().getAction(GitWorktreesToolWindowFactory.POPUP_ACTION_GROUP_ID) as ActionGroup
        val list = JBList<Any>()

        val handler = GitWorktreesPanel.installToolWindowPopupForTests(list)

        assertNotNull(handler)
        assertTrue(list.mouseListeners.contains(handler))
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
