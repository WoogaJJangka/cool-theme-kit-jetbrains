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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The settings of Cool Theme Kit, named and defaulted as in Cool Theme Kit for VS Code ({@code a-file-icon-vscode.*}),
 * so both editors look the same with the same choices.
 */
@Service(Service.Level.APP)
@State(name = "CoolThemeKitIcons", storages = @Storage("coolThemeKit.xml"))
public final class CoolIconsSettings implements PersistentStateComponent<CoolIconsSettings.State> {
    public static final class State {
        /** VS Code {@code roleMarks}: the mark of a role over the icon of {@code main.py}, {@code __init__.py}… */
        public boolean roleMarks = true;
        /** VS Code {@code accentColor}: {@code #rrggbb} instead of the Islands blue, "" for none */
        public String accentColor = "";
        /** VS Code {@code accentTargets}: {@code controls}, {@code tabs}, {@code folders} also take the accent */
        public List<String> accentTargets = new ArrayList<>();
        /** VS Code {@code iconStyle}: {@code glass} (the default) or {@code flat} */
        public String iconStyle = GLASS;
        /** VS Code {@code filesAssociations}: {@code *.ext} or a file name -> a bundled icon name or an SVG file */
        public Map<String, String> filesAssociations = new LinkedHashMap<>();
        /** VS Code {@code foldersAssociations}: a folder name -> a bundled folder icon name or an SVG file */
        public Map<String, String> foldersAssociations = new LinkedHashMap<>();
        /** VS Code {@code languagesAssociations}: a language id -> a bundled icon name or an SVG file */
        public Map<String, String> languagesAssociations = new LinkedHashMap<>();
    }

    static final String GLASS = "glass";
    static final String FLAT = "flat";

    private State state = new State();
    /** The associations of the state, read once for every icon asked for. */
    private volatile CoolAssociations associations;

    static @NotNull CoolIconsSettings getInstance() {
        return ApplicationManager.getApplication().getService(CoolIconsSettings.class);
    }

    /** The value the resolver reads; true without an application (the unit tests). */
    static boolean roleMarks() {
        return ApplicationManager.getApplication() == null || getInstance().state.roleMarks;
    }

    /** The accent ({@code #rrggbb}), or null for the Islands blue; null without an application (the unit tests) */
    static @Nullable String accent() {
        if (ApplicationManager.getApplication() == null) return null;
        return CoolAccent.normalize(getInstance().state.accentColor);
    }

    /** The targets of {@code accentTargets} that are known, once each, in the order of VS Code */
    static @NotNull List<String> accentTargets() {
        if (ApplicationManager.getApplication() == null) return List.of();
        return known(getInstance().state.accentTargets);
    }

    private static List<String> known(@Nullable List<String> targets) {
        return targets == null ? List.of() : CoolAccent.TARGETS.stream().filter(targets::contains).toList();
    }

    /**
     * The color of the folder icons: the accent (or the Islands blue) when {@code accentTargets} has {@code folders}, as
     * {@code effectiveFolderColor} in VS Code; null for the icons as they are drawn.
     */
    static @Nullable String folderColor() {
        if (!accentTargets().contains("folders")) return null;
        String accent = accent();
        return accent != null ? accent : CoolAccent.DEFAULT_ACCENT;
    }

    /** Whether the icons are the flat ones; false without an application (the unit tests). */
    static boolean flatIcons() {
        return ApplicationManager.getApplication() != null && FLAT.equals(getInstance().state.iconStyle);
    }

    /** The associations of the user; none without an application (the unit tests). */
    static @NotNull CoolAssociations associations() {
        if (ApplicationManager.getApplication() == null) return CoolAssociations.NONE;
        CoolIconsSettings settings = getInstance();
        CoolAssociations current = settings.associations;
        if (current == null) {
            State s = settings.state;
            current = new CoolAssociations(orEmpty(s.filesAssociations), orEmpty(s.foldersAssociations), orEmpty(s.languagesAssociations));
            settings.associations = current;
        }
        return current;
    }

    private static Map<String, String> orEmpty(@Nullable Map<String, String> map) {
        return map == null ? Map.of() : map;
    }

    @Override
    public @NotNull State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State loaded) {
        state = loaded;
        associations = null;
    }

    /** Sets the icon style and the associations; the icons change at once. */
    void setIcons(@NotNull String iconStyle, @NotNull Map<String, String> files, @NotNull Map<String, String> folders,
                  @NotNull Map<String, String> languages) {
        String style = FLAT.equals(iconStyle) ? FLAT : GLASS;
        if (style.equals(state.iconStyle) && files.equals(orEmpty(state.filesAssociations))
                && folders.equals(orEmpty(state.foldersAssociations)) && languages.equals(orEmpty(state.languagesAssociations))) {
            return;
        }
        state.iconStyle = style;
        state.filesAssociations = new LinkedHashMap<>(files);
        state.foldersAssociations = new LinkedHashMap<>(folders);
        state.languagesAssociations = new LinkedHashMap<>(languages);
        associations = null;
        refreshIcons();
    }

    void setRoleMarks(boolean roleMarks) {
        if (state.roleMarks == roleMarks) return;
        state.roleMarks = roleMarks;
        refreshIcons();
    }

    /**
     * Sets the accent and its targets. The folder icons, the controls and the editor change at once.
     * @param accent {@code #rrggbb}, or null for the Islands blue
     * @return whether the colors of the theme change too: the IDE reads a theme once, a restart shows them
     */
    boolean setAccent(@Nullable String accent, @NotNull List<String> targets) {
        String color = CoolAccent.normalize(accent);
        List<String> chosen = known(targets);
        String before = CoolThemeCustomizer.fingerprint(accent(), accentTargets());
        if (java.util.Objects.equals(color, accent()) && chosen.equals(accentTargets())) return false;
        state.accentColor = color == null ? "" : color;
        state.accentTargets = new ArrayList<>(chosen);
        CoolIcons.clearRecolored();
        refreshIcons();
        CoolAccentApplier.apply();
        return !before.equals(CoolThemeCustomizer.fingerprint(accent(), accentTargets()));
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
