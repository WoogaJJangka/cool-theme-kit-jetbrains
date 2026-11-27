package com.woogajjangka.coolthemekit;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/** Which Cool Icon a file or folder of a project gets. Shared by the PSI and the virtual file providers. */
final class CoolIconResolver {
    private CoolIconResolver() {
    }

    static @Nullable Icon forFile(@NotNull String name, @Nullable FileType fileType) {
        return CoolIcons.load(fileIcon(name, fileType, CoolIconsSettings.roleMarks()));
    }

    /**
     * The name first, as VS Code; then the language of the file type, or the language it is a dialect of (PostgreSQL is
     * SQL, Angular templates are HTML) when VS Code has no icon for the dialect.
     */
    static @Nullable String fileIcon(@NotNull String name, @Nullable FileType fileType, boolean roleMarks) {
        String icon = CoolIcons.nameIcon(name);
        if (icon != null) return roleMarks ? icon : CoolIcons.withoutRoleMark(icon);
        if (fileType instanceof LanguageFileType languageFileType) {
            for (Language language = languageFileType.getLanguage(); language != null; language = language.getBaseLanguage()) {
                icon = CoolIcons.languageIcon(language.getID());
                if (icon != null) return icon;
            }
        }
        return CoolIcons.defaultFileIcon();
    }

    /**
     * The icon of a folder, the open folder while it is expanded in a tree. Source, test and resource roots and excluded
     * folders keep the icon of the IDE, which tells what the folder is in the project (the IDE has no other mark for it);
     * a content root gets the root folder icon.
     */
    static @Nullable Icon forFolder(@NotNull VirtualFile folder, @Nullable Project project) {
        if (project != null && !project.isDisposed()) {
            ProjectFileIndex index = ProjectFileIndex.getInstance(project);
            if (folder.equals(index.getContentRootForFile(folder))) {
                return CoolIcons.loadFolder(CoolIcons.rootFolderIcon(false), CoolIcons.rootFolderIcon(true));
            }
            if (folder.equals(index.getSourceRootForFile(folder)) || index.isExcluded(folder)) {
                return null;
            }
        }
        String name = folder.getName();
        return CoolIcons.loadFolder(CoolIcons.folderIcon(name, false), CoolIcons.folderIcon(name, true));
    }
}
