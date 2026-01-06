package ru.rzn.gmyasoedov.gmaven.project.action

import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.ModuleUtil
import com.intellij.psi.PsiElement
import com.intellij.util.execution.ParametersListUtil
import org.jetbrains.uast.*
import ru.rzn.gmyasoedov.gmaven.GMavenConstants.SYSTEM_ID
import ru.rzn.gmyasoedov.gmaven.bundle.GBundle
import ru.rzn.gmyasoedov.gmaven.settings.debug.MavenDebugType
import ru.rzn.gmyasoedov.gmaven.util.MvnUtil

class DebugTestAction() : AnAction() {
    val executorId = DefaultDebugExecutor.EXECUTOR_ID

    init {
        templatePresentation.icon = AllIcons.Actions.StartDebugger
        templatePresentation.text = GBundle.message("gmaven.action.debug.test")
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        updateAction(e, executorId)
    }

    override fun actionPerformed(e: AnActionEvent) = actionPerformed(e, executorId)
}

class RunTestAction : AnAction() {
    val executorId = DefaultRunExecutor.EXECUTOR_ID

    init {
        templatePresentation.icon = AllIcons.Actions.Execute
        templatePresentation.text = GBundle.message("gmaven.action.run.test")
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        updateAction(e, executorId)
    }

    override fun actionPerformed(e: AnActionEvent) = actionPerformed(e, executorId)
}

private fun updateAction(e: AnActionEvent, executorId: String) {
    val message = if (executorId == DefaultRunExecutor.EXECUTOR_ID)
        GBundle.message("gmaven.action.run.test") else GBundle.message("gmaven.action.debug.test")
    e.presentation.text = message
    val psiElement = e.getData(CommonDataKeys.PSI_ELEMENT)
    val uElement = psiElement?.toUElement()
    if ((uElement is UMethod && !uElement.isStatic && uElement.name != "main") || uElement is UClass) {
        val underProject = MvnUtil.isUnderProject(psiElement)
        val testFile = MvnUtil.isTestFile(psiElement)
        e.presentation.isEnabledAndVisible = underProject && testFile
        if (testFile) {
            val name = getName(uElement)
            e.presentation.text = "$message '$name'"
        }
        return
    }
    e.presentation.isEnabledAndVisible = false
}

private fun getName(uElement: UElement): String {
    return if (uElement is UClass) {
        uElement.javaPsi.name ?: ""
    } else if (uElement is UMethod) {
        uElement.name
    } else {
        ""
    }
}

private fun actionPerformed(e: AnActionEvent, executorId: String) {
    val element = e.getData(CommonDataKeys.PSI_ELEMENT) ?: return
    actionPerformedTest(element, executorId)
}

fun actionPerformedTest(element: PsiElement, executorId: String) {
    val module = ModuleUtil.findModuleForPsiElement(element) ?: return
    val mavenModule = MvnUtil.findMavenModuleData(module) ?: return

    val testParam = getTestParam(element) ?: return

    val settings = ExternalSystemTaskExecutionSettings()
    settings.scriptParameters = ParametersListUtil.escape("-Dtest=$testParam")
    settings.scriptParameters += " -Dsurefire.failIfNoSpecifiedTests=false"
    if (executorId == DefaultDebugExecutor.EXECUTOR_ID) {
        MvnUtil.setRemoteDebugJvmParam(MavenDebugType.TEST, settings)
    }
    settings.externalProjectPath = mavenModule.linkedExternalProjectPath
    settings.taskNames = listOf("test")
    settings.externalSystemIdString = SYSTEM_ID.id

    ExternalSystemUtil.runTask(settings, executorId, module.project, SYSTEM_ID)
}


private fun getTestParam(psiElement: PsiElement): String? {
    return when (val uElement = psiElement.toUElement()) {
        is UClass -> uElement.qualifiedName
        is UMethod -> {
            val uClass = uElement.getContainingUClass()
            uClass?.qualifiedName?.let { "$it#${uElement.name}" }
        }

        else -> null
    }
}