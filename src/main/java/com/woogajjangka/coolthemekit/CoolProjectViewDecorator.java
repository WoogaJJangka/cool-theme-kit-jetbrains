package com.woogajjangka.coolthemekit;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/**
 * Sets the icon of the file and folder nodes of the Project view.
 *
 * <p>Some file kinds draw their own icon without asking the icon providers (the Python file of PyCharm), so in the
 * Project view the icon is set on the node too. Only nodes of a file or a folder: the nodes of a class or a function in a
 * file keep their icons.
 */
public final class CoolProjectViewDecorator implements ProjectViewNodeDecorator, DumbAware {
    @Override
    public void decorate(@NotNull ProjectViewNode<?> node, @NotNull PresentationData data) {
        if (!(node.getValue() instanceof PsiFileSystemItem)) return;
        VirtualFile file = node.getVirtualFile();
        if (file == null || !file.isValid()) return;
        Icon icon = file.isDirectory()
                ? CoolIconResolver.forFolder(file, node.getProject())
                : CoolIconResolver.forFile(file.getName(), file.getFileType());
        if (icon != null) data.setIcon(icon);
    }
}
