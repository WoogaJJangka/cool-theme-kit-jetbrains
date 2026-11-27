package com.woogajjangka.coolthemekit;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The folder color of Cool Theme Kit on an icon drawn in the glass style ({@code accentTargets: folders} in VS Code
 * paints it with the accent). In VS Code the setting recolors the plain folders and the icons drawn in the folder color
 * (most {@code .xxxignore} files), and the glass style derives every color of the icon from that color
 * ({@code glassFolder}, {@code glassFile} of {@code src/icons/glass/glassIcons.ts}). The icons copied from VS Code were
 * drawn with the default color, so recoloring one gives again the derived colors from the new one:
 * <ul>
 *   <li>a folder: the gradients of its back ({@code ctk-b}) and front ({@code ctk-f}), and the glyph of its badge
 *       ({@code ctk-k});</li>
 *   <li>a file: the gradient of its tile ({@code ctk-t}), its glyph ({@code ctk-k}) and the paint of the icon itself.</li>
 * </ul>
 * Which icons take the folder color is listed by {@code syncIcons} ({@code coolicons/folder-color.txt}).
 * {@code CoolFolderColorTest} compares the result with the icons VS Code draws for the same color.
 */
final class CoolFolderColor {
    /** The folder color of Cool Theme Kit by default ({@code DEFAULT_FOLDER_COLOR}) */
    static final String DEFAULT_COLOR = "#90a4ae";

    private static final String WHITE = "#ffffff";
    private static final String BLACK = "#000000";
    /** Above this luminance a tile gets a dark glyph instead of a white one */
    private static final double LIGHT_TILE = 0.45;

    private static final Pattern FLOOD = Pattern.compile("(<filter id=\"ctk-k\"[^>]*>\\s*<feFlood flood-color=\")([^\"]*)(\")");
    private static final Pattern STOP_COLOR = Pattern.compile("stop-color=\"[^\"]*\"");

    private CoolFolderColor() {
    }

    /** The icon drawn with {@code color} instead of the default folder color */
    static @NotNull String recolor(@NotNull String svg, @NotNull String color) {
        String to = color.toLowerCase(Locale.ROOT);
        if (svg.contains("id=\"ctk-b\"")) {
            // glassFolder: the back, the front, the glyph of a badge on the front
            String result = gradient(svg, "b", mix(to, BLACK, 0.16), mix(to, BLACK, 0.34));
            result = gradient(result, "f", mix(to, WHITE, 0.3), mix(to, BLACK, 0.04));
            return flood(result, inkOn(mix(to, WHITE, 0.14)));
        }
        if (svg.contains("id=\"ctk-t\"")) {
            // glassFile: the tile, the glyph, and the icon under the glyph filter painted in the folder color
            String result = gradient(svg, "t", mix(to, WHITE, 0.18), mix(to, BLACK, 0.16));
            result = flood(result, inkOn(to));
            return result.replaceAll("(?i)fill=\"" + DEFAULT_COLOR + "\"", "fill=\"" + to + "\"");
        }
        return svg;
    }

    /**
     * A flat icon drawn with {@code color} instead of the default folder color: VS Code paints the whole icon in it
     * ({@code FolderColorService}), so every paint of the default color takes the new one.
     */
    static @NotNull String recolorFlat(@NotNull String svg, @NotNull String color) {
        return svg.replaceAll("(?i)fill=\"" + DEFAULT_COLOR + "\"", "fill=\"" + color.toLowerCase(Locale.ROOT) + "\"");
    }

    /**
     * The icon of a style ({@link CoolIconsSettings#STYLES}) drawn with {@code color}: the glass icons derive their colors
     * from it, the flat and the glossy ones (the flat icon under the shading of the glass style) are painted in it.
     */
    static @NotNull String recolor(@NotNull String svg, @NotNull String color, @NotNull String style) {
        return CoolIconsSettings.GLASS.equals(style) ? recolor(svg, color) : recolorFlat(svg, color);
    }

    /** The stops of the gradient {@code ctk-<id>}, top to bottom */
    private static String gradient(String svg, String id, String... stops) {
        Matcher gradient = Pattern.compile("<linearGradient id=\"ctk-" + id + "\"[^>]*>.*?</linearGradient>", Pattern.DOTALL).matcher(svg);
        if (!gradient.find()) return svg;
        Matcher stop = STOP_COLOR.matcher(gradient.group());
        StringBuilder replaced = new StringBuilder();
        List<String> colors = List.of(stops);
        int index = 0;
        while (stop.find()) {
            String value = index < colors.size() ? colors.get(index) : null;
            index++;
            stop.appendReplacement(replaced, value == null ? Matcher.quoteReplacement(stop.group()) : "stop-color=\"" + value + "\"");
        }
        stop.appendTail(replaced);
        return svg.substring(0, gradient.start()) + replaced + svg.substring(gradient.end());
    }

    private static String flood(String svg, String color) {
        Matcher flood = FLOOD.matcher(svg);
        return flood.find() ? svg.substring(0, flood.start(2)) + color + svg.substring(flood.end(2)) : svg;
    }

    // the color helpers of glassIcons.ts (not those of the accent: no opacity, and these round each mix)

    private static int[] rgb(String hex) {
        String digits = hex.substring(1);
        if (digits.length() == 3) {
            StringBuilder full = new StringBuilder();
            for (char digit : digits.toCharArray()) full.append(digit).append(digit);
            digits = full.toString();
        }
        int value = Integer.parseInt(digits, 16);
        return new int[]{(value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF};
    }

    /** {@code amount} of the way from a color to another */
    static String mix(String from, String to, double amount) {
        int[] a = rgb(from);
        int[] b = rgb(to);
        StringBuilder hex = new StringBuilder("#");
        for (int index = 0; index < 3; index++) {
            long value = Math.max(0, Math.min(255, Math.round(a[index] + (b[index] - a[index]) * amount)));
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
    }

    private static double luminance(String hex) {
        double total = 0;
        double[] weights = {0.2126, 0.7152, 0.0722};
        int[] channels = rgb(hex);
        for (int index = 0; index < 3; index++) {
            double channel = channels[index] / 255.0;
            total += weights[index] * (channel <= 0.039_28 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4));
        }
        return total;
    }

    /** The color of a glyph on a tile of that color */
    static String inkOn(String tile) {
        return luminance(tile) > LIGHT_TILE ? mix(tile, BLACK, 0.74) : WHITE;
    }
}
