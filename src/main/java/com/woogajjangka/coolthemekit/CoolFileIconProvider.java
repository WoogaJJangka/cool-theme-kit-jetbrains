package com.woogajjangka.coolthemekit;

import com.intellij.ide.FileIconProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/** Icons of files shown without PSI: editor tabs, Recent Files, Search Everywhere, the file chooser. */
public final class CoolFileIconProvider implements FileIconProvider, DumbAware {
    @Override
    public @Nullable Icon getIcon(@NotNull VirtualFile file, int flags, @Nullable Project project) {
        if (file.isDirectory()) {
            return CoolIconResolver.forFolder(file, project);
        }
        return CoolIconResolver.forFile(file.getName(), file.getFileType());
    }
}
