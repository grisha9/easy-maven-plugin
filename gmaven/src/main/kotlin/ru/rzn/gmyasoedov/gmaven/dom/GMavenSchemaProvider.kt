package ru.rzn.gmyasoedov.gmaven.dom

import com.intellij.javaee.ResourceRegistrar
import com.intellij.javaee.StandardResourceProvider
import ru.rzn.gmyasoedov.gmaven.utils.MavenUtils

class GMavenSchemaProvider : StandardResourceProvider {
    override fun registerResources(registrar: ResourceRegistrar?) {
        if (MavenUtils.pluginEnabled(MavenUtils.INTELLIJ_MAVEN_PLUGIN_ID) || registrar == null) return

        mutableMapOf(
            "http://maven.apache.org/xsd/maven-4.0.0.xsd" to "/schemas/maven-4.0.0.xsd",
            "http://maven.apache.org/maven-v4_0_0.xsd" to "/schemas/maven-4.0.0.xsd",
            "http://maven.apache.org/xsd/settings-1.2.0.xsd" to "/schemas/settings-1.2.0.xsd",
        ).forEach { (url, path) -> addStdResource(registrar, url, path) }
    }

    private fun addStdResource(registrar: ResourceRegistrar, schemaUrl: String, schemaPath: String) {
        registrar.addStdResource(schemaUrl, schemaPath, javaClass.classLoader)
        if (schemaUrl.startsWith("http://")) {
            val schemaUrlHttps = schemaUrl.replace("http://", "https://")
            registrar.addStdResource(schemaUrlHttps, schemaPath, javaClass.classLoader)
        }
    }
}