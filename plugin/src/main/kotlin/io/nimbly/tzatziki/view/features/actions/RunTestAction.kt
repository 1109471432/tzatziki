package io.nimbly.tzatziki.view.features.actions

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import icons.ActionIcons
import io.nimbly.tzatziki.view.features.FeaturePanel
import io.nimbly.tzatziki.view.features.nodes.TzRunnableNode
import javax.swing.tree.DefaultMutableTreeNode

private const val PRESENTATION_TEXT = "Run Cucumber..."

@Suppress("MissingActionUpdateThread")
class RunTestAction(val panel: FeaturePanel) : AnAction() {

    init {
        this.templatePresentation.text = PRESENTATION_TEXT
        this.templatePresentation.icon = ActionIcons.RUN
    }

    override fun actionPerformed(e: AnActionEvent) {

        val component = panel.tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode
            ?: return

        val userObject = component.userObject
        if (userObject !is TzRunnableNode)
            return

        val configurationContext = userObject.getRunDataContext()
        val producer = userObject.getRunConfiguration()
            ?: return

        val configurationFromContext = producer.createConfigurationFromContext(configurationContext)
            ?: return

        val runnerAndConfigurationSettings = configurationFromContext.configurationSettings
        val executor = DefaultRunExecutor.getRunExecutorInstance()

        ExecutionUtil.runConfiguration(runnerAndConfigurationSettings, executor)
    }

    override fun update(e: AnActionEvent) {

        val component: DefaultMutableTreeNode? = panel.tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode
        val userObject: Any? = component?.userObject

        e.presentation.isEnabled = userObject is TzRunnableNode
        e.presentation.text = (userObject as? TzRunnableNode)?.getRunActionText() ?: PRESENTATION_TEXT
    }
}