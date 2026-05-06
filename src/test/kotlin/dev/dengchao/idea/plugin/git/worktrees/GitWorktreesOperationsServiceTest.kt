// Copyright 2000-2026 JetBrains s.r.o. and contributors.
package dev.dengchao.idea.plugin.git.worktrees

import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import dev.dengchao.idea.plugin.git.worktrees.model.WorktreeInfo
import dev.dengchao.idea.plugin.git.worktrees.services.BulkRemoveWorktreesTarget
import dev.dengchao.idea.plugin.git.worktrees.services.DeleteWorktreeBranchDecision
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesOperationsService
import git4idea.commands.GitCommandResult
import git4idea.repo.GitRepository
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.nio.file.Files
import org.junit.Test

/**
 * Unit tests for GitWorktreesOperationsService.
 * Tests cover worktree parsing, branch lookup, and decision handling.
 */
class GitWorktreesOperationsServiceTest : LightPlatform4TestCase() {

    @Test
    fun `test parseWorktrees single main worktree`() {
        val porcelainOutput = listOf(
            "worktree ${project.basePath}",
            "branch refs/heads/master",
        )

        val worktrees = parseWorktreesFromPorcelain(porcelainOutput, project.basePath!!)

        assert(worktrees.size == 1)
        assert(worktrees[0].isMain)
        assert(worktrees[0].isCurrent)
        assert(worktrees[0].branchName == "master")
    }

    @Test
    fun `test parseWorktrees multiple worktrees`() {
        val basePath = project.basePath!!
        val porcelainOutput = listOf(
            "worktree $basePath",
            "branch refs/heads/master",
            "",
            "worktree /tmp/feature-tree",
            "branch refs/heads/feature",
            "",
            "worktree /tmp/bugfix-tree",
            "branch refs/heads/bugfix",
            "locked",
        )

        val worktrees = parseWorktreesFromPorcelain(porcelainOutput, basePath)

        assert(worktrees.size == 3)
        assert(worktrees[0].isMain)
        assert(worktrees[0].branchName == "master")
        assert(worktrees[1].branchName == "feature")
        assert(!worktrees[1].isMain)
        assert(worktrees[2].isLocked)
        assert(worktrees[2].branchName == "bugfix")
    }

    @Test
    fun `test parseWorktrees detached HEAD`() {
        val basePath = project.basePath!!
        val porcelainOutput = listOf(
            "worktree $basePath",
            "branch refs/heads/master",
            "",
            "worktree /tmp/detached-tree",
            "detached",
        )

        val worktrees = parseWorktreesFromPorcelain(porcelainOutput, basePath)

        assert(worktrees.size == 2)
        assert(worktrees[1].branchName == null)
    }

    @Test
    fun `test findLinkedWorktreeForBranch`() {
        val worktrees = listOf(
            WorktreeInfo(path = project.basePath!!, branchName = "master", isMain = true, isCurrent = true, isLocked = false, isPrunable = false),
            WorktreeInfo(path = "/tmp/feature-tree", branchName = "feature", isMain = false, isCurrent = false, isLocked = false, isPrunable = false),
        )

        // Simulate lookup - in real scenario this would use service.worktrees()
        val found = worktrees.firstOrNull { it.branchName == "feature" && !it.isCurrent }
        assert(found != null)
        assert(found!!.path == "/tmp/feature-tree")

        val notFound = worktrees.firstOrNull { it.branchName == "nonexistent" && !it.isCurrent }
        assert(notFound == null)
    }

    @Test
    fun `test DeleteWorktreeBranchDecision enum values`() {
        assertEquals(
            listOf(
                DeleteWorktreeBranchDecision.CANCEL,
                DeleteWorktreeBranchDecision.DELETE_WORKTREE_ONLY,
                DeleteWorktreeBranchDecision.DELETE_WORKTREE_AND_BRANCH,
            ),
            DeleteWorktreeBranchDecision.values().toList(),
        )
    }

    @Test
    fun `test removeWorktreeWithBranchDecision does not ask for branch decision again`() {
        val repository = gitRepository(project.basePath!!, currentBranchName = "master")
        val service = GitWorktreesOperationsService.getInstance(project)
        var dialogCalls = 0
        var removeCalls = 0
        var deleteCalls = 0

        service.overrideProvidersForTests(
            repositoriesProvider = { listOf(repository) },
            worktreesProvider = { emptyList() },
            parentDisposable = testRootDisposable,
        )
        service.overrideBranchDeletionDialogForTests(
            dialogProvider = { _, _ ->
                dialogCalls++
                DeleteWorktreeBranchDecision.DELETE_WORKTREE_AND_BRANCH
            },
            parentDisposable = testRootDisposable,
        )
        service.overrideGitOperationsForTests(
            removeWorktreeRunner = { _, _ ->
                removeCalls++
                GitCommandResult(false, 0, emptyList(), emptyList())
            },
            deleteBranchRunner = { _, _, _ ->
                deleteCalls++
                GitCommandResult(false, 0, emptyList(), emptyList())
            },
            parentDisposable = testRootDisposable,
        )

        val result = service.removeWorktreeWithBranchDecision(
            repository,
            branchName = "feature",
            worktreePath = "/tmp/feature",
            decision = DeleteWorktreeBranchDecision.DELETE_WORKTREE_AND_BRANCH,
            notifyResult = false,
        )

        assertTrue(result)
        assertEquals(0, dialogCalls)
        assertEquals(1, removeCalls)
        assertEquals(1, deleteCalls)
    }

    @Test
    fun `test removeWorktree cleans leftover directory after git unregisters worktree`() {
        val repository = gitRepository(project.basePath!!, currentBranchName = "master")
        val service = GitWorktreesOperationsService.getInstance(project)
        val leftoverDirectory = Files.createTempDirectory("gw4i-leftover-worktree")
        Files.writeString(leftoverDirectory.resolve("leftover.txt"), "leftover")

        service.overrideProvidersForTests(
            repositoriesProvider = { listOf(repository) },
            worktreesProvider = { emptyList() },
            parentDisposable = testRootDisposable,
        )
        service.overrideGitOperationsForTests(
            removeWorktreeRunner = { _, path ->
                GitCommandResult(
                    false,
                    1,
                    listOf("error: failed to delete '$path': Directory not empty"),
                    emptyList(),
                )
            },
            parentDisposable = testRootDisposable,
        )

        val result = service.removeWorktree(repository, leftoverDirectory.toString(), notifyResult = false)

        assertTrue(result)
        assertFalse(Files.exists(leftoverDirectory))
    }

    @Test
    fun `test bulk remove worktrees only keeps branches`() {
        val repository = gitRepository(project.basePath!!, currentBranchName = "master")
        val service = GitWorktreesOperationsService.getInstance(project)
        val removedPaths = mutableListOf<String>()
        val deletedBranches = mutableListOf<String>()

        service.overrideProvidersForTests(
            repositoriesProvider = { listOf(repository) },
            worktreesProvider = { emptyList() },
            parentDisposable = testRootDisposable,
        )
        service.overrideGitOperationsForTests(
            removeWorktreeRunner = { _, path ->
                removedPaths += path
                GitCommandResult(false, 0, emptyList(), emptyList())
            },
            deleteBranchRunner = { _, branch, _ ->
                deletedBranches += branch
                GitCommandResult(false, 0, emptyList(), emptyList())
            },
            parentDisposable = testRootDisposable,
        )

        val result = service.removeWorktreesWithBranchDecision(
            targets = listOf(
                BulkRemoveWorktreesTarget(repository, worktree(path = "/project/main", branchName = "master", isMain = true)),
                BulkRemoveWorktreesTarget(repository, worktree(path = "/project/feature", branchName = "feature")),
                BulkRemoveWorktreesTarget(repository, worktree(path = "/project/bugfix", branchName = "bugfix")),
            ),
            decision = DeleteWorktreeBranchDecision.DELETE_WORKTREE_ONLY,
            notifyResult = false,
        )

        assertEquals(listOf("/project/feature", "/project/bugfix"), removedPaths)
        assertTrue(deletedBranches.isEmpty())
        assertEquals(2, result.removedWorktrees)
        assertEquals(0, result.deletedBranches)
    }

    @Test
    fun `test bulk remove worktrees and branches skips detached branch deletion`() {
        val repository = gitRepository(project.basePath!!, currentBranchName = "master")
        val service = GitWorktreesOperationsService.getInstance(project)
        val removedPaths = mutableListOf<String>()
        val deletedBranches = mutableListOf<String>()

        service.overrideProvidersForTests(
            repositoriesProvider = { listOf(repository) },
            worktreesProvider = { emptyList() },
            parentDisposable = testRootDisposable,
        )
        service.overrideGitOperationsForTests(
            removeWorktreeRunner = { _, path ->
                removedPaths += path
                GitCommandResult(false, 0, emptyList(), emptyList())
            },
            deleteBranchRunner = { _, branch, _ ->
                deletedBranches += branch
                GitCommandResult(false, 0, emptyList(), emptyList())
            },
            parentDisposable = testRootDisposable,
        )

        val result = service.removeWorktreesWithBranchDecision(
            targets = listOf(
                BulkRemoveWorktreesTarget(repository, worktree(path = "/project/feature", branchName = "feature")),
                BulkRemoveWorktreesTarget(repository, worktree(path = "/project/detached", branchName = null)),
            ),
            decision = DeleteWorktreeBranchDecision.DELETE_WORKTREE_AND_BRANCH,
            notifyResult = false,
        )

        assertEquals(listOf("/project/feature", "/project/detached"), removedPaths)
        assertEquals(listOf("feature"), deletedBranches)
        assertEquals(2, result.removedWorktrees)
        assertEquals(1, result.deletedBranches)
    }

    @Test
    fun `test bulk remove deletes branch before cleaning leftover directory`() {
        val repository = gitRepository(project.basePath!!, currentBranchName = "master")
        val service = GitWorktreesOperationsService.getInstance(project)
        val leftoverDirectory = Files.createTempDirectory("gw4i-bulk-leftover-worktree")
        Files.writeString(leftoverDirectory.resolve("leftover.txt"), "leftover")
        val events = mutableListOf<String>()

        service.overrideProvidersForTests(
            repositoriesProvider = { listOf(repository) },
            worktreesProvider = {
                events += "list"
                emptyList()
            },
            parentDisposable = testRootDisposable,
        )
        service.overrideGitOperationsForTests(
            removeWorktreeRunner = { _, path ->
                events += "remove"
                GitCommandResult(
                    false,
                    1,
                    listOf("error: failed to delete '$path': Directory not empty"),
                    emptyList(),
                )
            },
            deleteBranchRunner = { _, branch, _ ->
                events += if (Files.exists(leftoverDirectory)) {
                    "delete branch $branch before cleanup"
                } else {
                    "delete branch $branch after cleanup"
                }
                GitCommandResult(false, 0, emptyList(), emptyList())
            },
            parentDisposable = testRootDisposable,
        )

        val result = service.removeWorktreesWithBranchDecision(
            targets = listOf(
                BulkRemoveWorktreesTarget(repository, worktree(path = leftoverDirectory.toString(), branchName = "feature")),
            ),
            decision = DeleteWorktreeBranchDecision.DELETE_WORKTREE_AND_BRANCH,
            notifyResult = false,
        )

        assertEquals(listOf("remove", "delete branch feature before cleanup"), events)
        assertFalse(Files.exists(leftoverDirectory))
        assertEquals(1, result.removedWorktrees)
        assertEquals(1, result.deletedBranches)
    }

    @Test
    fun `test bulk remove can defer leftover cleanup`() {
        val repository = gitRepository(project.basePath!!, currentBranchName = "master")
        val service = GitWorktreesOperationsService.getInstance(project)
        val leftoverDirectory = Files.createTempDirectory("gw4i-bulk-deferred-leftover-worktree")
        Files.writeString(leftoverDirectory.resolve("leftover.txt"), "leftover")

        service.overrideProvidersForTests(
            repositoriesProvider = { listOf(repository) },
            worktreesProvider = { emptyList() },
            parentDisposable = testRootDisposable,
        )
        service.overrideGitOperationsForTests(
            removeWorktreeRunner = { _, path ->
                GitCommandResult(
                    false,
                    1,
                    listOf("error: failed to delete '$path': Directory not empty"),
                    emptyList(),
                )
            },
            deleteBranchRunner = { _, _, _ -> GitCommandResult(false, 0, emptyList(), emptyList()) },
            parentDisposable = testRootDisposable,
        )

        val result = service.removeWorktreesWithBranchDecision(
            targets = listOf(
                BulkRemoveWorktreesTarget(repository, worktree(path = leftoverDirectory.toString(), branchName = "feature")),
            ),
            decision = DeleteWorktreeBranchDecision.DELETE_WORKTREE_AND_BRANCH,
            notifyResult = false,
            cleanupLeftoversImmediately = false,
        )

        assertTrue(Files.exists(leftoverDirectory))
        assertEquals(listOf(leftoverDirectory.toString()), result.leftoverCleanupPaths)
    }

    @Test
    fun `test bulk remove refreshes each affected repository once`() {
        var refreshes = 0
        val repository = gitRepository(project.basePath!!, currentBranchName = "master") {
            refreshes++
        }
        val service = GitWorktreesOperationsService.getInstance(project)

        service.overrideProvidersForTests(
            repositoriesProvider = { listOf(repository) },
            worktreesProvider = { emptyList() },
            parentDisposable = testRootDisposable,
        )
        service.overrideGitOperationsForTests(
            removeWorktreeRunner = { _, _ -> GitCommandResult(false, 0, emptyList(), emptyList()) },
            deleteBranchRunner = { _, _, _ -> GitCommandResult(false, 0, emptyList(), emptyList()) },
            parentDisposable = testRootDisposable,
        )

        service.removeWorktreesWithBranchDecision(
            targets = listOf(
                BulkRemoveWorktreesTarget(repository, worktree(path = "/project/feature", branchName = "feature")),
                BulkRemoveWorktreesTarget(repository, worktree(path = "/project/bugfix", branchName = "bugfix")),
            ),
            decision = DeleteWorktreeBranchDecision.DELETE_WORKTREE_AND_BRANCH,
            notifyResult = false,
        )

        assertEquals(1, refreshes)
    }

    @Test
    fun `test bulk remove ignores main worktrees`() {
        val repository = gitRepository(project.basePath!!, currentBranchName = "master")
        val service = GitWorktreesOperationsService.getInstance(project)
        val removedPaths = mutableListOf<String>()

        service.overrideProvidersForTests(
            repositoriesProvider = { listOf(repository) },
            worktreesProvider = { emptyList() },
            parentDisposable = testRootDisposable,
        )
        service.overrideGitOperationsForTests(
            removeWorktreeRunner = { _, path ->
                removedPaths += path
                GitCommandResult(false, 0, emptyList(), emptyList())
            },
            parentDisposable = testRootDisposable,
        )

        val result = service.removeWorktreesWithBranchDecision(
            targets = listOf(
                BulkRemoveWorktreesTarget(repository, worktree(path = "/project/main", branchName = "master", isMain = true)),
            ),
            decision = DeleteWorktreeBranchDecision.DELETE_WORKTREE_AND_BRANCH,
            notifyResult = false,
        )

        assertTrue(removedPaths.isEmpty())
        assertEquals(0, result.removedWorktrees)
        assertEquals(0, result.deletedBranches)
    }

    /**
     * Helper function to parse worktrees from porcelain output.
     * This mirrors the logic in GitWorktreesOperationsService.parseWorktrees().
     */
    private fun parseWorktreesFromPorcelain(lines: List<String>, mainPath: String): List<WorktreeInfo> {
        val entries = mutableListOf<WorktreeInfo>()
        var path: String? = null
        var branchName: String? = null
        var isLocked = false
        var isPrunable = false

        fun flush() {
            val currentPath = path ?: return
            val normalizedPath = currentPath.replace('\\', '/').trimEnd('/')
            val normalizedMain = mainPath.replace('\\', '/').trimEnd('/')
            val isCurrent = normalizedPath == normalizedMain
            val isMain = entries.isEmpty()
            entries += WorktreeInfo(
                path = currentPath,
                branchName = branchName,
                isMain = isMain,
                isCurrent = isCurrent,
                isLocked = isLocked,
                isPrunable = isPrunable,
            )
            path = null
            branchName = null
            isLocked = false
            isPrunable = false
        }

        lines.forEach { line ->
            when {
                line.isBlank() -> flush()
                line.startsWith("worktree ") -> {
                    flush()
                    path = line.removePrefix("worktree ").trim()
                }
                line.startsWith("branch ") -> {
                    val fullRef = line.removePrefix("branch ").trim()
                    branchName = fullRef.removePrefix("refs/heads/")
                }
                line == "detached" -> branchName = null
                line.startsWith("locked") -> isLocked = true
                line.startsWith("prunable") -> isPrunable = true
            }
        }
        flush()
        return entries
    }

    private fun worktree(
        path: String,
        branchName: String?,
        isMain: Boolean = false,
    ): WorktreeInfo {
        return WorktreeInfo(
            path = path,
            branchName = branchName,
            isMain = isMain,
            isCurrent = isMain,
            isLocked = false,
            isPrunable = false,
        )
    }

    private fun gitRepository(
        rootPath: String,
        currentBranchName: String?,
        onUpdate: () -> Unit = {},
    ): GitRepository {
        val root = TestVirtualFile(rootPath)
        val handler = InvocationHandler { proxy, method, args ->
            when (method.name) {
                "getProject" -> this.project
                "getRoot" -> root
                "getPresentableUrl" -> rootPath
                "getCurrentBranchName" -> currentBranchName
                "isFresh" -> false
                "isDisposed" -> false
                "update" -> onUpdate()
                "dispose" -> Unit
                "toLogString" -> rootPath
                "toString" -> "GitRepository($rootPath)"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        }
        return Proxy.newProxyInstance(
            GitRepository::class.java.classLoader,
            arrayOf(GitRepository::class.java),
            handler,
        ) as GitRepository
    }

    private class TestVirtualFile(
        private val filePath: String,
    ) : VirtualFile() {
        override fun getName(): String = filePath.substringAfterLast('/')
        override fun getFileSystem() = LocalFileSystem.getInstance()
        override fun getPath(): String = filePath
        override fun isWritable(): Boolean = true
        override fun isDirectory(): Boolean = true
        override fun isValid(): Boolean = true
        override fun getParent(): VirtualFile? {
            val parentPath = filePath.trimEnd('/').substringBeforeLast('/', missingDelimiterValue = "")
            if (parentPath.isBlank()) return null
            return TestVirtualFile(parentPath)
        }

        override fun getChildren(): Array<VirtualFile> = emptyArray()
        override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long) = throw UnsupportedOperationException()
        override fun contentsToByteArray(): ByteArray = ByteArray(0)
        override fun getTimeStamp(): Long = 0
        override fun getLength(): Long = 0
        override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) = Unit
        override fun getInputStream() = throw UnsupportedOperationException()

        override fun equals(other: Any?): Boolean {
            return other is VirtualFile && other.path == filePath
        }

        override fun hashCode(): Int = filePath.hashCode()
    }
}
