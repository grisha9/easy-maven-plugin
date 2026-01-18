package ru.rzn.gmyasoedov.gmaven.project.action

import com.intellij.codeInsight.MetaAnnotationUtil
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.*
import icons.GMavenIcons
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.getUParentForIdentifier
import org.jetbrains.uast.toUElement
import ru.rzn.gmyasoedov.gmaven.settings.advanced.MavenAdvancedSettingsState
import ru.rzn.gmyasoedov.gmaven.util.MavenMarkerInfoGroup
import ru.rzn.gmyasoedov.gmaven.util.MvnUtil

class SpringBootRunLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (!MavenAdvancedSettingsState.getInstance().runLineMarker) return null
        val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return null
        if (!isSpringBootModule(module)) return null
        val uClass = getUParentForIdentifier(element) as? UClass ?: return null
        MvnUtil.findMavenModuleData(module) ?: return null
        return getSpringBootLineMarkerInfo(uClass)
    }

    private fun getSpringBootLineMarkerInfo(uClass: UClass): LineMarkerInfo<*>? {
        val qualifiedName = uClass.qualifiedName ?: return null
        val springAnnotation = findUAnnotation(uClass) ?: return null

        val sourcePsi = springAnnotation.uastAnchor?.sourcePsi ?: return null
        val actionGroup = DefaultActionGroup()
        actionGroup.add(RunSpringGutterAction(qualifiedName, DefaultRunExecutor.EXECUTOR_ID))
        actionGroup.add(RunSpringGutterAction(qualifiedName, DefaultDebugExecutor.EXECUTOR_ID))

        return MavenMarkerInfoGroup(
            sourcePsi,
            GMavenIcons.MavenRun,
            { "Run or Debug Application via Spring-Boot Maven Plugin" },
            actionGroup
        )
    }

    private fun isSpringBootModule(module: Module): Boolean {
        return JavaPsiFacade.getInstance(module.project)
            .findClass(SPRING_BOOT_APPLICATION, module.moduleWithLibrariesScope) != null
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

private class RunSpringGutterAction(val qualifiedName: String, val executorId: String) : AnAction() {
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
        val element = e.getData(CommonDataKeys.PSI_FILE) ?: return
        actionPerformedSpring(element, qualifiedName, executorId)
    }
}