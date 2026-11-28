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

    /** VS Code {@code hideFileIcons} and {@code folderTheme: "none"}: no icon at all (an empty one, so the IDE draws none either). */
    private static final Icon NO_ICON = com.intellij.util.ui.EmptyIcon.ICON_16;

    static @Nullable Icon forFile(@NotNull String name, @Nullable FileType fileType) {
        if (CoolIconsSettings.hideFileIcons()) return NO_ICON;
        return CoolIcons.load(fileIcon(name, fileType, CoolIconsSettings.roleMarks(), CoolIconsSettings.associations()));
    }

    static @Nullable String fileIcon(@NotNull String name, @Nullable FileType fileType, boolean roleMarks) {
        return fileIcon(name, fileType, roleMarks, CoolAssociations.NONE);
    }

    /**
     * The name first, as VS Code; then the language of the file type, or the language it is a dialect of (PostgreSQL is
     * SQL, Angular templates are HTML) when VS Code has no icon for the dialect. The associations of the user come first
     * at each step ({@link CoolAssociations}).
     */
    static @Nullable String fileIcon(@NotNull String name, @Nullable FileType fileType, boolean roleMarks,
                                     @NotNull CoolAssociations associations) {
        String icon = associations.nameIcon(name, roleMarks);
        if (icon != null) return icon;
        if (fileType instanceof LanguageFileType languageFileType) {
            for (Language language = languageFileType.getLanguage(); language != null; language = language.getBaseLanguage()) {
                icon = associations.languageIcon(language.getID());
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
        String folderTheme = CoolIconsSettings.folderTheme();
        if (project != null && !project.isDisposed()) {
            ProjectFileIndex index = ProjectFileIndex.getInstance(project);
            if (folder.equals(index.getContentRootForFile(folder))) {
                if (CoolIconsSettings.FOLDERS_NONE.equals(folderTheme)) return NO_ICON;
                return CoolIcons.loadFolder(CoolIcons.rootFolderIcon(false), CoolIcons.rootFolderIcon(true));
            }
            if (folder.equals(index.getSourceRootForFile(folder)) || index.isExcluded(folder)) {
                return null;
            }
        }
        if (CoolIconsSettings.FOLDERS_NONE.equals(folderTheme)) return NO_ICON;
        String name = folder.getName();
        CoolAssociations associations = CoolIconsSettings.associations();
        boolean specific = CoolIconsSettings.FOLDERS_SPECIFIC.equals(folderTheme);
        return CoolIcons.loadFolder(associations.folderIcon(name, false, specific), associations.folderIcon(name, true, specific));
    }
}
