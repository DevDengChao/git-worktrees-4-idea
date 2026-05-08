// Copyright 2000-2026 JetBrains s.r.o. and contributors.
package dev.dengchao.idea.plugin.git.worktrees

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionWrapper
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshotProvider
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.testFramework.TestActionEvent
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogCommitSelection
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsRefImpl
import dev.dengchao.idea.plugin.git.worktrees.actions.GitLogWorktreeActionReplacement
import dev.dengchao.idea.plugin.git.worktrees.actions.GitLogWorktreeActionInstaller
import dev.dengchao.idea.plugin.git.worktrees.actions.GitWorktreeBranchActionGroup
import dev.dengchao.idea.plugin.git.worktrees.actions.GitLogWorktreeBranchOperationGroup
import dev.dengchao.idea.plugin.git.worktrees.actions.GitLogWorktreeCheckoutGroup
import dev.dengchao.idea.plugin.git.worktrees.model.WorktreeInfo
import dev.dengchao.idea.plugin.git.worktrees.services.DeleteWorktreeBranchDecision
import dev.dengchao.idea.plugin.git.worktrees.services.GitWorktreesOperationsService
import git4idea.GitLocalBranch
import git4idea.commands.GitCommandResult
import git4idea.log.GitRefManager
import git4idea.repo.GitRepository
import java.io.File
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer
import org.junit.Test

class GitLogWorktreeActionsTest : LightPlatform4TestCase() {

    @Test
    fun `test installer replaces Git Log action groups with worktree-aware wrappers`() {
        val actionManager = ActionManager.getInstance()
        val originalCheckout = actionManager.getAction("Git.CheckoutGroup")
        val originalBranchOperations = actionManager.getAction("Git.BranchOperationGroup")
        val originalBranch = actionManager.getAction("Git.Branch")

        GitLogWorktreeActionReplacement.resetForTests()
        GitLogWorktreeActionReplacement.installForTests(actionManager)

        assertTrue(actionManager.getAction("Git.CheckoutGroup") is GitLogWorktreeCheckoutGroup)
        assertTrue(actionManager.getAction("Git.BranchOperationGroup") is GitLogWorktreeBranchOperationGroup)
        assertTrue(actionManager.getAction("Git.Branch") is GitWorktreeBranchActionGroup)
        assertTrue(
            actionManager.getAction("Git.Log.ContextMenu.CheckoutBrowse")
                .findChildByClassName("dev.dengchao.idea.plugin.git.worktrees.actions.GitLogWorktreeCheckoutGroup"),
        )
        assertTrue(
            actionManager.getAction("Git.Log.ContextMenu")
                .findChildByClassName("dev.dengchao.idea.plugin.git.worktrees.actions.GitLogWorktreeBranchOperationGroup"),
        )

        actionManager.replaceAction("Git.CheckoutGroup", originalCheckout)
        actionManager.replaceAction("Git.BranchOperationGroup", originalBranchOperations)
        actionManager.replaceAction("Git.Branch", originalBranch)
    }

    @Test
    fun `test action installer only overrides public app lifecycle callback`() {
        val declaredMethodNames = GitLogWorktreeActionInstaller::class.java.declaredMethods.map { it.name }.toSet()

        assertTrue("Installer should keep the public startup callback", "appFrameCreated" in declaredMethodNames)
        assertFalse("Installer should not override internal AppLifecycleListener.appStarted", "appStarted" in declaredMethodNames)
    }

    @Test
    fun `test Git Log checkout branch action confirms and checks out linked worktree branch`() {
        val events = mutableListOf<String>()
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master") {
            events += "refresh"
        }
        val worktree = WorktreeInfo(
            path = "/project/feature-tree",
            branchName = "feature",
            isMain = false,
            isCurrent = false,
            isLocked = false,
            isPrunable = false,
        )
        configureService(repository, worktree, events)

        val group = GitLogWorktreeCheckoutGroup()
        val children = group.getChildren(logEvent(repository, "feature"))

        assertEquals(1, children.size)
        val checkoutGroup = children.single() as ActionGroup
        val checkoutAction = checkoutGroup.getChildren(TestActionEvent()).single { it.templatePresentation.text == "feature" }

        checkoutAction.actionPerformed(TestActionEvent())

        assertEquals(
            listOf(
                "confirm checkout feature /project/feature-tree",
                "task ${Gw4iBundle.message("GitWorktrees.task.checkout.branch.title")}",
                "checkout feature force=false",
                "refresh",
            ),
            events,
        )
    }

    @Test
    fun `test Git Log checkout resolves linked worktree branch from git metadata without listing worktrees`() {
        val events = mutableListOf<String>()
        val root = createRepositoryMetadataWithWorktree("master", "feat/admin-reward-task-query", "admin-reward-task-query")
        val repository = gitRepository(rootPath = root.toString().replace(File.separatorChar, '/'), currentBranchName = "master") {
            events += "refresh"
        }
        configureService(repository, worktree = null, events)

        val children = GitLogWorktreeCheckoutGroup()
            .getChildren(logEvent(repository, "feat/admin-reward-task-query"))
        val checkoutAction = findAction(children, "feat/admin-reward-task-query")

        checkoutAction.actionPerformed(TestActionEvent())

        assertEquals(
            listOf(
                "confirm checkout feat/admin-reward-task-query ${root.resolve(".worktrees/admin-reward-task-query").toString().replace(File.separatorChar, '/')}",
                "task ${Gw4iBundle.message("GitWorktrees.task.checkout.branch.title")}",
                "checkout feat/admin-reward-task-query force=false",
                "refresh",
            ),
            events,
        )
    }

    @Test
    fun `test Git branch popup replaces native checkout and delete for linked worktree branch`() {
        val events = mutableListOf<String>()
        val branchName = "feat/admin-reward-task-query"
        val root = createRepositoryMetadataWithWorktree("master", branchName, "admin-reward-task-query")
        val worktreePath = root.resolve(".worktrees/admin-reward-task-query").toString().replace(File.separatorChar, '/')
        val repository = gitRepository(rootPath = root.toString().replace(File.separatorChar, '/'), currentBranchName = "master") {
            events += "refresh"
        }
        configureService(repository, worktree = null, events)

        val nativeCheckout = git4idea.actions.ref.GitCheckoutAction()
        val nativeDelete = git4idea.actions.ref.GitDeleteRefAction()
        val nativeGroup = TestActionGroup(
            "Branch",
            listOf(
                nativeCheckout,
                GitCheckoutAsNewBranchLikeAction(),
                nativeDelete,
            ),
        )
        val children = GitWorktreeBranchActionGroup(nativeGroup).getChildren(TestActionEvent())

        assertEquals(3, children.size)
        assertNotSame(nativeCheckout, children[0])
        assertSame(nativeGroup.getChildren(TestActionEvent())[1], children[1])
        assertNotSame(nativeDelete, children[2])

        val checkoutAction = wrapForBranch(children[0], repository, branchName)
        val deleteAction = wrapForBranch(children[2], repository, branchName)
        assertTrue(checkoutAction is DataSnapshotProvider)
        assertTrue(deleteAction is DataSnapshotProvider)

        checkoutAction.actionPerformed(branchPopupEvent(checkoutAction))
        deleteAction.actionPerformed(branchPopupEvent(deleteAction))

        assertEquals(
            listOf(
                "confirm checkout $branchName $worktreePath",
                "task ${Gw4iBundle.message("GitWorktrees.task.checkout.branch.title")}",
                "checkout $branchName force=false",
                "refresh",
                "decision $branchName $worktreePath",
                "task ${Gw4iBundle.message("GitWorktrees.task.remove.worktree.title")}",
                "remove $worktreePath",
                "delete $branchName force=true",
                "refresh",
            ),
            events,
        )
    }

    @Test
    fun `test Git branch popup delegates native checkout and delete for ordinary branches`() {
        val nativeEvents = mutableListOf<String>()
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        configureService(repository, worktree = null, events = mutableListOf())

        val nativeCheckout = NativeGitCheckoutAction(nativeEvents)
        val nativeDelete = NativeGitDeleteRefAction(nativeEvents)
        val nativeGroup = TestActionGroup("Branch", listOf(nativeCheckout, nativeDelete))
        val children = GitWorktreeBranchActionGroup(nativeGroup).getChildren(TestActionEvent())

        val checkoutAction = wrapForBranch(children[0], repository, "ordinary")
        val deleteAction = wrapForBranch(children[1], repository, "ordinary")
        checkoutAction.actionPerformed(branchPopupEvent(checkoutAction))
        deleteAction.actionPerformed(branchPopupEvent(deleteAction))

        assertEquals(listOf("native checkout", "native delete"), nativeEvents)
    }

    @Test
    fun `test Git branch popup wrapper does not use service fallback without git metadata`() {
        val nativeEvents = mutableListOf<String>()
        val events = mutableListOf<String>()
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        val branchName = "feat/admin-reward-task-query"
        configureService(
            repository,
            WorktreeInfo(
                path = "/project/admin-reward-task-query",
                branchName = branchName,
                isMain = false,
                isCurrent = false,
                isLocked = false,
                isPrunable = false,
            ),
            events,
        )

        val nativeCheckout = NativeGitCheckoutAction(nativeEvents)
        val nativeDelete = NativeGitDeleteRefAction(nativeEvents)
        val nativeGroup = TestActionGroup("Branch", listOf(nativeCheckout, nativeDelete))
        val children = GitWorktreeBranchActionGroup(nativeGroup).getChildren(TestActionEvent())

        val checkoutAction = wrapForBranch(children[0], repository, branchName)
        val deleteAction = wrapForBranch(children[1], repository, branchName)
        val checkoutEvent = branchPopupEvent(checkoutAction)
        val deleteEvent = branchPopupEvent(deleteAction)

        checkoutAction.update(checkoutEvent)
        deleteAction.update(deleteEvent)
        checkoutAction.actionPerformed(checkoutEvent)
        deleteAction.actionPerformed(deleteEvent)

        assertEquals(
            "Branch popup wrappers must not resolve linked worktrees through GitWorktreesOperationsService during runtime updates.",
            emptyList<String>(),
            events,
        )
        assertEquals(listOf("native checkout", "native delete"), nativeEvents)
    }

    @Test
    fun `test Git Log checkout matches slash branch by native action field when text is shortened`() {
        val events = mutableListOf<String>()
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master") {
            events += "refresh"
        }
        val branchName = "feat/admin-reward-task-query"
        val worktree = WorktreeInfo(
            path = "/project/admin-reward-task-query",
            branchName = branchName,
            isMain = false,
            isCurrent = false,
            isLocked = false,
            isPrunable = false,
        )
        configureService(repository, worktree, events)

        val nativeBranchAction = NativeCheckoutLikeAction(repository, branchName, "admin-reward-task-query")
        val nativeGroup = TestActionGroup("Checkout", listOf(nativeBranchAction))
        val children = GitLogWorktreeCheckoutGroup(nativeGroup)
            .getChildren(logEvent(repository, branchName))

        val checkoutAction = findAction(children, branchName)
        checkoutAction.actionPerformed(TestActionEvent())

        assertEquals(
            listOf(
                "confirm checkout $branchName /project/admin-reward-task-query",
                "task ${Gw4iBundle.message("GitWorktrees.task.checkout.branch.title")}",
                "checkout $branchName force=false",
                "refresh",
            ),
            events,
        )
    }

    @Test
    fun `test Git Log branch delete action asks worktree deletion decision`() {
        val events = mutableListOf<String>()
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        val worktree = WorktreeInfo(
            path = "/project/feature-tree",
            branchName = "feature",
            isMain = false,
            isCurrent = false,
            isLocked = false,
            isPrunable = false,
        )
        configureService(repository, worktree, events)

        val group = GitLogWorktreeBranchOperationGroup()
        val branchGroup = group.getChildren(logEvent(repository, "feature"))
            .filterIsInstance<ActionGroup>()
            .single { it.templatePresentation.text?.contains("feature") == true }
        val deleteAction = findAction(branchGroup, "Delete Branch / Worktree")

        deleteAction.actionPerformed(TestActionEvent())

        assertEquals(
            listOf(
                "decision feature /project/feature-tree",
                "task ${Gw4iBundle.message("GitWorktrees.task.remove.worktree.title")}",
                "remove /project/feature-tree",
                "delete feature force=true",
            ),
            events,
        )
    }

    @Test
    fun `test Git Log branch delete uses wrapped selected ref data for slash branch`() {
        val events = mutableListOf<String>()
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        val branchName = "feat/admin-reward-task-query"
        val worktree = WorktreeInfo(
            path = "/project/admin-reward-task-query",
            branchName = branchName,
            isMain = false,
            isCurrent = false,
            isLocked = false,
            isPrunable = false,
        )
        configureService(repository, worktree, events)

        val nativeBranchGroup = TestActionGroup(
            text = "admin-reward-task-query",
            children = listOf(
                GitDeleteRefLikeDataSnapshotAction(
                    text = "Delete",
                    branchName = branchName,
                    repository = repository,
                ),
            ),
        )
        val nativeGroup = TestActionGroup("Branches", listOf(nativeBranchGroup))
        val branchGroup = GitLogWorktreeBranchOperationGroup(nativeGroup)
            .getChildren(logEvent(repository, branchName))
            .filterIsInstance<ActionGroup>()
            .single()
        val deleteAction = findAction(branchGroup, "Delete Branch / Worktree")

        deleteAction.actionPerformed(TestActionEvent())

        assertEquals(
            listOf(
                "decision $branchName /project/admin-reward-task-query",
                "task ${Gw4iBundle.message("GitWorktrees.task.remove.worktree.title")}",
                "remove /project/admin-reward-task-query",
                "delete $branchName force=true",
            ),
            events,
        )
    }

    @Test
    fun `test Git Log worktree groups leave unlinked branches to original actions`() {
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        configureService(repository, worktree = null, events = mutableListOf())

        val checkoutChildren = GitLogWorktreeCheckoutGroup()
            .getChildren(logEvent(repository, "feature"))
        val branchChildren = GitLogWorktreeBranchOperationGroup()
            .getChildren(logEvent(repository, "feature"))

        assertEquals("GitCheckoutActionGroup should still expose the native checkout branch item", 1, checkoutChildren.size)
        assertTrue(
            "Branch operation group should not synthesize worktree delete fallback for unlinked branches",
            branchChildren.none { it.templatePresentation.text?.contains("Delete Branch / Worktree") == true },
        )
    }

    @Test
    fun `test Git Log worktree groups do not intercept current branch`() {
        val events = mutableListOf<String>()
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "feature")
        val worktree = WorktreeInfo(
            path = "/project/feature-tree",
            branchName = "feature",
            isMain = false,
            isCurrent = false,
            isLocked = false,
            isPrunable = false,
        )
        configureService(repository, worktree, events)

        val checkoutChildren = GitLogWorktreeCheckoutGroup()
            .getChildren(logEvent(repository, "feature"))
        val branchChildren = GitLogWorktreeBranchOperationGroup()
            .getChildren(logEvent(repository, "feature"))

        assertFalse(findActionTexts(checkoutChildren).contains("feature"))
        assertTrue(
            "Current branch should not synthesize worktree delete fallback",
            branchChildren.none { it.templatePresentation.text?.contains("Delete Branch / Worktree") == true },
        )
    }

    @Test
    fun `test Git Log worktree groups do not intercept remote branches or tags`() {
        val events = mutableListOf<String>()
        val repository = gitRepository(rootPath = "/project/root", currentBranchName = "master")
        val worktree = WorktreeInfo(
            path = "/project/feature-tree",
            branchName = "feature",
            isMain = false,
            isCurrent = false,
            isLocked = false,
            isPrunable = false,
        )
        configureService(repository, worktree, events)

        val commitId = commitId(repository)
        val event = logEvent(
            repository,
            listOf(
                VcsRefImpl(commitId.hash, "origin/feature", GitRefManager.REMOTE_BRANCH, repository.root),
                VcsRefImpl(commitId.hash, "feature", GitRefManager.TAG, repository.root),
            ),
        )

        val checkoutChildren = GitLogWorktreeCheckoutGroup().getChildren(event)
        val branchChildren = GitLogWorktreeBranchOperationGroup().getChildren(event)

        assertFalse(findActionTexts(checkoutChildren).contains("feature"))
        assertTrue(
            "Remote branches and tags should not synthesize worktree delete fallback",
            branchChildren.none { it.templatePresentation.text?.contains("Delete Branch / Worktree") == true },
        )
    }

    private fun configureService(
        repository: GitRepository,
        worktree: WorktreeInfo?,
        events: MutableList<String>,
    ) {
        val service = GitWorktreesOperationsService.getInstance(project)
        service.overrideProvidersForTests(
            repositoriesProvider = { listOf(repository) },
            worktreesProvider = { if (worktree == null) emptyList() else listOf(worktree) },
            parentDisposable = testRootDisposable,
        )
        service.overrideGitOperationsForTests(
            checkoutRunner = { _, branch, force ->
                events += "checkout $branch force=$force"
                GitWorktreesOperationsService.CheckoutResult(
                    GitCommandResult(false, 0, emptyList(), emptyList()),
                    hasConflictingChanges = false,
                )
            },
            removeWorktreeRunner = { _, path ->
                events += "remove $path"
                GitCommandResult(false, 0, emptyList(), emptyList())
            },
            deleteBranchRunner = { _, branch, force ->
                events += "delete $branch force=$force"
                GitCommandResult(false, 0, emptyList(), emptyList())
            },
            parentDisposable = testRootDisposable,
        )
        service.overrideTaskRunnersForTests(
            backgroundTaskRunner = { title, runTask, onFinished ->
                events += "task $title"
                runTask()
                onFinished()
            },
            parentDisposable = testRootDisposable,
        )
        service.overrideBranchDeletionDialogForTests(
            dialogProvider = { branch, path ->
                events += "decision $branch $path"
                DeleteWorktreeBranchDecision.DELETE_WORKTREE_AND_BRANCH
            },
            parentDisposable = testRootDisposable,
        )
        service.overrideCheckoutUsedByWorktreeDialogForTests(
            dialogProvider = { branch, path ->
                events += "confirm checkout $branch $path"
                true
            },
            parentDisposable = testRootDisposable,
        )
    }

    private fun logEvent(repository: GitRepository, branchName: String): com.intellij.openapi.actionSystem.AnActionEvent {
        val commitId = commitId(repository)
        val refs = listOf(localBranchRef(repository, branchName, commitId.hash))
        return logEvent(repository, refs)
    }

    private fun logEvent(repository: GitRepository, refs: List<VcsRef>): com.intellij.openapi.actionSystem.AnActionEvent {
        val commitId = commitId(repository)
        return TestActionEvent.createTestEvent(CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
            sink[PlatformDataKeys.PROJECT] = project
            sink[VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION] = SingleCommitSelection(commitId)
            sink[VcsLogDataKeys.VCS_LOG_REFS] = refs
        })
    }

    private fun branchPopupEvent(repository: GitRepository, branchName: String): com.intellij.openapi.actionSystem.AnActionEvent {
        return TestActionEvent.createTestEvent(CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
            sink[PlatformDataKeys.PROJECT] = project
            sink[dev.dengchao.idea.plugin.git.worktrees.actions.GitBranchActionDataKeys.SELECTED_REF] =
                GitLocalBranch(branchName)
            sink[dev.dengchao.idea.plugin.git.worktrees.actions.GitBranchActionDataKeys.SELECTED_REPOSITORY] =
                repository
            sink[dev.dengchao.idea.plugin.git.worktrees.actions.GitBranchActionDataKeys.AFFECTED_REPOSITORIES] =
                listOf(repository)
        })
    }

    private fun branchPopupEvent(action: AnAction): com.intellij.openapi.actionSystem.AnActionEvent {
        val provider = action as DataSnapshotProvider
        val projectContext = CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
            sink[PlatformDataKeys.PROJECT] = project
        }
        return TestActionEvent.createTestEvent(CustomizedDataContext.withSnapshot(projectContext, provider))
    }

    private fun wrapForBranch(action: AnAction, repository: GitRepository, branchName: String): AnAction {
        val wrapperClass = Class.forName("git4idea.ui.branch.GitBranchActionWrapper")
        return wrapperClass
            .getMethod(
                "tryWrap",
                AnAction::class.java,
                git4idea.GitBranch::class.java,
                GitRepository::class.java,
                List::class.java,
            )
            .invoke(null, action, GitLocalBranch(branchName), repository, listOf(repository)) as AnAction
    }

    private fun commitId(repository: GitRepository): CommitId {
        return CommitId(HashImpl.build("0123456789abcdef0123456789abcdef01234567"), repository.root)
    }

    private fun localBranchRef(repository: GitRepository, branchName: String, hash: Hash): VcsRef {
        return VcsRefImpl(hash, branchName, GitRefManager.LOCAL_BRANCH, repository.root)
    }

    private fun createRepositoryMetadataWithWorktree(
        mainBranchName: String,
        branchName: String,
        worktreeName: String,
    ): Path {
        val root = Files.createTempDirectory("gw4i-log-actions")
        val gitDir = root.resolve(".git")
        val worktreePath = root.resolve(".worktrees").resolve(worktreeName)
        val worktreeMetadata = gitDir.resolve("worktrees").resolve(worktreeName)
        Files.createDirectories(worktreePath)
        Files.createDirectories(worktreeMetadata)
        Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/$mainBranchName\n")
        Files.writeString(worktreeMetadata.resolve("HEAD"), "ref: refs/heads/$branchName\n")
        Files.writeString(worktreeMetadata.resolve("gitdir"), worktreePath.resolve(".git").toString().replace('\\', '/') + "\n")
        Files.writeString(worktreeMetadata.resolve("commondir"), "../..\n")
        return root
    }

    private fun findAction(group: ActionGroup, text: String): AnAction {
        group.getChildren(null).forEach { child ->
            if (child.templatePresentation.text == text) return child
            if (child is ActionGroup) {
                runCatching { return findAction(child, text) }
            }
        }
        error("Action '$text' was not found")
    }

    private fun findAction(actions: Array<AnAction>, text: String): AnAction {
        actions.forEach { action ->
            if (action.templatePresentation.text == text) return action
            if (action is ActionGroup) {
                runCatching { return findAction(action, text) }
            }
        }
        error("Action '$text' was not found")
    }

    private fun findActionTexts(actions: Array<AnAction>): Set<String> {
        val texts = mutableSetOf<String>()
        actions.forEach { collectActionTexts(it, texts) }
        return texts
    }

    private fun collectActionTexts(action: AnAction, texts: MutableSet<String>) {
        action.templatePresentation.text?.let(texts::add)
        if (action is ActionGroup) {
            action.getChildren(null).forEach { collectActionTexts(it, texts) }
        }
    }

    private fun AnAction?.findChildByClassName(className: String): Boolean {
        if (this == null) return false
        if (javaClass.name == className) return true
        if (this is ActionGroup) {
            return getChildren(null).any { it.findChildByClassName(className) }
        }
        return false
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

    private class SingleCommitSelection(private val commitId: CommitId) : VcsLogCommitSelection {
        override val rows: IntArray = intArrayOf(0)
        override val ids: List<Int> = listOf(0)
        override val commits: List<CommitId> = listOf(commitId)
        override val cachedMetadata: List<VcsCommitMetadata> = emptyList()
        override val cachedFullDetails: List<VcsFullCommitDetails> = emptyList()
        override fun requestFullDetails(consumer: Consumer<in List<VcsFullCommitDetails>>) = consumer.accept(emptyList())
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

    private class NativeCheckoutLikeAction(
        @Suppress("unused")
        private val repository: GitRepository,
        @Suppress("unused")
        private val hashOrRefName: String,
        text: String,
    ) : DumbAwareAction(text) {
        override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) = Unit
    }

    private class TestActionGroup(
        text: String,
        private val children: List<AnAction>,
    ) : ActionGroup(text, true) {
        override fun getChildren(e: com.intellij.openapi.actionSystem.AnActionEvent?): Array<AnAction> =
            children.toTypedArray()
    }

    private class GitDeleteRefLikeDataSnapshotAction(
        text: String,
        private val branchName: String,
        private val repository: GitRepository,
    ) : AnActionWrapper(git4idea.actions.ref.GitDeleteRefAction()), DataSnapshotProvider {
        init {
            templatePresentation.text = text
        }

        override fun dataSnapshot(sink: DataSink) {
            sink[dev.dengchao.idea.plugin.git.worktrees.actions.GitBranchActionDataKeys.SELECTED_REF] =
                GitLocalBranch(branchName)
            sink[dev.dengchao.idea.plugin.git.worktrees.actions.GitBranchActionDataKeys.SELECTED_REPOSITORY] =
                repository
            sink[dev.dengchao.idea.plugin.git.worktrees.actions.GitBranchActionDataKeys.AFFECTED_REPOSITORIES] =
                listOf(repository)
        }

        override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) = Unit
    }

    private class GitCheckoutAsNewBranchLikeAction : DumbAwareAction("Checkout as New Branch") {
        override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) = Unit
    }

    private class NativeGitCheckoutAction(
        private val events: MutableList<String>,
    ) : AnActionWrapper(git4idea.actions.ref.GitCheckoutAction()) {
        override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
            events += "native checkout"
        }
    }

    private class NativeGitDeleteRefAction(
        private val events: MutableList<String>,
    ) : AnActionWrapper(git4idea.actions.ref.GitDeleteRefAction()) {
        override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
            events += "native delete"
        }
    }
}
