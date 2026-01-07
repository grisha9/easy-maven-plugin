package ru.rzn.gmyasoedov.gmaven.project.action

import com.intellij.codeInsight.MetaAnnotationUtil
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import icons.GMavenIcons
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.getUParentForIdentifier
import org.jetbrains.uast.toUElement
import ru.rzn.gmyasoedov.gmaven.settings.advanced.MavenAdvancedSettingsState
import ru.rzn.gmyasoedov.gmaven.util.MavenMarkerInfoGroup
import ru.rzn.gmyasoedov.gmaven.util.MvnUtil

class MavenRunSpringLineMarkerProvider : LineMarkerProvider {

    override fun collectSlowLineMarkers(elements: List<PsiElement?>, result: MutableCollection<in LineMarkerInfo<*>>) {
        if (!MavenAdvancedSettingsState.getInstance().runLineMarker) return
        super.collectSlowLineMarkers(elements, result)
    }

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val uElement = getUParentForIdentifier(element) as? UClass ?: return null

        val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return null
        MvnUtil.findMavenModuleData(module) ?: return null

        val springAnnotation = findUAnnotation(uElement) ?: return null
        val sourcePsi = springAnnotation.uastAnchor?.sourcePsi ?: return null
        val actionGroup = DefaultActionGroup()
        actionGroup.add(RunAction(uElement, DefaultRunExecutor.EXECUTOR_ID))
        actionGroup.add(RunAction(uElement, DefaultDebugExecutor.EXECUTOR_ID))

        return MavenMarkerInfoGroup(
            sourcePsi,
            GMavenIcons.MavenRun,
            { "Run or Debug Application via Spring-Boot Maven Plugin" },
            actionGroup
        )
    }

    private fun findUAnnotation(uElement: UClass): UAnnotation? {
        return uElement.uAnnotations
            .firstOrNull {
                it.javaPsi?.isMetaAnnotatedByOrSelf(SPRING_BOOT_APPLICATION) == true
            }
    }

    fun PsiAnnotation.isMetaAnnotatedByOrSelf(annotation: String) =
        qualifiedName == annotation || resolveUAnnotationType()?.isMetaAnnotatedBy(annotation) ?: false

    fun PsiAnnotation.resolveUAnnotationType(): PsiClass? {
        val element = nameReferenceElement
        val declaration = element?.resolve().toUElement()?.javaPsi
        if (declaration !is PsiClass || !declaration.isAnnotationType) return null
        return declaration
    }

    fun PsiModifierListOwner.isMetaAnnotatedBy(annotation: String) =
        MetaAnnotationUtil.isMetaAnnotated(this, listOf(annotation))
}

private class RunAction(val uClass: UClass, val executorId: String) : AnAction() {
    init {
        if (executorId == DefaultRunExecutor.EXECUTOR_ID) {
            templatePresentation.text = "Run Spring Boot Application"
            templatePresentation.icon = AllIcons.Actions.Execute
        } else {
            templatePresentation.text = "Debug Spring Boot Application"
            templatePresentation.icon = AllIcons.Actions.StartDebugger
        }
    }

    override fun actionPerformed(event: AnActionEvent) {
        actionPerformedSpring(uClass.javaPsi, uClass.qualifiedName!!, executorId)
    }
}