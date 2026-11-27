package com.woogajjangka.coolthemekit;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The accent color of Cool Theme Kit: the blue of the Islands themes, which the setting {@code accentColor} replaces.
 * A port of {@code src/themes/accent.ts} of Cool Theme Kit for VS Code, so both editors draw the same colors from the
 * same accent ({@code CoolAccentTest} checks it against the colors VS Code gives, {@code bun run export:jetbrains}).
 *
 * <ul>
 *   <li>A part Islands paints with the accent itself takes exactly the chosen color ({@link #solid}).</li>
 *   <li>A part Islands paints with a color derived from the accent (the selection, the links) keeps its lightness and
 *       opacity, its hue moved by the same angle as the accent and its saturation scaled by the same ratio
 *       ({@link #tint}); text also keeps its contrast on the editor ({@link #text}).</li>
 *   <li>Text on the accent stays readable: kept if it reads, else black or white ({@link #onAccent}).</li>
 *   <li>The parts of {@code accentTargets}, which Islands leaves gray, take the accent with their own opacity
 *       ({@link #target}).</li>
 * </ul>
 * Colors are {@code #rrggbb} or {@code #rrggbbaa}, in lower case.
 */
final class CoolAccent {
    /** The accent of the Islands themes ({@code blue-80}), which the tints are measured from */
    static final String DEFAULT_ACCENT = "#3871e1";

    /** A named accent: the Islands blue, then the accents of the Material themes, one per hue (as in VS Code) */
    record Preset(@NotNull String id, @NotNull String color, @Nullable String source) {
    }

    /** The same list, in the same order, as {@code ACCENT_PRESETS} of Cool Theme Kit; the names are {@code accent.preset.<id>} */
    static final List<Preset> PRESETS = List.of(
            new Preset("islandsBlue", DEFAULT_ACCENT, null),
            new Preset("green", "#79cb60", "GitHub"),
            new Preset("teal", "#009688", "Material Oceanic"),
            new Preset("cyan", "#00bcd4", "Material Lighter"),
            new Preset("aqua", "#84ffff", "Material Deep Ocean"),
            new Preset("blue", "#2979ff", "Atom One Dark"),
            new Preset("violet", "#7e57c2", "Night Owl"),
            new Preset("purple", "#ab47bc", "Material Palenight"),
            new Preset("magenta", "#d33682", "Solarized Dark"),
            new Preset("pink", "#ff79c5", "Dracula"),
            new Preset("coral", "#f9826c", "GitHub Dark"),
            new Preset("orange", "#ff9800", "Material Darker"),
            new Preset("yellow", "#ffd866", "Monokai Pro"));

    /** The targets of {@code accentTargets}, in the order of VS Code */
    static final List<String> TARGETS = List.of("controls", "tabs", "folders");

    private static final double MIN_CONTRAST = 4.5;
    private static final String DARK_TEXT = "#000000";
    private static final String LIGHT_TEXT = "#ffffff";
    private static final Pattern HEX = Pattern.compile("^#([\\dA-Fa-f]{3,4}|[\\dA-Fa-f]{6}|[\\dA-Fa-f]{8})$");

    private CoolAccent() {
    }

    /** A color: its channels (0–255) and its opacity as the two digits it had, "" when opaque */
    private record Rgba(double r, double g, double b, @NotNull String alpha) {
    }

    private record Hsl(double h, double s, double l) {
    }

    private static @Nullable Rgba parse(@Nullable String value) {
        if (value == null) return null;
        Matcher match = HEX.matcher(value.trim());
        if (!match.matches()) return null;
        String digits = match.group(1);
        if (digits.length() <= 4) {
            StringBuilder full = new StringBuilder();
            for (char digit : digits.toCharArray()) full.append(digit).append(digit);
            digits = full.toString();
        }
        int rgb = Integer.parseInt(digits.substring(0, 6), 16);
        String alpha = digits.length() == 8 ? digits.substring(6).toLowerCase(Locale.ROOT) : "";
        return new Rgba((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, alpha.equals("ff") ? "" : alpha);
    }

    private static @NotNull Rgba rgb(@NotNull String value) {
        Rgba color = parse(value);
        if (color == null) throw new IllegalArgumentException("not a color: " + value);
        return color;
    }

    private static String channel(double value) {
        return String.format("%02x", Math.round(Math.min(255, Math.max(0, value))));
    }

    private static @NotNull String hex(@NotNull Rgba color, @NotNull String alpha) {
        return "#" + channel(color.r) + channel(color.g) + channel(color.b) + alpha;
    }

    private static Hsl toHsl(Rgba color) {
        double red = color.r / 255;
        double green = color.g / 255;
        double blue = color.b / 255;
        double max = Math.max(red, Math.max(green, blue));
        double min = Math.min(red, Math.min(green, blue));
        double l = (max + min) / 2;
        double delta = max - min;
        if (delta == 0) return new Hsl(0, 0, l);
        double s = delta / (1 - Math.abs(2 * l - 1));
        double h;
        if (max == red) h = ((green - blue) / delta) % 6;
        else if (max == green) h = (blue - red) / delta + 2;
        else h = (red - green) / delta + 4;
        return new Hsl((h * 60 + 360) % 360, s, l);
    }

    private static Rgba toRgb(Hsl color, String alpha) {
        double chroma = (1 - Math.abs(2 * color.l - 1)) * color.s;
        double x = chroma * (1 - Math.abs(((color.h / 60) % 2) - 1));
        double m = color.l - chroma / 2;
        int sector = (int) Math.floor((((color.h % 360) + 360) % 360) / 60);
        double[][] sectors = {{chroma, x, 0}, {x, chroma, 0}, {0, chroma, x}, {0, x, chroma}, {x, 0, chroma}, {chroma, 0, x}};
        double[] rgb = sectors[sector];
        return new Rgba((rgb[0] + m) * 255, (rgb[1] + m) * 255, (rgb[2] + m) * 255, alpha);
    }

    /** WCAG relative luminance */
    private static double luminance(Rgba color) {
        double[] channels = {color.r, color.g, color.b};
        for (int index = 0; index < 3; index++) {
            double value = channels[index] / 255;
            channels[index] = value <= 0.039_28 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
    }

    /** WCAG contrast ratio of two opaque colors */
    private static double contrast(Rgba first, Rgba second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    /** The color moved in lightness, away from the background, until it has the contrast (the hue and saturation stay) */
    private static Hsl readable(Hsl color, Rgba background, double minimum) {
        double step = luminance(background) > 0.5 ? -0.01 : 0.01;
        Hsl result = color;
        for (int index = 0; index < 100 && contrast(toRgb(result, ""), background) < minimum; index++) {
            result = new Hsl(result.h, result.s, Math.min(1, Math.max(0, result.l + step)));
        }
        return result;
    }

    /** Pure black or white, whichever reads better on the color */
    private static String readableText(Rgba background) {
        return contrast(rgb(DARK_TEXT), background) >= contrast(rgb(LIGHT_TEXT), background) ? DARK_TEXT : LIGHT_TEXT;
    }

    /** The setting as {@code #rrggbb} in lower case, or null when there is none or it is not a color (an opacity is dropped) */
    static @Nullable String normalize(@Nullable String value) {
        Rgba color = parse(value);
        return color == null ? null : hex(color, "");
    }

    /** Whether the accent is the one of the Islands themes, which leaves the theme as it is */
    static boolean isDefault(@Nullable String accent) {
        return accent == null || accent.equals(DEFAULT_ACCENT);
    }

    /** A part Islands paints with the accent: the accent, with the opacity of the part */
    static @NotNull String solid(@NotNull String base, @NotNull String accent) {
        return hex(rgb(accent), rgb(base).alpha);
    }

    /** A part Islands paints with a color derived from the accent */
    static @NotNull String tint(@NotNull String base, @NotNull String accent) {
        if (isDefault(accent)) return base.toLowerCase(Locale.ROOT);
        Rgba color = rgb(base);
        return hex(toRgb(tinted(color, accent), ""), color.alpha);
    }

    private static Hsl tinted(Rgba color, String accent) {
        Hsl hsl = toHsl(color);
        Hsl reference = toHsl(rgb(DEFAULT_ACCENT));
        Hsl target = toHsl(rgb(accent));
        double turn = target.h - reference.h;
        double scale = reference.s == 0 ? 1 : target.s / reference.s;
        return new Hsl((((hsl.h + turn) % 360) + 360) % 360, Math.min(1, hsl.s * scale), hsl.l);
    }

    /**
     * Text in the color of the accent (links, the matched letters of a list): a tint that stays as readable on the editor
     * as the Islands blue, or at least 4.5:1 (a green of the same lightness looks much lighter than a blue).
     */
    static @NotNull String text(@NotNull String base, @NotNull String accent, @NotNull String background) {
        if (isDefault(accent)) return base.toLowerCase(Locale.ROOT);
        Rgba color = rgb(base);
        Rgba editor = rgb(background);
        Hsl moved = readable(tinted(color, accent), editor, Math.min(MIN_CONTRAST, contrast(color, editor)));
        return hex(toRgb(moved, ""), color.alpha);
    }

    /** The text on the accent: kept if it reads (4.5:1), else black or white */
    static @NotNull String onAccent(@NotNull String foreground, @NotNull String accent) {
        if (isDefault(accent)) return foreground.toLowerCase(Locale.ROOT);
        Rgba background = rgb(accent);
        Rgba text = parse(foreground);
        if (text != null && contrast(text, background) >= MIN_CONTRAST) return foreground.toLowerCase(Locale.ROOT);
        return readableText(background);
    }

    /** A part of {@code accentTargets}: the accent itself, with the opacity of the part */
    static @NotNull String target(@NotNull String base, @NotNull String accent) {
        return hex(rgb(accent), rgb(base).alpha);
    }

    /** The accent with another opacity ({@code 80}: half), for a part that is always see-through */
    static @NotNull String withAlpha(@NotNull String accent, @NotNull String alpha) {
        return hex(rgb(accent), alpha);
    }

    /** The text on a part of a target: kept if it reads on the accent, else black or white */
    static @NotNull String onTarget(@NotNull String foreground, @NotNull String background) {
        Rgba fill = rgb(background);
        Rgba text = parse(foreground);
        if (!fill.alpha.isEmpty() || (text != null && contrast(text, fill) >= MIN_CONTRAST)) return foreground.toLowerCase(Locale.ROOT);
        return readableText(fill);
    }

    /** {@code #rrggbb[aa]} as a color of the IDE */
    static @NotNull Color toColor(@NotNull String value) {
        Rgba color = rgb(value);
        int alpha = color.alpha.isEmpty() ? 255 : Integer.parseInt(color.alpha, 16);
        return new Color((int) Math.round(color.r), (int) Math.round(color.g), (int) Math.round(color.b), alpha);
    }

    /** A color of the IDE as {@code #rrggbb[aa]} */
    static @NotNull String toHex(@NotNull Color color) {
        String rgb = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        return color.getAlpha() == 255 ? rgb : rgb + String.format("%02x", color.getAlpha());
    }
}
