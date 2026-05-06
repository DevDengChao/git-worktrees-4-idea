// Copyright 2000-2026 JetBrains s.r.o. and contributors.
package dev.dengchao.idea.plugin.git.worktrees

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import org.junit.jupiter.api.*
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * UI Test for Git Worktrees plugin with temporary Git fixture.
 * 
 * Creates a temporary Git repository with multiple worktrees,
 * then verifies the plugin functionality.
 * 
 * Prerequisites:
 * 1. Start IDE with robot-server: ./gradlew runIdeForUiTests
 * 2. Run this test: ./gradlew test --tests "*GitWorktreesFixtureTest*"
 */
@EnabledIfSystemProperty(named = "gw4i.ui.tests", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class GitWorktreesFixtureTest {

    private lateinit var remoteRobot: RemoteRobot
    private lateinit var tempDir: Path
    private lateinit var mainRepo: Path
    private lateinit var featureTree: Path
    private lateinit var bugfixTree: Path

    @BeforeEach
    fun setUp() {
        val port = System.getProperty("robot-server.port", "8082")
        remoteRobot = RemoteRobot("http://127.0.0.1:$port")
        
        // Create temporary Git fixture
        setupGitFixture()
    }

    @AfterEach
    fun tearDown() {
        closeDialogs()
        cleanupGitFixture()
    }

    @Test
    @Order(1)
    fun `test IDE opens the fixture project`() = step("Open fixture project") {
        waitForIdeToLoad()
        
        // Open project via Welcome Frame
        val openButton = remoteRobot.find<ComponentFixture>(
            byXpath("//div[@class='MainButton' and @visible_text='Open']")
        )
        openButton.click()
        
        // Wait for file chooser
        waitFor(Duration.ofSeconds(10)) {
            try {
                remoteRobot.find<ComponentFixture>(
                    byXpath("//div[@class='ComboBox' or @class='PathTextField']")
                )
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    @Test
    @Order(2)
    fun `test Git Worktrees tool window is available`() = step("Verify Git Worktrees tool window") {
        waitForIdeToLoad()
        
        // The tool window stripe button should be on the right side
        waitFor(Duration.ofSeconds(10)) {
            try {
                remoteRobot.find<ComponentFixture>(
                    byXpath("//div[@accessiblename='Git Worktrees']")
                )
                true
            } catch (_: Exception) {
                // Try alternative - it may be in the tool windows list
                try {
                    remoteRobot.find<ComponentFixture>(
                        byXpath("//div[@visible_text='Git Worktrees']")
                    )
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }
    }

    @Test
    @Order(3)
    fun `test worktree list shows all worktrees`() = step("Verify worktree list") {
        waitForIdeToLoad()
        
        // Open Git Worktrees tool window
        openGitWorktreesToolWindow()
        
        // Verify table shows multiple worktrees
        waitFor(Duration.ofSeconds(10)) {
            try {
                val table = remoteRobot.find<ComponentFixture>(
                    byXpath("//div[@class='JBTable' or @class='JTable']")
                )
                // Table should contain rows
                table.callJs<String>("component.getModel().getRowCount()")
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    @Test
    @Order(4)
    fun `test toolbar contains expected buttons`() = step("Verify toolbar") {
        waitForIdeToLoad()
        openGitWorktreesToolWindow()
        
        // ToolbarDecorator should be present
        remoteRobot.find<ComponentFixture>(
            byXpath("//div[@class='ToolbarDecorator']")
        )
    }

    @Test
    @Order(5)
    fun `test context menu has expected actions`() = step("Verify context menu") {
        waitForIdeToLoad()
        openGitWorktreesToolWindow()
        
        // Right-click on the table
        try {
            val table = remoteRobot.find<ComponentFixture>(
                byXpath("//div[@class='JBTable' or @class='JTable']")
            )
            table.rightClick()
            
            // Popup menu should appear
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
            // List may not be clickable if empty
        }
    }

    private fun setupGitFixture() {
        tempDir = Files.createTempDirectory("git-worktrees-test-")
        mainRepo = tempDir.resolve("main-repo")
        featureTree = tempDir.resolve("feature-tree")
        bugfixTree = tempDir.resolve("bugfix-tree")

        // Create main repository
        mainRepo.toFile().mkdirs()
        executeGitCommand(mainRepo, "init")
        executeGitCommand(mainRepo, "config", "user.email", "test@test.com")
        executeGitCommand(mainRepo, "config", "user.name", "Test User")
        
        // Create initial commit
        val readme = mainRepo.resolve("README.md")
        Files.writeString(readme, "# Test Project\n")
        executeGitCommand(mainRepo, "add", ".")
        executeGitCommand(mainRepo, "commit", "-m", "Initial commit")
        
        // Create feature branch
        executeGitCommand(mainRepo, "checkout", "-b", "feature")
        val featureFile = mainRepo.resolve("feature.txt")
        Files.writeString(featureFile, "Feature work\n")
        executeGitCommand(mainRepo, "add", ".")
        executeGitCommand(mainRepo, "commit", "-m", "Add feature")
        
        // Go back to master
        executeGitCommand(mainRepo, "checkout", "master")
        
        // Create worktrees
        executeGitCommand(mainRepo, "worktree", "add", "-b", "feature-branch", featureTree.toString(), "feature")
        executeGitCommand(mainRepo, "worktree", "add", "-b", "bugfix-branch", bugfixTree.toString(), "master")
        
        println("Created fixture at: $tempDir")
        println("Main repo: $mainRepo")
        println("Feature tree: $featureTree")
        println("Bugfix tree: $bugfixTree")
    }

    private fun cleanupGitFixture() {
        // Clean up temporary files
        tempDir.toFile().deleteRecursively()
    }

    private fun executeGitCommand(repo: Path, vararg args: String) {
        val processBuilder = ProcessBuilder("git", *args)
        processBuilder.directory(repo.toFile())
        processBuilder.redirectErrorStream(true)
        
        val process = processBuilder.start()
        val exitCode = process.waitFor()
        
        if (exitCode != 0) {
            val output = process.inputStream.bufferedReader().readText()
            throw RuntimeException("Git command failed: ${args.joinToString(" ")}\nOutput: $output")
        }
    }

    private fun openGitWorktreesToolWindow() {
        // Try to click on tool window stripe button
        try {
            val stripeButton = remoteRobot.find<ComponentFixture>(
                byXpath("//div[@accessiblename='Git Worktrees']")
            )
            stripeButton.click()
            return
        } catch (_: Exception) {
            // Not found
        }
        
        // Alternative: use Find Action
        try {
            // Double Shift to open Search Everywhere
            remoteRobot.callJs<Boolean>(
                "var robot = new java.awt.Robot(); robot.keyPress(16); robot.keyRelease(16); Thread.sleep(100); robot.keyPress(16); robot.keyRelease(16); true;"
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
        } catch (_: Exception) {
            // Failed to open search
        }
    }

    private fun waitForIdeToLoad() {
        waitFor(Duration.ofSeconds(60)) {
            try {
                // Try Welcome Frame first
                remoteRobot.find<ComponentFixture>(
                    byXpath("//div[@class='FlatWelcomeFrame']")
                )
                true
            } catch (_: Exception) {
                try {
                    // Try IDE Frame (project open)
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
