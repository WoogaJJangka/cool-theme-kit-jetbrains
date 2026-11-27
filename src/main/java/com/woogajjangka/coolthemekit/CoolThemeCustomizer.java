package com.woogajjangka.coolthemekit;

import com.intellij.ide.ui.customization.UIThemeCustomizer;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The accent in the colors of Islands Dark and Islands Light, as VS Code draws it in Cool Islands Dark and Light.
 *
 * <p>Islands names the colors of its parts ({@code accent-brand-bg}, {@code text-link}, {@code selection-bg-active},
 * {@code tab-selected-border-active}…) and gives each a blue of its palette ({@code blue-80}…). Every UI key that shows
 * the accent goes through one of these names, and nothing else does (the blues of the debugger, charts and colored tags
 * use the palette or their own colors). So the accent replaces these names, and only them: the one that is the accent
 * itself ({@code blue-80}) takes the chosen color, the others its tint, the text ones also keep their contrast on the
 * editor ({@link CoolAccent}). {@code accentTargets: tabs} paints the lines of the selected tab with the accent.
 *
 * <p>The IDE asks this service ({@code open} in the platform, replaced with {@code overrides="true"}) for the colors of a
 * theme when it reads the theme, once: a change is seen after a restart.
 */
public final class CoolThemeCustomizer implements UIThemeCustomizer {
    private static final Logger LOG = Logger.getInstance(CoolThemeCustomizer.class);

    /** A theme that takes the accent: its file, and the background of its editor (the text tints are measured on it) */
    record Theme(@NotNull String file, @NotNull String editorBackground) {
    }

    /** The themes of the names the IDE gives ({@code UIThemeBean.name}), the editor backgrounds those of VS Code */
    static final Map<String, Theme> THEMES = Map.of(
            "Islands Dark", new Theme("/themes/islands/ManyIslandsDark.theme.json", "#191a1c"),
            "Islands Light", new Theme("/themes/islands/ManyIslandsLight.theme.json", "#ffffff"));

    /** The names that are text on the editor or a list: links, the matched letters of the completion popup */
    private static final Set<String> TEXT = Set.of("text-link", "popup-completion-match-text");
    private static final Pattern BLUE = Pattern.compile("blue-\\d+");
    private static final Pattern COLORS = Pattern.compile("\"colors\"\\s*:\\s*\\{([^}]*)}");
    private static final Pattern ENTRY = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"");

    private static final Map<String, Map<String, String>> PALETTES = new ConcurrentHashMap<>();

    @Override
    public @NotNull Map<String, Color> createColorCustomizer(@NotNull String themeName) {
        Theme theme = THEMES.get(themeName);
        if (theme == null) return Map.of();
        try {
            Map<String, String> palette = PALETTES.computeIfAbsent(themeName, name -> readPalette(theme.file));
            Map<String, Color> result = new LinkedHashMap<>();
            colors(palette, CoolIconsSettings.accent(), CoolIconsSettings.accentTargets(), theme.editorBackground)
                    .forEach((name, value) -> result.put(name, CoolAccent.toColor(value)));
            return result;
        } catch (RuntimeException e) {
            LOG.warn("Cannot put the accent in " + themeName, e);
            return Map.of();
        }
    }

    @Override
    public @NotNull Map<String, String> createNamedColorCustomizer(@NotNull String themeName) {
        return Map.of();
    }

    @Override
    public @NotNull Map<String, String> createIconCustomizer(@NotNull String themeName) {
        return Map.of();
    }

    @Override
    public @NotNull Map<String, String> createEditorThemeCustomizer(@NotNull String themeName) {
        return Map.of();
    }

    /** What the colors of the theme depend on: a change of it needs a restart to be seen */
    static @NotNull String fingerprint(@Nullable String accent, @NotNull List<String> targets) {
        return (accent == null ? "" : accent) + (targets.contains("tabs") ? "+tabs" : "");
    }

    /** The named colors of a theme file ({@code "colors"}), as written */
    static @NotNull Map<String, String> readPalette(@NotNull String file) {
        try (InputStream in = UIThemeCustomizer.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException(file + " not found");
            return parsePalette(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
    }

    static @NotNull Map<String, String> parsePalette(@NotNull String json) {
        Matcher colors = COLORS.matcher(json);
        if (!colors.find()) throw new IllegalStateException("no colors in the theme");
        Map<String, String> palette = new LinkedHashMap<>();
        Matcher entry = ENTRY.matcher(colors.group(1));
        while (entry.find()) palette.put(entry.group(1), entry.group(2));
        return palette;
    }

    /** The color of a name, through the names it refers to, as {@code #rrggbb[aa]} in lower case */
    static @Nullable String resolve(@NotNull Map<String, String> palette, @NotNull String name) {
        String value = name;
        for (int depth = 0; depth < 16 && palette.containsKey(value); depth++) value = palette.get(value);
        return value.startsWith("#") ? value.toLowerCase(Locale.ROOT) : null;
    }

    /**
     * The named colors the accent changes, and their new values.
     * @param accent {@code #rrggbb}, null for the Islands blue
     */
    static @NotNull Map<String, String> colors(@NotNull Map<String, String> palette, @Nullable String accent,
                                               @NotNull List<String> targets, @NotNull String editorBackground) {
        Map<String, String> result = new LinkedHashMap<>();
        if (accent != null && !CoolAccent.isDefault(accent)) {
            for (Map.Entry<String, String> entry : palette.entrySet()) {
                String name = entry.getKey();
                if (BLUE.matcher(name).matches() || !BLUE.matcher(entry.getValue()).matches()) continue;
                String base = resolve(palette, name);
                if (base == null) continue;
                String color;
                if (base.startsWith(CoolAccent.DEFAULT_ACCENT)) color = CoolAccent.solid(base, accent);
                else if (TEXT.contains(name)) color = CoolAccent.text(base, accent, editorBackground);
                else color = CoolAccent.tint(base, accent);
                result.put(name, color);
            }
            // the text on the accent (default buttons) stays readable
            String onAccent = resolve(palette, "text-over-accent");
            if (onAccent != null) result.put("text-over-accent", CoolAccent.onAccent(onAccent, accent));
        }
        if (targets.contains("tabs")) {
            String color = accent != null ? accent : CoolAccent.DEFAULT_ACCENT;
            String active = resolve(palette, "tab-selected-border-active");
            if (active != null) result.put("tab-selected-border-active", CoolAccent.target(active, color));
            // VS Code tab.unfocusedActiveBorder: the accent at half
            if (palette.containsKey("tab-selected-border-inactive")) {
                result.put("tab-selected-border-inactive", CoolAccent.withAlpha(color, "80"));
            }
        }
        result.entrySet().removeIf(entry -> entry.getValue().equals(resolve(palette, entry.getKey())));
        return result;
    }
}
