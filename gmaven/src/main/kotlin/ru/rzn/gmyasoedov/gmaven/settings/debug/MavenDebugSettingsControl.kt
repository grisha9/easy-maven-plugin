package ru.rzn.gmyasoedov.gmaven.settings.debug

import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class MavenDebugSettingsControl(val project: Project) : SearchableConfigurable {
    private val currentSettings = MavenDebugSettingsState.getInstance()

    private val propertyGraph = PropertyGraph()

    private val surefireNameBind = propertyGraph.property("")
    private val surefireValueBind = propertyGraph.property("")

    private val springNameBind = propertyGraph.property("")
    private val springValueBind = propertyGraph.property("")

    private val execNameBind = propertyGraph.property("")
    private val execValueBind = propertyGraph.property("")

    override fun createComponent(): JComponent {
        return panel {
            group("Maven Surefire Plugin") {
                row("Debug param name:") {
                    textField()
                        .align(AlignX.FILL)
                        .bindText(surefireNameBind)
                        .resizableColumn()

                    link("To default") { surefireNameBind.set(MavenDebugType.TEST.paramName) }
                }
                row("Debug param value:") {
                    textField()
                        .align(AlignX.FILL)
                        .bindText(surefireValueBind)
                        .resizableColumn()

                    link("To default") { surefireValueBind.set(DEFAULT_DEBUG_VALUE) }
                }
            }

            group("Spring Boot Maven Plugin") {
                row("Debug param name:") {
                    textField()
                        .align(AlignX.FILL)
                        .bindText(springNameBind)
                        .resizableColumn()

                    link("To default") { springNameBind.set(MavenDebugType.SPRING.paramName) }
                }
                row("Debug param value:") {
                    textField()
                        .align(AlignX.FILL)
                        .bindText(springValueBind)
                        .resizableColumn()

                    link("To default") { springValueBind.set(DEFAULT_DEBUG_VALUE) }
                }
            }

            group("Exec Maven Plugin") {
                row("Debug param name:") {
                    textField()
                        .align(AlignX.FILL)
                        .bindText(execNameBind)
                        .resizableColumn()

                    link("To default") { execNameBind.set(MavenDebugType.EXEC.paramName) }
                }
                row("Debug param value:") {
                    textField()
                        .align(AlignX.FILL)
                        .bindText(execValueBind)
                        .resizableColumn()

                    link("To default") { execValueBind.set(DEFAULT_DEBUG_VALUE) }
                }
            }
        }
    }

    override fun reset() {
        surefireNameBind.set(currentSettings.surefireDebugParamName ?: "")
        surefireValueBind.set(currentSettings.surefireDebugValue ?: "")

        springNameBind.set(currentSettings.springDebugParamName ?: "")
        springValueBind.set(currentSettings.springDebugValue ?: "")

        execNameBind.set(currentSettings.execDebugParamName ?: "")
        execValueBind.set(currentSettings.execDebugValue ?: "")
    }

    override fun isModified(): Boolean {
        if ((currentSettings.surefireDebugParamName ?: "") != surefireNameBind.get()) return true
        if ((currentSettings.surefireDebugValue ?: "") != surefireValueBind.get()) return true

        if ((currentSettings.springDebugParamName ?: "") != springNameBind.get()) return true
        if ((currentSettings.springDebugValue ?: "") != springValueBind.get()) return true

        if ((currentSettings.execDebugParamName ?: "") != execNameBind.get()) return true
        if ((currentSettings.execDebugValue ?: "") != execValueBind.get()) return true

        return false
    }

    override fun apply() {
        currentSettings.surefireDebugParamName = surefireNameBind.get()
        currentSettings.surefireDebugValue = surefireValueBind.get()

        currentSettings.springDebugParamName = springNameBind.get()
        currentSettings.springDebugValue = springValueBind.get()

        currentSettings.execDebugParamName = execNameBind.get()
        currentSettings.execDebugValue = execValueBind.get()
    }


    override fun getId(): String = javaClass.name

    override fun getDisplayName() = id
}
