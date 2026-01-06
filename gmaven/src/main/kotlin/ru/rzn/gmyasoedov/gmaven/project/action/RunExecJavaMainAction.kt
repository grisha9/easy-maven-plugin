package ru.rzn.gmyasoedov.gmaven.project.action

import com.intellij.codeInsight.MetaAnnotationUtil
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
import ru.rzn.gmyasoedov.gmaven.GMavenConstants.MODULE_PROP_BUILD_FILE
import ru.rzn.gmyasoedov.gmaven.GMavenConstants.SYSTEM_ID
import ru.rzn.gmyasoedov.gmaven.bundle.GBundle.message
import ru.rzn.gmyasoedov.gmaven.settings.debug.MavenDebugType
import ru.rzn.gmyasoedov.gmaven.util.CachedModuleDataService
import ru.rzn.gmyasoedov.gmaven.util.MavenPathUtil
import ru.rzn.gmyasoedov.gmaven.util.MvnUtil

const val SPRING_BOOT_APPLICATION = "org.springframework.boot.autoconfigure.SpringBootApplication"

class RunExecJavaMainAction : AnAction() {
    val executorId = DefaultRunExecutor.EXECUTOR_ID

    init {
        templatePresentation.icon = AllIcons.Actions.Execute
        templatePresentation.text = message("gmaven.action.run.exec.java")
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        updateActionJava(e, executorId)
    }

    override fun actionPerformed(e: AnActionEvent) = actionPerformedJava(e, executorId)
}

class DebugExecJavaMainAction : AnAction() {
    val executorId = DefaultDebugExecutor.EXECUTOR_ID

    init {
        templatePresentation.icon = AllIcons.Actions.StartDebugger
        templatePresentation.text = message("gmaven.action.debug.exec.java")
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        updateActionJava(e, executorId)
    }

    override fun actionPerformed(e: AnActionEvent) = actionPerformedJava(e, executorId)
}

class RunSpringMainAction : AnAction() {
    val executorId = DefaultRunExecutor.EXECUTOR_ID

    init {
        templatePresentation.icon = AllIcons.Actions.Execute
        templatePresentation.text = message("gmaven.action.run.spring")
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        updateActionSpring(e, executorId)
    }

    override fun actionPerformed(e: AnActionEvent) = actionPerformedSpring(e, executorId)
}

class DebugSpringMainAction : AnAction() {
    val executorId = DefaultDebugExecutor.EXECUTOR_ID

    init {
        templatePresentation.icon = AllIcons.Actions.StartDebugger
        templatePresentation.text = message("gmaven.action.debug.spring")
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        updateActionSpring(e, executorId)
    }

    override fun actionPerformed(e: AnActionEvent) = actionPerformedSpring(e, executorId)
}

private fun updateActionJava(e: AnActionEvent, executorId: String) {
    val psiElement = e.getData(CommonDataKeys.PSI_ELEMENT)
    val uElement = psiElement?.toUElement()
    if (uElement is UMethod && uElement.name == "main" && !isSpring(uElement)) {
        e.presentation.isEnabledAndVisible = MvnUtil.isUnderProject(psiElement)
        e.presentation.text = if (executorId == DefaultRunExecutor.EXECUTOR_ID)
            message("gmaven.action.run.exec.java") else message("gmaven.action.debug.exec.java")
        return
    }
    e.presentation.isEnabledAndVisible = false
}

private fun updateActionSpring(e: AnActionEvent, executorId: String) {
    val psiElement = e.getData(CommonDataKeys.PSI_ELEMENT)
    val uElement = psiElement?.toUElement()
    if (uElement is UMethod && uElement.name == "main" && isSpring(uElement)) {
        e.presentation.isEnabledAndVisible = MvnUtil.isUnderProject(psiElement)
        e.presentation.text = if (executorId == DefaultRunExecutor.EXECUTOR_ID)
            message("gmaven.action.run.spring") else message("gmaven.action.debug.spring")
        return
    }
    e.presentation.isEnabledAndVisible = false
}

private fun actionPerformedJava(e: AnActionEvent, executorId: String) {
    val element = e.getData(CommonDataKeys.PSI_ELEMENT) ?: return
    val module = ModuleUtil.findModuleForPsiElement(element) ?: return
    CachedModuleDataService.invalidate()
    val mavenModule = MvnUtil.findMavenModuleData(module) ?: return

    val pomPath = mavenModule.getProperty(MODULE_PROP_BUILD_FILE) ?: mavenModule.linkedExternalProjectPath
    val isTest = MvnUtil.isTestFile(element)
    val execClass = element.toUElement()?.getContainingUClass()?.qualifiedName ?: return

    val settings = ExternalSystemTaskExecutionSettings()
    settings.scriptParameters = ParametersListUtil.escape("-Dexec.mainClass=$execClass")
    settings.scriptParameters += " -f"
    settings.scriptParameters += " " + ParametersListUtil.escape(MavenPathUtil.checkOnWsl(pomPath))
    if (isTest) {
        settings.scriptParameters += " -Dexec.classpathScope=test"
    } else {
        settings.scriptParameters += " -DskipTests"
    }
    if (executorId == DefaultDebugExecutor.EXECUTOR_ID) {
        MvnUtil.setRemoteDebugJvmParam(MavenDebugType.EXEC, settings)
    }

    settings.externalProjectPath = mavenModule.linkedExternalProjectPath
    settings.taskNames = listOf("exec:java")
    settings.externalSystemIdString = SYSTEM_ID.id

    ExternalSystemUtil.runTask(settings, executorId, module.project, SYSTEM_ID)
}

private fun actionPerformedSpring(e: AnActionEvent, executorId: String) {
    val element = e.getData(CommonDataKeys.PSI_ELEMENT) ?: return
    val uMethod = element.toUElement() as? UMethod ?: return
    val sprigMainClass = getSpringMainClass(uMethod)?.qualifiedName ?: return
    actionPerformedSpring(element, sprigMainClass, executorId)
}

fun actionPerformedSpring(element: PsiElement, sprigMainClass: String, executorId: String) {
    val module = ModuleUtil.findModuleForPsiElement(element) ?: return
    val mavenModule = MvnUtil.findMavenModuleData(module) ?: return
    val pomPath = mavenModule.getProperty(MODULE_PROP_BUILD_FILE) ?: mavenModule.linkedExternalProjectPath
    val isTest = MvnUtil.isTestFile(element)

    val settings = ExternalSystemTaskExecutionSettings()
    settings.scriptParameters = ParametersListUtil.escape("-Dspring-boot.run.main-class=$sprigMainClass")
    settings.scriptParameters += " -f"
    settings.scriptParameters += " " + ParametersListUtil.escape(MavenPathUtil.checkOnWsl(pomPath))
    if (!isTest) {
        settings.scriptParameters += " -DskipTests"
    }
    if (executorId == DefaultDebugExecutor.EXECUTOR_ID) {
        MvnUtil.setRemoteDebugJvmParam(MavenDebugType.SPRING, settings)
    }

    settings.externalProjectPath = mavenModule.linkedExternalProjectPath
    settings.taskNames = listOf("spring-boot:run")
    settings.externalSystemIdString = SYSTEM_ID.id

    ExternalSystemUtil.runTask(settings, executorId, module.project, SYSTEM_ID)
}

private fun isSpring(uElement: UMethod) = getSpringMainClass(uElement) != null

private fun getSpringMainClass(uElement: UMethod): UClass? {
    val uFile = uElement.getContainingUFile() ?: return null
    for (uClass in uFile.classes) {
        if (MetaAnnotationUtil.isMetaAnnotated(uClass.javaPsi, listOf(SPRING_BOOT_APPLICATION))) {
            return uClass
        }
    }
    return null
}