package dev.dengchao.idea.plugin.git.worktrees

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatform4TestCase
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesOperationsService
import git4idea.repo.GitRepository
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import org.junit.Test

class GitWorktreesRepositorySelectionTest : LightPlatform4TestCase() {

    @Test
    fun `single repository is unique top-level repository`() {
        val repository = repository("project", "/project")

        assertSame(repository, GitWorktreesOperationsService.uniqueTopLevelRepository(listOf(repository)))
    }

    @Test
    fun `nested repositories resolve to parent repository`() {
        val parent = repository("project", "/project")
        val child = repository("module", "/project/module")

        assertSame(parent, GitWorktreesOperationsService.uniqueTopLevelRepository(listOf(parent, child)))
    }

    @Test
    fun `sibling repositories do not have a unique top-level repository`() {
        val first = repository("one", "/project/one")
        val second = repository("two", "/project/two")

        assertNull(GitWorktreesOperationsService.uniqueTopLevelRepository(listOf(first, second)))
    }

    @Test
    fun `worktrees are sorted with main first then by name`() {
        val worktrees = GitWorktreesOperationsService.parseWorktrees(
            repository("project", "/project"),
            listOf(
                "worktree /project",
                "branch refs/heads/master",
                "",
                "worktree /project/zebra-tree",
                "branch refs/heads/zebra",
                "",
                "worktree /project/alpha-tree",
                "branch refs/heads/alpha",
            ),
        )

        assertEquals(listOf("project", "alpha-tree", "zebra-tree"), worktrees.map { it.name })
        assertTrue(worktrees.first().isMain)
        assertTrue(worktrees.first().isCurrent)
    }

    private fun repository(name: String, path: String): GitRepository {
        return gitRepositoryProxy(project, TestVirtualFile(name, path), currentBranchName = "master")
    }

    private fun gitRepositoryProxy(
        project: Project,
        root: VirtualFile,
        currentBranchName: String?,
    ): GitRepository {
        val handler = InvocationHandler { proxy, method, args ->
            when (method.name) {
                "getProject" -> project
                "getRoot" -> root
                "getPresentableUrl" -> root.path
                "getCurrentBranchName" -> currentBranchName
                "isFresh" -> false
                "isDisposed" -> false
                "update" -> Unit
                "dispose" -> Unit
                "toLogString" -> root.path
                "toString" -> "GitRepository(${root.path})"
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
        private val fileName: String,
        private val filePath: String,
    ) : VirtualFile() {
        override fun getName(): String = fileName
        override fun getFileSystem() = LocalFileSystem.getInstance()
        override fun getPath(): String = filePath
        override fun isWritable(): Boolean = true
        override fun isDirectory(): Boolean = true
        override fun isValid(): Boolean = true
        override fun getParent(): VirtualFile? {
            val parentPath = filePath.trimEnd('/').substringBeforeLast('/', missingDelimiterValue = "")
            if (parentPath.isBlank()) return null
            return TestVirtualFile(parentPath.substringAfterLast('/'), parentPath)
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
