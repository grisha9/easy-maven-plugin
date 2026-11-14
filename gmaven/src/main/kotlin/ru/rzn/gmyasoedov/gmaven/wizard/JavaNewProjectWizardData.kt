package ru.rzn.gmyasoedov.gmaven.wizard

import com.intellij.openapi.observable.properties.GraphProperty

interface JavaNewProjectWizardData : MavenNewProjectWizardData {

  val addSampleCodeProperty: GraphProperty<Boolean>

  var addSampleCode: Boolean
}