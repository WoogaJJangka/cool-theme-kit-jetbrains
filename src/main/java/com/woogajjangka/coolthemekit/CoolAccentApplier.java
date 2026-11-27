package com.woogajjangka.coolthemekit;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.util.Key;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.UIManager;
import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The parts of the accent the IDE does not take from the colors of the theme ({@link CoolThemeCustomizer}), applied at
 * once and again after each change of theme or editor scheme:
 * <ul>
 *   <li>{@code accentTargets: controls}: the badges, the pressed tool buttons and the scrollbars, which Islands leaves
 *       gray (VS Code {@code badge.background}, {@code inputOption.activeBackground}, {@code scrollbarSlider.*}), with
 *       their own opacity;</li>
 *   <li>the editors: the selection and the links of Ctrl+click, tints of the Islands blue (VS Code
 *       {@code editor.selectionBackground}, {@code editorLink.activeForeground}).</li>
 * </ul>
 * Only in Islands Dark and Islands Light, with their editor schemes, as VS Code only colors Cool Islands. The values of
 * the IDE are kept and put back first, so a change of accent or a theme without it leaves nothing behind.
 *
 * <p>The editor scheme is never changed: the IDE saves it (its editable copy {@code _@user_Islands Dark}), so the accent
 * would stay in the settings of the user. Each editor gets the tints in its own colors instead (they live with the
 * editor), and only for a color the user did not change: the one of Islands, as VS Code has it
 * ({@code CoolAccentTest}).
 */
public final class CoolAccentApplier implements LafManagerListener, AppLifecycleListener, EditorColorsListener, EditorFactoryListener {
    private static final Logger LOG = Logger.getInstance(CoolAccentApplier.class);

    /** A theme that takes the accent: its editor scheme, and the colors of it the accent tints */
    record Islands(@NotNull String scheme, @NotNull String selection, @NotNull String link) {
    }

    /** By theme id; the colors are those of Cool Islands in VS Code ({@code editor.selectionBackground}, {@code editorLink.activeForeground}) */
    static final Map<String, Islands> EDITORS = Map.of(
            "Islands Dark", new Islands("Islands Dark", "#214283", "#548af7"),
            "Islands Light", new Islands("Light", "#a6d2ff", "#006dcc"));

    /** The prefix of the editable copy of a bundled scheme, the one the IDE uses and saves */
    private static final String EDITABLE_COPY = "_@user_";

    /** {@code accentTargets: controls}: the UI keys, and the key of the text drawn on them */
    private static final List<String> CONTROLS = List.of(
            "Counter.background", "ActionButton.pressedBackground",
            "ScrollBar.thumbColor", "ScrollBar.hoverThumbColor",
            "ScrollBar.Transparent.thumbColor", "ScrollBar.Transparent.hoverThumbColor",
            "ScrollBar.Mac.thumbColor", "ScrollBar.Mac.hoverThumbColor",
            "ScrollBar.Mac.Transparent.thumbColor", "ScrollBar.Mac.Transparent.hoverThumbColor");
    private static final Map<String, String> CONTROL_TEXT = Map.of("Counter.foreground", "Counter.background");

    /** The UI values of the IDE this class replaced (null: the key had none) */
    private static final Map<String, Object> UI_ORIGINALS = new LinkedHashMap<>();

    @Override
    public void lookAndFeelChanged(@NotNull LafManager source) {
        // the IDE has put the values of the new theme in place of everything
        UI_ORIGINALS.clear();
        apply();
    }

    @Override
    public void appFrameCreated(@NotNull List<String> commandLineArgs) {
        apply();
    }

    @Override
    public void globalSchemeChange(@Nullable EditorColorsScheme changed) {
        apply();
    }

    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
        tintEditor(event.getEditor(), editorTints());
    }

    /** Applies the accent once a project is open: the theme and the editor scheme are in place by then */
    public static final class Startup implements ProjectActivity {
        @Override
        public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
            apply();
            return Unit.INSTANCE;
        }
    }

    /** The id of the current theme when it takes the accent, else null */
    private static @Nullable String accentTheme() {
        if (ApplicationManager.getApplication() == null) return null;
        UIThemeLookAndFeelInfo laf = LafManager.getInstance().getCurrentUIThemeLookAndFeel();
        return laf != null && EDITORS.containsKey(laf.getId()) ? laf.getId() : null;
    }

    /** Puts back what was replaced, then the accent for the current theme and settings */
    static void apply() {
        ApplicationManager.getApplication().invokeLater(() -> {
            boolean ui = applyControls(accentTheme());
            Map<String, Object> tints = editorTints();
            for (Editor editor : EditorFactory.getInstance().getAllEditors()) tintEditor(editor, tints);
            LOG.debug("accent " + CoolIconsSettings.accent() + " " + CoolIconsSettings.accentTargets() + " in " + accentTheme()
                    + ": controls " + ui + ", editors " + tints.keySet());
            if (ui) LafManager.getInstance().repaintUI();
        });
    }

    private static boolean applyControls(@Nullable String theme) {
        boolean changed = !UI_ORIGINALS.isEmpty();
        UI_ORIGINALS.forEach(UIManager::put);
        UI_ORIGINALS.clear();
        if (theme == null || !CoolIconsSettings.accentTargets().contains("controls")) return changed;

        String accent = CoolIconsSettings.accent();
        String color = accent != null ? accent : CoolAccent.DEFAULT_ACCENT;
        for (String key : CONTROLS) {
            Color base = UIManager.getColor(key);
            if (base == null) continue;
            replace(key, CoolAccent.toColor(CoolAccent.target(CoolAccent.toHex(base), color)));
        }
        CONTROL_TEXT.forEach((key, on) -> {
            Color text = UIManager.getColor(key);
            Color background = UIManager.getColor(on);
            if (text == null || background == null) return;
            replace(key, CoolAccent.toColor(CoolAccent.onTarget(CoolAccent.toHex(text), CoolAccent.toHex(background))));
        });
        return true;
    }

    private static void replace(String key, Color value) {
        if (!UI_ORIGINALS.containsKey(key)) UI_ORIGINALS.put(key, UIManager.get(key));
        UIManager.put(key, value);
    }

    /**
     * What the editors show in place of the colors of the scheme: {@code selection} (a color) and {@code link} (text
     * attributes), each only when the accent applies and the scheme still has the color of Islands. Empty: the scheme.
     */
    private static @NotNull Map<String, Object> editorTints() {
        Map<String, Object> tints = new LinkedHashMap<>();
        String theme = accentTheme();
        String accent = CoolIconsSettings.accent();
        if (theme == null || accent == null || CoolAccent.isDefault(accent)) return tints;
        Islands islands = EDITORS.get(theme);
        EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
        String name = scheme.getName().startsWith(EDITABLE_COPY) ? scheme.getName().substring(EDITABLE_COPY.length()) : scheme.getName();
        if (!islands.scheme().equals(name)) return tints;

        Color selection = scheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR);
        if (selection != null && CoolAccent.toHex(selection).equals(islands.selection())) {
            tints.put("selection", CoolAccent.toColor(CoolAccent.tint(islands.selection(), accent)));
        }
        TextAttributes link = scheme.getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR);
        if (link != null && link.getForegroundColor() != null && CoolAccent.toHex(link.getForegroundColor()).equals(islands.link())) {
            TextAttributes tinted = link.clone();
            String background = CoolThemeCustomizer.THEMES.get(theme).editorBackground();
            tinted.setForegroundColor(CoolAccent.toColor(CoolAccent.text(islands.link(), accent, background)));
            tints.put("link", tinted);
        }
        return tints;
    }

    /** Whether an editor got the tints (an editor that never did is left as it is) */
    private static final Key<Boolean> TINTED = Key.create("coolThemeKit.accent");

    /** The colors of one editor: the tints, or those of the scheme again */
    private static void tintEditor(@NotNull Editor editor, @NotNull Map<String, Object> tints) {
        if (editor.isDisposed() || (tints.isEmpty() && editor.getUserData(TINTED) == null)) return;
        editor.putUserData(TINTED, tints.isEmpty() ? null : Boolean.TRUE);
        EditorColorsScheme global = EditorColorsManager.getInstance().getGlobalScheme();
        EditorColorsScheme own = editor.getColorsScheme();
        Color selection = (Color) tints.get("selection");
        own.setColor(EditorColors.SELECTION_BACKGROUND_COLOR,
                selection != null ? selection : global.getColor(EditorColors.SELECTION_BACKGROUND_COLOR));
        TextAttributes link = (TextAttributes) tints.get("link");
        own.setAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR,
                link != null ? link : global.getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR));
        editor.getContentComponent().repaint();
    }
}
