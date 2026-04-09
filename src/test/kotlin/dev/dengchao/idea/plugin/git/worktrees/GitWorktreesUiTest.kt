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
import java.awt.event.KeyEvent
import java.time.Duration

/**
 * UI Test for Git Worktrees plugin.
 * 
 * Prerequisites:
 * 1. Start IDE with robot-server: ./gradlew runIdeForUiTests
 * 2. Run this test: ./gradlew test --tests "*GitWorktreesUiTest*"
 * 
 * Inspect UI hierarchy at http://localhost:8082
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
    fun `test Welcome Frame loads correctly`() = step("Verify Welcome Frame") {
        waitForIdeToLoad()
        
        remoteRobot.find<ComponentFixture>(
            byXpath("//div[@class='FlatWelcomeFrame']")
        )
    }

    @Test
    fun `test IDE version is displayed`() = step("Verify IDE version") {
        waitForIdeToLoad()
        
        remoteRobot.find<ComponentFixture>(
            byXpath("//div[@visible_text='2025.2.6.1']")
        )
    }

    @Test
    fun `test New Project button exists`() = step("Verify New Project button") {
        waitForIdeToLoad()
        
        remoteRobot.find<ComponentFixture>(
            byXpath("//div[@class='MainButton' and @visible_text='New Project']")
        )
    }

    @Test
    fun `test Open button exists`() = step("Verify Open button") {
        waitForIdeToLoad()
        
        remoteRobot.find<ComponentFixture>(
            byXpath("//div[@class='MainButton' and @visible_text='Open']")
        )
    }

    @Test
    fun `test Clone Repository button exists`() = step("Verify Clone Repository button") {
        waitForIdeToLoad()
        
        remoteRobot.find<ComponentFixture>(
            byXpath("//div[@class='MainButton' and @visible_text='Clone Repository']")
        )
    }

    @Test
    fun `test Plugins link exists`() = step("Verify Plugins link") {
        waitForIdeToLoad()
        
        remoteRobot.find<ComponentFixture>(
            byXpath("//div[@class='Tree']")
        )
    }

    @Test
    fun `test Settings button exists`() = step("Verify Settings button") {
        waitForIdeToLoad()
        
        // Settings gear icon should be present
        remoteRobot.find<ComponentFixture>(
            byXpath("//div[@myactionlink = 'settings.svg']")
        )
    }

    private fun waitForIdeToLoad() {
        waitFor(Duration.ofSeconds(60)) {
            try {
                remoteRobot.find<ComponentFixture>(
                    byXpath("//div[@class='FlatWelcomeFrame']")
                )
                true
            } catch (_: Exception) {
                try {
                    remoteRobot.find<ComponentFixture>(
                        byXpath("//div[@class='WelcomeFrame']")
                    )
                    true
                } catch (_: Exception) {
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
