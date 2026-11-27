package com.woogajjangka.coolthemekit;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
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
 *   <li>the editor: the selection and the links of Ctrl+click, tints of the Islands blue in its scheme (VS Code
 *       {@code editor.selectionBackground}, {@code editorLink.activeForeground}).</li>
 * </ul>
 * Only in Islands Dark and Islands Light, with their own editor schemes, as VS Code only colors Cool Islands. The values
 * of the IDE are kept and put back first, so a change of accent or a theme without it leaves nothing behind.
 */
public final class CoolAccentApplier implements LafManagerListener, AppLifecycleListener, EditorColorsListener {
    /** The editor scheme of each theme that takes the accent */
    private static final Map<String, String> SCHEMES = Map.of("Islands Dark", "Islands Dark", "Islands Light", "Light");

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
    /** The editor scheme this class changed, and its values */
    private static @Nullable EditorColorsScheme scheme;
    private static @Nullable Color selection;
    private static @Nullable TextAttributes link;

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

    /** The name of the current theme when it takes the accent, else null */
    private static @Nullable String accentTheme() {
        if (ApplicationManager.getApplication() == null) return null;
        UIThemeLookAndFeelInfo laf = LafManager.getInstance().getCurrentUIThemeLookAndFeel();
        return laf != null && SCHEMES.containsKey(laf.getName()) ? laf.getName() : null;
    }

    /** Puts back what was replaced, then the accent for the current theme and settings */
    static void apply() {
        ApplicationManager.getApplication().invokeLater(() -> {
            String theme = accentTheme();
            boolean ui = applyControls(theme);
            boolean editor = applyEditor(theme);
            if (ui) LafManager.getInstance().repaintUI();
            if (editor) EditorFactory.getInstance().refreshAllEditors();
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

    private static boolean applyEditor(@Nullable String theme) {
        boolean changed = false;
        if (scheme != null) {
            scheme.setColor(EditorColors.SELECTION_BACKGROUND_COLOR, selection);
            scheme.setAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR, link);
            scheme = null;
            changed = true;
        }
        String accent = CoolIconsSettings.accent();
        if (theme == null || accent == null || CoolAccent.isDefault(accent)) return changed;
        EditorColorsScheme current = EditorColorsManager.getInstance().getGlobalScheme();
        if (!SCHEMES.get(theme).equals(current.getName())) return changed;

        Color background = current.getDefaultBackground();
        Color oldSelection = current.getColor(EditorColors.SELECTION_BACKGROUND_COLOR);
        TextAttributes oldLink = current.getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR);
        scheme = current;
        selection = oldSelection;
        link = oldLink;
        if (oldSelection != null) {
            current.setColor(EditorColors.SELECTION_BACKGROUND_COLOR, CoolAccent.toColor(CoolAccent.tint(CoolAccent.toHex(oldSelection), accent)));
        }
        if (oldLink != null && oldLink.getForegroundColor() != null) {
            TextAttributes tinted = oldLink.clone();
            tinted.setForegroundColor(CoolAccent.toColor(
                    CoolAccent.text(CoolAccent.toHex(oldLink.getForegroundColor()), accent, CoolAccent.toHex(background))));
            current.setAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR, tinted);
        }
        return true;
    }
}
