package ru.rzn.gmyasoedov.gmaven.project.action

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import icons.GMavenIcons
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElement
import ru.rzn.gmyasoedov.gmaven.settings.advanced.MavenAdvancedSettingsState
import ru.rzn.gmyasoedov.gmaven.util.MavenMarkerInfoGroup
import ru.rzn.gmyasoedov.gmaven.util.MvnUtil

class MavenRunTestLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement) = null

    override fun collectSlowLineMarkers(elements: List<PsiElement?>, result: MutableCollection<in LineMarkerInfo<*>>) {
        if (!MavenAdvancedSettingsState.getInstance().runLineMarker) return
        val element = elements.firstOrNull() ?: return
        if (!MvnUtil.isTestFile(element)) return
        val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return
        if (!MvnUtil.isUnderProject(element)) return
        MvnUtil.findMavenModuleData(module) ?: return
        val uClasses = (element.containingFile?.toUElement() as? UFile)?.classes ?: emptyList()

        uClasses.flatMap { it.methods.asSequence() }.forEach {
            getTestLineMarkerInfo(it)?.let { marker -> result += marker }
        }
    }

    private fun getTestLineMarkerInfo(uMethod: UMethod): LineMarkerInfo<*>? {
        val testUAnnotation = uMethod.uAnnotations.find { it.qualifiedName?.endsWith("Test") == true } ?: return null

        val sourcePsi = testUAnnotation.uastAnchor?.sourcePsi ?: return null
        val actionGroup = DefaultActionGroup()
        val smartPointer = uMethod.javaPsi.toSmartPointer()
        actionGroup.add(RunTestGutterAction(smartPointer, DefaultRunExecutor.EXECUTOR_ID))
        actionGroup.add(RunTestGutterAction(smartPointer, DefaultDebugExecutor.EXECUTOR_ID))

        return MavenMarkerInfoGroup(
            sourcePsi,
            GMavenIcons.MavenRun,
            { "Run or Debug Test via Maven Surefire Plugin" },
            actionGroup
        )
    }
}

fun PsiElement.toSmartPointer(): SmartPsiElementPointer<PsiElement> {
    return SmartPointerManager.getInstance(project).createSmartPsiElementPointer(this, this.containingFile)
}

internal class RunTestGutterAction(
    val elementPointer: SmartPsiElementPointer<PsiElement>, val executorId: String
) : AnAction() {
    init {
        if (executorId == DefaultRunExecutor.EXECUTOR_ID) {
            templatePresentation.text = "Run Spring Boot Application"
            templatePresentation.icon = AllIcons.Actions.Execute
        } else {
            templatePresentation.text = "Debug Spring Boot Application"
            templatePresentation.icon = AllIcons.Actions.StartDebugger
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val element = elementPointer.element ?: return
        actionPerformedTest(element, executorId)
    }
}