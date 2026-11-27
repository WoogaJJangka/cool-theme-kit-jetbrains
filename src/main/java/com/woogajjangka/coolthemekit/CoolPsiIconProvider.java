package com.woogajjangka.coolthemekit;

import com.intellij.ide.IconProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/** Icons of files and folders shown through PSI: the Project view, Structure, search results. */
public final class CoolPsiIconProvider extends IconProvider implements DumbAware {
    @Override
    public @Nullable Icon getIcon(@NotNull PsiElement element, int flags) {
        if (element instanceof PsiDirectory directory) {
            return CoolIconResolver.forFolder(directory.getVirtualFile(), directory.getProject());
        }
        if (element instanceof PsiFile file) {
            VirtualFile virtualFile = file.getVirtualFile();
            String name = virtualFile != null ? virtualFile.getName() : file.getName();
            return CoolIconResolver.forFile(name, file.getFileType());
        }
        return null;
    }
}
