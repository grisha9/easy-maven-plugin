package ru.rzn.gmyasoedov.gmaven.project.externalSystem.model

import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.serialization.PropertyMapping

class LifecycleMaven4Data @PropertyMapping("owner", "name", "linkedExternalProjectPath") constructor(
    owner: ProjectSystemId,
    name: String,
    linkedExternalProjectPath: String,
) : LifecycleData(owner, name, linkedExternalProjectPath) {

    companion object {
        val KEY = Key.create(
            LifecycleMaven4Data::class.java,
            LifecycleData.KEY.processingWeight
        )
    }
}