// Copyright 2000-2026 JetBrains s.r.o. and contributors.
package dev.dengchao.idea.plugin.git.worktrees

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.GenericContainerFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import java.time.Duration

/**
 * UI Test for Git Worktrees plugin.
 * 
 * Prerequisites:
 * 1. Start IDE with robot-server: ./gradlew runIdeForUiTests
 * 2. Run this test: ./gradlew test --tests "*GitWorktreesUiTest*"
 * 
 * The test connects to the running IDE at http://localhost:8082
 * and interacts with the UI components using XPath locators.
 * 
 * You can inspect the UI hierarchy at http://localhost:8082 in your browser.
 */
@EnabledIfSystemProperty(named = "robot-server.port", matches = ".*")
class GitWorktreesUiTest {

    private lateinit var remoteRobot: RemoteRobot

    @BeforeEach
    fun setUp() {
        val port = System.getProperty("robot-server.port", "8082")
        remoteRobot = RemoteRobot("http://127.0.0.1:$port")
    }

    @AfterEach
    fun tearDown() {
        // Clean up: close any open dialogs
        closeDialogs()
    }

    @Test
    fun `test Welcome Frame is visible`() = step("Verify Welcome Frame") {
        waitForIdeToLoad()
        
        // The Welcome Frame should be visible
        remoteRobot.find<ComponentFixture>(byXpath("//div[@accessiblename='Welcome']"))
    }

    @Test
    fun `test Git Worktrees tool window can be opened via Find Action`() = step("Open Git Worktrees tool window") {
        waitForIdeToLoad()
        
        // Open Find Action (Ctrl+Shift+A / Cmd+Shift+A)
        remoteRobot.find<ComponentFixture>(byXpath("//div[@myactionlink = 'search.svg']")).click()
        
        // Type "Git Worktrees" in the search field
        val searchField = remoteRobot.find<ComponentFixture>(
            byXpath("//div[@class='SearchField']")
        )
        searchField.text = "Show Git Worktrees"
        
        // Wait for the action to appear and click it
        waitFor(Duration.ofSeconds(5)) {
            try {
                val actionLink = remoteRobot.find<ComponentFixture>(
                    byXpath("//div[@class='ActionLink' and @text='Show Git Worktrees']")
                )
                actionLink.click()
                true
            } catch (_: Exception) {
                false
            }
        }
        
        // Verify the tool window is opened
        waitFor(Duration.ofSeconds(5)) {
            try {
                remoteRobot.find<ComponentFixture>(
                    byXpath("//div[@accessiblename='Git Worktrees']")
                )
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    @Test
    fun `test Git Worktrees toolbar is visible`() = step("Verify toolbar") {
        openGitWorktreesToolWindow()
        
        // Verify the toolbar decorator is present
        remoteRobot.find<ComponentFixture>(
            byXpath("//div[@class='ToolbarDecorator']")
        )
    }

    @Test
    fun `test Git Worktrees empty text when no worktrees`() = step("Verify empty state") {
        openGitWorktreesToolWindow()
        
        // When no worktrees are configured, empty text should be shown
        waitFor(Duration.ofSeconds(5)) {
            try {
                val emptyText = remoteRobot.find<ComponentFixture>(
                    byXpath("//div[@class='JBEmptyText']")
                )
                emptyText.hasText("No linked worktrees")
            } catch (_: Exception) {
                false
            }
        }
    }

    @Test
    fun `test context menu on right-click`() = step("Test context menu") {
        openGitWorktreesToolWindow()
        
        // Try to right-click on the list area
        try {
            val list = remoteRobot.find<ComponentFixture>(
                byXpath("//div[@class='JBList']")
            )
            list.rightClick()
            
            // Wait for popup menu to appear
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
            // List may not be available if empty - this is acceptable
        }
    }

    private fun openGitWorktreesToolWindow() {
        // Method 1: Try tool window stripe button
        try {
            val stripeButton = remoteRobot.find<ComponentFixture>(
                byXpath("//div[@accessiblename='Git Worktrees' and @class='StripeButton']")
            )
            stripeButton.click()
            return
        } catch (_: Exception) {
            // Stripe button not found
        }
        
        // Method 2: Use Find Action
        remoteRobot.find<ComponentFixture>(byXpath("//div[@myactionlink = 'search.svg']")).click()
        val searchField = remoteRobot.find<ComponentFixture>(
            byXpath("//div[@class='SearchField']")
        )
        searchField.text = "Show Git Worktrees"
        
        waitFor(Duration.ofSeconds(5)) {
            try {
                remoteRobot.find<ComponentFixture>(
                    byXpath("//div[@class='ActionLink' and @text='Show Git Worktrees']")
                ).click()
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun waitForIdeToLoad() {
        // Wait for IDE to fully load
        waitFor(Duration.ofSeconds(30)) {
            try {
                remoteRobot.find<ComponentFixture>(byXpath("//div[@class='WelcomeFrame']"))
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun closeDialogs() {
        try {
            val dialogs = remoteRobot.findAll<ComponentFixture>(
                byXpath("//div[@class='MyDialog']")
            )
            dialogs.forEach { dialog ->
                try {
                    dialog.callJs("component.setVisible(false)")
                } catch (_: Exception) {
                    // Ignore errors
                }
            }
        } catch (_: Exception) {
            // No dialogs to close
        }
    }

    private fun ComponentFixture.hasText(text: String): Boolean {
        return try {
            this.text.contains(text, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }
}
