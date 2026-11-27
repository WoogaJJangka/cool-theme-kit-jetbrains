package com.woogajjangka.coolthemekit;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.ui.IconDeferrer;
import org.jetbrains.annotations.NotNull;

/**
 * The settings of Cool Icons, named and defaulted as in Cool Theme Kit for VS Code ({@code a-file-icon-vscode.*}), so
 * both editors show the same icons with the same choices.
 */
@Service(Service.Level.APP)
@State(name = "CoolThemeKitIcons", storages = @Storage("coolThemeKit.xml"))
public final class CoolIconsSettings implements PersistentStateComponent<CoolIconsSettings.State> {
    public static final class State {
        /** VS Code {@code roleMarks}: the mark of a role over the icon of {@code main.py}, {@code __init__.py}… */
        public boolean roleMarks = true;
    }

    private State state = new State();

    static @NotNull CoolIconsSettings getInstance() {
        return ApplicationManager.getApplication().getService(CoolIconsSettings.class);
    }

    /** The value the resolver reads; true without an application (the unit tests). */
    static boolean roleMarks() {
        return ApplicationManager.getApplication() == null || getInstance().state.roleMarks;
    }

    @Override
    public @NotNull State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State loaded) {
        state = loaded;
    }

    void setRoleMarks(boolean roleMarks) {
        if (state.roleMarks == roleMarks) return;
        state.roleMarks = roleMarks;
        refreshIcons();
    }

    /** Draws the file icons of every open project again: the Project view and the editor tabs. */
    static void refreshIcons() {
        IconDeferrer.getInstance().clearCache();
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            if (project.isDisposed()) continue;
            ProjectView.getInstance(project).refresh();
            FileEditorManagerEx.getInstanceEx(project).refreshIcons();
        }
    }
}
