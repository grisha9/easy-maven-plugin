/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ru.rzn.gmyasoedov.gmaven.chooser;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import ru.rzn.gmyasoedov.gmaven.utils.MavenUtils;

import java.util.stream.Stream;

public class MavenPomFileChooserDescriptor extends FileChooserDescriptor {


  public MavenPomFileChooserDescriptor() {
    super(false, true, false, false, false, false);
  }

  @Override
  public boolean isFileSelectable(@Nullable VirtualFile file) {
    if (!super.isFileSelectable(file)) return false;
    return Stream.of(file.getChildren()).anyMatch(MavenUtils::isSimplePomFile);
  }
}
