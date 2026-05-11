package dev.dengchao.idea.plugin.git.worktrees.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.util.ui.FormBuilder
import dev.dengchao.idea.plugin.git.worktrees.Gw4iBundle
import java.awt.event.ItemEvent
import javax.swing.DefaultListCellRenderer
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel

class GitWorktreesProjectConfigurable(
    private val project: Project,
) : SearchableConfigurable {
    companion object {
        const val ID: String = "git.worktrees.settings"
    }

    internal enum class SettingsTarget {
        GLOBAL,
        PROJECT,
    }

    private var panel: JPanel? = null
    private val targetComboBox = JComboBox(SettingsTarget.entries.toTypedArray())
    private val useProjectSettingsCheckBox = JCheckBox(Gw4iBundle.message("settings.GitWorktrees.project.override"))
    private val showRelativeLocationsCheckBox = JCheckBox(Gw4iBundle.message("settings.GitWorktrees.show.relative.locations"))
    private val rememberGitWindowTabCheckBox = JCheckBox(Gw4iBundle.message("settings.GitWorktrees.remember.git.window.tab"))

    private var globalShowRelativeLocations: Boolean = true
    private var globalRememberGitWindowTab: Boolean = true
    private var projectUseProjectSettings: Boolean = false
    private var projectShowRelativeLocations: Boolean = true
    private var projectRememberGitWindowTab: Boolean = true

    override fun getId(): String = ID

    override fun getDisplayName(): String = Gw4iBundle.message("settings.GitWorktrees.display.name")

    override fun createComponent(): JComponent {
        if (panel != null) return panel as JPanel

        targetComboBox.renderer = SettingsTargetListCellRenderer()
        targetComboBox.addItemListener {
            if (it.stateChange == ItemEvent.SELECTED) {
                updateControlsFromCurrentTarget()
            }
        }
        useProjectSettingsCheckBox.addItemListener {
            projectUseProjectSettings = useProjectSettingsCheckBox.isSelected
            updateEnabledState()
        }
        showRelativeLocationsCheckBox.addItemListener {
            if (targetComboBox.selectedItem == SettingsTarget.GLOBAL) {
                globalShowRelativeLocations = showRelativeLocationsCheckBox.isSelected
            } else {
                projectShowRelativeLocations = showRelativeLocationsCheckBox.isSelected
            }
        }
        rememberGitWindowTabCheckBox.addItemListener {
            if (targetComboBox.selectedItem == SettingsTarget.GLOBAL) {
                globalRememberGitWindowTab = rememberGitWindowTabCheckBox.isSelected
            } else {
                projectRememberGitWindowTab = rememberGitWindowTabCheckBox.isSelected
            }
        }

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(Gw4iBundle.message("settings.GitWorktrees.target.label"), targetComboBox, true)
            .addComponent(useProjectSettingsCheckBox)
            .addComponent(showRelativeLocationsCheckBox)
            .addComponent(rememberGitWindowTabCheckBox)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        return panel as JPanel
    }

    override fun isModified(): Boolean {
        val global = GitWorktreesGlobalSettings.getInstance().state
        val projectState = GitWorktreesProjectSettings.getInstance(project).state
        return global.showRelativeLocations != globalShowRelativeLocations ||
            global.rememberGitWindowTab != globalRememberGitWindowTab ||
            projectState.useProjectSettings != projectUseProjectSettings ||
            projectState.showRelativeLocations != projectShowRelativeLocations ||
            projectState.rememberGitWindowTab != projectRememberGitWindowTab
    }

    override fun apply() {
        GitWorktreesGlobalSettings.getInstance().loadState(
            GitWorktreesGlobalSettings.State(
                showRelativeLocations = globalShowRelativeLocations,
                rememberGitWindowTab = globalRememberGitWindowTab,
            ),
        )
        val settings = GitWorktreesProjectSettings.getInstance(project)
        val restoreGitWindowTab = settings.state.restoreGitWindowTab
        settings.loadState(
            GitWorktreesProjectSettings.State(
                useProjectSettings = projectUseProjectSettings,
                showRelativeLocations = projectShowRelativeLocations,
                rememberGitWindowTab = projectRememberGitWindowTab,
                restoreGitWindowTab = restoreGitWindowTab,
            ),
        )
    }

    override fun reset() {
        val global = GitWorktreesGlobalSettings.getInstance().state
        val projectState = GitWorktreesProjectSettings.getInstance(project).state
        globalShowRelativeLocations = global.showRelativeLocations
        globalRememberGitWindowTab = global.rememberGitWindowTab
        projectUseProjectSettings = projectState.useProjectSettings
        projectShowRelativeLocations = projectState.showRelativeLocations
        projectRememberGitWindowTab = projectState.rememberGitWindowTab

        targetComboBox.selectedItem = SettingsTarget.PROJECT
        updateControlsFromCurrentTarget()
    }

    override fun disposeUIResources() {
        panel = null
    }

    internal fun setTargetForTests(target: SettingsTarget) {
        targetComboBox.selectedItem = target
        updateControlsFromCurrentTarget()
    }

    internal fun setUseProjectSettingsForTests(value: Boolean) {
        useProjectSettingsCheckBox.isSelected = value
    }

    internal fun setShowRelativeLocationsForTests(value: Boolean) {
        showRelativeLocationsCheckBox.isSelected = value
    }

    internal fun setRememberGitWindowTabForTests(value: Boolean) {
        rememberGitWindowTabCheckBox.isSelected = value
    }

    private fun updateControlsFromCurrentTarget() {
        val isProjectTarget = targetComboBox.selectedItem == SettingsTarget.PROJECT
        useProjectSettingsCheckBox.isSelected = projectUseProjectSettings
        useProjectSettingsCheckBox.isEnabled = isProjectTarget
        showRelativeLocationsCheckBox.isSelected = if (isProjectTarget) projectShowRelativeLocations else globalShowRelativeLocations
        rememberGitWindowTabCheckBox.isSelected = if (isProjectTarget) projectRememberGitWindowTab else globalRememberGitWindowTab
        updateEnabledState()
    }

    private fun updateEnabledState() {
        val isProjectTarget = targetComboBox.selectedItem == SettingsTarget.PROJECT
        val enableSettingCheckboxes = !isProjectTarget || useProjectSettingsCheckBox.isSelected
        showRelativeLocationsCheckBox.isEnabled = enableSettingCheckboxes
        rememberGitWindowTabCheckBox.isEnabled = enableSettingCheckboxes
    }

    private class SettingsTargetListCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): java.awt.Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            text = when (value as? SettingsTarget) {
                SettingsTarget.GLOBAL -> Gw4iBundle.message("settings.GitWorktrees.target.global")
                SettingsTarget.PROJECT -> Gw4iBundle.message("settings.GitWorktrees.target.project")
                null -> ""
            }
            return component
        }
    }
}
