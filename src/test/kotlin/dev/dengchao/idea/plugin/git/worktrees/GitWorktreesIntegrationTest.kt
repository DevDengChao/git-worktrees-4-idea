// Copyright 2000-2026 JetBrains s.r.o. and contributors.
package dev.dengchao.idea.plugin.git.worktrees

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.LightPlatform4TestCase
import org.junit.Test

/**
 * Integration tests for Git Worktrees plugin.
 * These tests verify the plugin components are properly registered
 * and accessible through the IntelliJ Platform APIs.
 */
class GitWorktreesIntegrationTest : LightPlatform4TestCase() {

    @Test
    fun `test tool window factory is registered`() {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val availableIds = toolWindowManager.availableToolWindowIds

        // Our tool window should be registered
        assert(availableIds.contains("Git Worktrees")) { 
            "Git Worktrees tool window should be registered" 
        }
    }

    @Test
    fun `test actions are registered`() {
        val actionManager = ActionManager.getInstance()
        
        // Verify core actions are registered
        val actionIds = listOf(
            "GitWorktrees.ShowToolWindow",
            "GitWorktrees.Refresh",
            "GitWorktrees.Checkout",
            "GitWorktrees.Open",
            "GitWorktrees.Remove",
            "GitWorktrees.CheckoutInOtherRepo",
            "GitWorktrees.Branch.CheckoutIgnoreOtherWorktrees",
            "GitWorktrees.Branch.DeleteUsedByWorktree",
        )
        
        actionIds.forEach { actionId ->
            val action = actionManager.getAction(actionId)
            assert(action != null) { "Action $actionId should be registered" }
        }
    }

    @Test
    fun `test action groups are registered`() {
        val actionManager = ActionManager.getInstance()
        
        val groupIds = listOf(
            "GitWorktrees.ToolWindow.Toolbar",
            "GitWorktrees.ToolWindow.Popup",
        )
        
        groupIds.forEach { groupId ->
            val group = actionManager.getAction(groupId)
            assert(group != null) { "Action group $groupId should be registered" }
        }
    }
}
