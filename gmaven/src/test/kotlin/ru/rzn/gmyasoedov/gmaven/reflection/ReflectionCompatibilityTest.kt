package ru.rzn.gmyasoedov.gmaven.reflection

import com.intellij.openapi.externalSystem.action.DetachExternalProjectAction
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunnableState
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import org.junit.Assert
import org.junit.Test
import ru.rzn.gmyasoedov.gmaven.GMavenConstants

class ReflectionCompatibilityTest {

    //need for check DetachExternalProjectAction.detachProject exist because used reflection for call it
    @Test
    fun testUnlinkProject() {
        Assert.assertTrue(true)
    }

    @Test
    fun testExternalSystemRunnableStateDebugger() {
        println(ExternalSystemRunnableState.DEBUGGER_DISPATCH_ADDR_KEY)
    }

    private fun unlinkProject(project: Project, externalProjectPath: String) {
        val systemId = GMavenConstants.SYSTEM_ID
        val projectData = ExternalSystemApiUtil.findProjectNode(project, systemId, externalProjectPath)?.data ?: return
        DetachExternalProjectAction.detachProject(project, projectData.owner, projectData, null)
    }
}