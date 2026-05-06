package dev.dengchao.idea.plugin.git.worktrees.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import dev.dengchao.idea.plugin.git.worktrees.Gw4iBundle
import dev.dengchao.idea.plugin.git.worktrees.ui.GitWorktreesPanel
import dev.dengchao.idea.plugin.git.worktrees.ui.GitWorktreesToolWindowFactory
import javax.swing.JComponent

@Service(Service.Level.PROJECT)
class GitWorktreesContentService(private val project: Project) {

    companion object {
        fun getInstance(project: Project): GitWorktreesContentService {
            return project.getService(GitWorktreesContentService::class.java)
        }
    }

    private var panelFactory: () -> JComponent = { GitWorktreesPanel(project) }

    fun openOrSelectWorktreesTab() {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            openOrSelectWorktreesTabNow()
        } else {
            application.invokeLater {
                if (!project.isDisposed) {
                    openOrSelectWorktreesTabNow()
                }
            }
        }
    }

    fun openFromLegacyToolWindow(toolWindow: ToolWindow) {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            openFromLegacyToolWindowNow(toolWindow)
        } else {
            application.invokeLater {
                if (!project.isDisposed && !toolWindow.isDisposed) {
                    openFromLegacyToolWindowNow(toolWindow)
                }
            }
        }
    }

    internal fun openOrSelectWorktreesTab(contentManager: ContentManager): Content {
        val title = Gw4iBundle.message("toolwindow.GitWorktrees.vcs.tab.title")
        return openOrSelectWorktreesContent(contentManager, title, true)
    }

    internal fun openOrSelectLegacyToolWindowContent(contentManager: ContentManager): Content {
        val title = Gw4iBundle.message("toolwindow.GitWorktrees.title")
        return openOrSelectWorktreesContent(contentManager, title, false)
    }

    private fun openOrSelectWorktreesContent(contentManager: ContentManager, title: String, closeable: Boolean): Content {
        val existing = contentManager.findContent(title)
        if (existing != null) {
            contentManager.setSelectedContent(existing)
            return existing
        }

        val panel = panelFactory()
        val content = ContentFactory.getInstance().createContent(panel, title, false)
        if (panel is Disposable) {
            content.setDisposer(panel)
        }
        content.isCloseable = closeable
        contentManager.addContent(content)
        contentManager.setSelectedContent(content)
        return content
    }

    internal fun overridePanelFactoryForTests(panelFactory: () -> JComponent, parentDisposable: Disposable) {
        this.panelFactory = panelFactory
        Disposer.register(parentDisposable) {
            this.panelFactory = { GitWorktreesPanel(project) }
        }
    }

    private fun openOrSelectWorktreesTabNow() {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val vcsToolWindow = toolWindowManager.getToolWindow(ToolWindowId.VCS)
        if (vcsToolWindow != null) {
            openOrSelectWorktreesTab(vcsToolWindow.contentManager)
            vcsToolWindow.activate(null)
            return
        }

        toolWindowManager.getToolWindow(GitWorktreesToolWindowFactory.TOOLWINDOW_ID)
            ?.activate(null)
    }

    private fun openFromLegacyToolWindowNow(toolWindow: ToolWindow) {
        val vcsToolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS)
        if (vcsToolWindow != null) {
            openOrSelectWorktreesTab(vcsToolWindow.contentManager)
            toolWindow.hide(null)
            vcsToolWindow.activate(null)
            return
        }

        openOrSelectLegacyToolWindowContent(toolWindow.contentManager)
        toolWindow.activate(null)
    }
}
