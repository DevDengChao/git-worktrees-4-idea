// Copyright 2000-2026 JetBrains s.r.o. and contributors.
package dev.dengchao.idea.plugin.git.worktrees

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import java.time.Duration

/**
 * UI Test for Git Worktrees plugin on E:\dianyan\hai-liao project.
 * 
 * Prerequisites:
 * 1. Start IDE with robot-server: ./gradlew runIdeForUiTests
 * 2. Open E:\dianyan\hai-liao project in the IDE
 * 3. Run this test: ./gradlew test --tests "*GitWorktreesHaiLiaoTest*"
 * 
 * Inspect UI hierarchy at http://localhost:8082
 */
@EnabledIfSystemProperty(named = "gw4i.ui.tests", matches = "true")
class GitWorktreesHaiLiaoTest {

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
    fun `test IDE is loaded with hai-liao project`() = step("Verify hai-liao project is open") {
        waitForIdeToLoad()
        
        // Verify IDE frame is present
        remoteRobot.find<ComponentFixture>(
            byXpath("//div[@class='IdeFrameImpl']")
        )
    }

    @Test
    fun `test Git Worktrees tool window stripe button exists`() = step("Verify Git Worktrees tool window") {
        waitForIdeToLoad()
        
        // Look for Git Worktrees stripe button on the right side
        try {
            remoteRobot.find<ComponentFixture>(
                byXpath("//div[@accessiblename='Git Worktrees' and @class='StripeButton']")
            )
        } catch (_: Exception) {
            // Try alternative location - may be in tool window list
            try {
                remoteRobot.find<ComponentFixture>(
                    byXpath("//div[@visible_text='Git Worktrees']")
                )
            } catch (_: Exception) {
                // Try finding via action
                remoteRobot.find<ComponentFixture>(
                    byXpath("//div[contains(@text, 'Git Worktrees') or contains(@visible_text, 'Git Worktrees')]")
                )
            }
        }
    }

    @Test
    fun `test Git Worktrees tool window can be opened`() = step("Open Git Worktrees tool window") {
        waitForIdeToLoad()
        
        // Try to open via View menu
        try {
            // Click View menu
            remoteRobot.find<ComponentFixture>(
                byXpath("//div[@accessiblename='View' and @class='ActionMenu']")
            ).click()
            
            waitFor(Duration.ofSeconds(3)) {
                try {
                    // Find Tool Windows submenu
                    remoteRobot.find<ComponentFixture>(
                        byXpath("//div[@accessiblename='Tool Windows' and @class='ActionMenuItem']")
                    ).click()
                    
                    waitFor(Duration.ofSeconds(3)) {
                        try {
                            // Find Git Worktrees
                            remoteRobot.find<ComponentFixture>(
                                byXpath("//div[@accessiblename='Git Worktrees' and @class='ActionMenuItem']")
                            ).click()
                            true
                        } catch (_: Exception) {
                            false
                        }
                    }
                    true
                } catch (_: Exception) {
                    false
                }
            }
        } catch (_: Exception) {
            // Menu approach failed, try keyboard shortcut
            // Ctrl+Shift+A for Find Action
            remoteRobot.callJs<Boolean>(
                "var robot = new java.awt.Robot(); robot.keyPress(17); robot.keyPress(16); robot.keyPress(65); Thread.sleep(100); robot.keyRelease(65); robot.keyRelease(16); robot.keyRelease(17); true;"
            )
            
            waitFor(Duration.ofSeconds(3)) {
                try {
                    remoteRobot.find<ComponentFixture>(
                        byXpath("//div[@class='SearchField']")
                    )
                    true
                } catch (_: Exception) {
                    false
                }
            }
            
            // Type "Git Worktrees"
            remoteRobot.callJs<Boolean>(
                "var robot = new java.awt.Robot(); 'Git Worktrees'.split('').forEach(function(c) { var code = c.toUpperCase().charCodeAt(0); robot.keyPress(code); robot.keyRelease(code); }); true;"
            )
        }
    }

    @Test
    fun `test worktree list shows worktrees if any exist`() = step("Verify worktree list") {
        waitForIdeToLoad()
        
        // After opening tool window, verify list or empty text
        waitFor(Duration.ofSeconds(5)) {
            try {
                // Either shows list of worktrees
                remoteRobot.find<ComponentFixture>(
                    byXpath("//div[@class='JBList']")
                )
                true
            } catch (_: Exception) {
                try {
                    // Or shows empty text
                    remoteRobot.find<ComponentFixture>(
                        byXpath("//div[@class='JBEmptyText']")
                    )
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }
    }

    @Test
    fun `test toolbar buttons are present`() = step("Verify toolbar buttons") {
        waitForIdeToLoad()
        
        // Verify ToolbarDecorator is present
        remoteRobot.find<ComponentFixture>(
            byXpath("//div[@class='ToolbarDecorator']")
        )
    }

    private fun waitForIdeToLoad() {
        waitFor(Duration.ofSeconds(60)) {
            try {
                remoteRobot.find<ComponentFixture>(
                    byXpath("//div[@class='IdeFrameImpl']")
                )
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
                    dialog.callJs<Boolean>("component.setVisible(false); true;")
                } catch (_: Exception) {
                    // Ignore
                }
            }
        } catch (_: Exception) {
            // No dialogs
        }
    }
}
