package ru.rzn.gmyasoedov.gmaven.project.importing.kotlin

import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.compilerPlugin.CompilerPluginSetup
import java.io.File


object AllOpenMavenProjectImportHandler : AbstractMavenImportHandler() {
    private const val ANNOTATION_PARAMETER_PREFIX = "all-open:annotation="
    private val SUPPORTED_PRESETS = mapOf(
        "spring" to listOf(
            "org.springframework.stereotype.Component",
            "org.springframework.transaction.annotation.Transactional",
            "org.springframework.scheduling.annotation.Async",
            "org.springframework.cache.annotation.Cacheable",
            "org.springframework.boot.test.context.SpringBootTest",
            "org.springframework.validation.annotation.Validated"
        ),
        "quarkus" to listOf(
            "javax.enterprise.context.ApplicationScoped",
            "javax.enterprise.context.RequestScoped"
        ),
        "micronaut" to listOf(
            "io.micronaut.aop.Around",
            "io.micronaut.aop.Introduction",
            "io.micronaut.aop.InterceptorBinding",
            "io.micronaut.aop.InterceptorBindingDefinitions"
        )
    )


    override val compilerPluginId = "org.jetbrains.kotlin.allopen"
    override val pluginName = "allopen"
    override val pluginJarFileFromIdea: File = KotlinArtifacts.allopenCompilerPluginPath.toFile()

    override fun getOptions(
        enabledCompilerPlugins: List<String>,
        compilerPluginOptions: List<String>
    ): List<CompilerPluginSetup.PluginOption>? {
        if ("all-open" !in enabledCompilerPlugins && "spring" !in enabledCompilerPlugins) {
            return null
        }

        val annotations = mutableListOf<String>()

        for ((presetName, presetAnnotations) in SUPPORTED_PRESETS) {
            if (presetName in enabledCompilerPlugins) {
                annotations.addAll(presetAnnotations)
            }
        }

        annotations.addAll(compilerPluginOptions.mapNotNull { text ->
            if (!text.startsWith(ANNOTATION_PARAMETER_PREFIX)) return@mapNotNull null
            text.substring(ANNOTATION_PARAMETER_PREFIX.length)
        })

        return annotations.map { CompilerPluginSetup.PluginOption("annotation", it) }
    }
}
