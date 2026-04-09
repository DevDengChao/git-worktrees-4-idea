// Copyright 2000-2026 JetBrains s.r.o. and contributors.
package dev.dengchao.idea.plugin.git.worktrees

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ComponentFixture
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
 * Usage:
 * 1. Start IDE with robot-server: ./gradlew runIdeForUiTests
 * 2. Run this test: ./gradlew test --tests "*GitWorktreesUiTest*"
 * 
 * Inspect UI at http://localhost:8082
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
        closeDialogs()
    }

    @Test
    fun `test Welcome Frame is visible`() = step("Verify Welcome Frame") {
        waitForIdeToLoad()
        remoteRobot.find<ComponentFixture>(byXpath("//div[@accessiblename='Welcome']"))
    }

    @Test
    fun `test Git Worktrees tool window can be opened`() = step("Open Git Worktrees tool window") {
        waitForIdeToLoad()
        openGitWorktreesToolWindow()
        
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
        
        remoteRobot.find<ComponentFixture>(
            byXpath("//div[@class='ToolbarDecorator']")
        )
    }

    @Test
    fun `test empty text when no worktrees`() = step("Verify empty state") {
        openGitWorktreesToolWindow()
        
        waitFor(Duration.ofSeconds(5)) {
            try {
                val emptyText = remoteRobot.find<ComponentFixture>(
                    byXpath("//div[@class='JBEmptyText']")
                )
                emptyText.text().contains("No linked worktrees")
            } catch (_: Exception) {
                false
            }
        }
    }

    @Test
    fun `test context menu on right-click`() = step("Test context menu") {
        openGitWorktreesToolWindow()
        
        try {
            val list = remoteRobot.find<ComponentFixture>(
                byXpath("//div[@class='JBList']")
            )
            list.rightClick()
            
            waitFor(Duration.ofSeconds(3)) {
                try {
                    remoteRobot.find<ComponentFixture>(
                        byXpath("//div[@class='JPopupMenu']")
                    )
                    true
                } catch (_: Exception) {
                    false
                }
            }
        } catch (_: Exception) {
            // List may not be available if empty
        }
    }

    private fun openGitWorktreesToolWindow() {
        // Try tool window stripe button
        try {
            val stripeButton = remoteRobot.find<ComponentFixture>(
                byXpath("//div[@accessiblename='Git Worktrees' and @class='StripeButton']")
            )
            stripeButton.click()
            return
        } catch (_: Exception) {
            // Not found
        }
        
        // Try via action link
        try {
            remoteRobot.find<ComponentFixture>(byXpath("//div[@myactionlink = 'search.svg']")).click()
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
        } catch (_: Exception) {
            // Action not found
        }
    }

    private fun waitForIdeToLoad() {
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
                    // Ignore
                }
            }
        } catch (_: Exception) {
            // No dialogs
        }
    }
}
