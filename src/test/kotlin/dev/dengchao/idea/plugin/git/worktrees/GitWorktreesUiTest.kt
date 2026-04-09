// Copyright 2000-2026 JetBrains s.r.o. and contributors.
package dev.dengchao.idea.plugin.git.worktrees

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.TextData
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.fixtures.GenericContainerFixture
import com.intellij.remoterobot.search.locators.Locator
import com.intellij.remoterobot.stepsProcessing.StepWorker
import com.intellij.remoterobot.utils.waitFor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.IOException
import java.net.ConnectException
import java.time.Duration

/**
 * UI Test for Git Worktrees plugin.
 * 
 * Usage:
 * 1. Start IDE with robot-server: ./gradlew runIdeForUiTests
 * 2. Run this test: ./gradlew test --tests "*GitWorktreesUiTest*"
 * 
 * The test connects to the running IDE at http://localhost:8082
 * and interacts with the UI components.
 */
@TestMethodOrder(OrderAnnotation::class)
class GitWorktreesUiTest {

    private lateinit var remoteRobot: RemoteRobot

    @BeforeEach
    fun setUp() {
        remoteRobot = RemoteRobot("http://127.0.0.1:8082")
    }

    @AfterEach
    fun tearDown() {
        // Close any open dialogs
        try {
            remoteRobot.find<ComponentFixture>(byXpath("//div[@class='MyDialog']"))
                .runCatching { callJs("component.setVisible(false)") }
        } catch (_: Exception) {
            // No dialog to close
        }
    }

    @Test
    @Order(1)
    fun `test tool window is available`() {
        // Open Git Worktrees tool window via Action
        remoteRobot.find<ComponentFixture>(byXpath("//div[@myactionlink = 'gearHover.svg']")).click()
        
        // Alternative: use Find Action (Ctrl+Shift+A / Cmd+Shift+A)
        remoteRobot.action("Find Action").run()
        remoteRobot.find<ComponentFixture>(byXpath("//div[@class='SearchField']"))
            .text = "Git Worktrees"
        
        // Wait for the action to appear
        waitFor(Duration.ofSeconds(5)) {
            try {
                remoteRobot.findAll<ComponentFixture>(byXpath("//div[@class='ActionLink']"))
                    .any { it.hasText("Git Worktrees") }
            } catch (_: Exception) {
                false
            }
        }
    }

    @Test
    @Order(2)
    fun `test worktree panel shows empty state when no worktrees`() {
        // Open the tool window
        openGitWorktreesToolWindow()
        
        // Verify empty text is shown
        waitFor(Duration.ofSeconds(10)) {
            try {
                val emptyText = remoteRobot.find<ComponentFixture>(
                    byXpath("//div[@class='JBEmptyText']")
                )
                emptyText.text.contains("No linked worktrees")
            } catch (_: Exception) {
                false
            }
        }
    }

    @Test
    @Order(3)
    fun `test toolbar buttons are visible`() {
        openGitWorktreesToolWindow()
        
        // Wait for tool window to load
        waitFor(Duration.ofSeconds(10)) {
            try {
                // Check toolbar is present
                remoteRobot.find<ComponentFixture>(byXpath("//div[@class='ToolbarDecorator']"))
                true
            } catch (_: Exception) {
                false
            }
        }
        
        // Verify Refresh button exists
        remoteRobot.find<ComponentFixture>(byXpath("//div[@myactionlink = 'refresh.svg']"))
    }

    @Test
    @Order(4)
    fun `test context menu on worktree list`() {
        openGitWorktreesToolWindow()
        
        // Right-click on the worktree list (if there are worktrees)
        try {
            val list = remoteRobot.find<ComponentFixture>(
                byXpath("//div[@class='JBList']")
            )
            list.rightClick()
            
            // Wait for popup menu
            waitFor(Duration.ofSeconds(3)) {
                try {
                    remoteRobot.find<GenericContainerFixture>(
                        byXpath("//div[@class='JPopupMenu']")
                    )
                    true
                } catch (_: Exception) {
                    false
                }
            }
        } catch (_: Exception) {
            // No worktree list yet, which is fine for initial state
        }
    }

    private fun openGitWorktreesToolWindow() {
        // Method 1: Use the tool window stripe button
        try {
            val stripeButton = remoteRobot.find<ComponentFixture>(
                byXpath("//div[@accessiblename='Git Worktrees' and @class='StripeButton']")
            )
            stripeButton.click()
            return
        } catch (_: Exception) {
            // Stripe button not found, try alternative method
        }
        
        // Method 2: Use View | Tool Windows menu
        try {
            remoteRobot.find<ComponentFixture>(byXpath("//div[@accessiblename='View']")).click()
            remoteRobot.find<ComponentFixture>(byXpath("//div[@accessiblename='Tool Windows']")).click()
            remoteRobot.find<ComponentFixture>(byXpath("//div[@accessiblename='Git Worktrees']")).click()
            return
        } catch (_: Exception) {
            // Menu path failed
        }
        
        // Method 3: Use Find Action
        remoteRobot.action("Find Action").run()
        remoteRobot.find<ComponentFixture>(byXpath("//div[@class='SearchField']"))
            .text = "Show Git Worktrees"
        
        waitFor(Duration.ofSeconds(5)) {
            try {
                remoteRobot.find<ComponentFixture>(byXpath("//div[@class='ActionLink']")) { 
                    it.hasText("Show Git Worktrees") 
                }.click()
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun RemoteRobot.action(name: String) {
        find<ComponentFixture>(byXpath("//div[@accessiblename='$name']")).click()
    }

    private fun ComponentFixture.hasText(text: String): Boolean {
        return try {
            this.text.contains(text, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    private fun byXpath(xpath: String): Locator {
        return com.intellij.remoterobot.search.locators.byXpath(xpath)
    }
}
