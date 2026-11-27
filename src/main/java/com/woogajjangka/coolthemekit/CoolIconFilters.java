package com.woogajjangka.coolthemekit;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The filters of the icons in the settings of Cool Theme Kit for VS Code: {@code folderColor} (or the accent),
 * {@code monochrome} with {@code monochromeColor}, {@code opacity} and {@code saturation}. VS Code writes every icon again
 * through them ({@code IconThemeGenerator.filterSvg}: the folder color, the style, then {@code MonochromeService},
 * {@code OpacityService}, {@code SaturationService}); the plugin does the same to the icons it ships, drawn with the
 * default settings. The folder color is put on the finished icon ({@link CoolFolderColor}), the others are ports of
 * those services, so {@code CoolIconFiltersTest} finds the same svg as VS Code.
 *
 * <p>Pure string work: no platform service is needed.
 */
final class CoolIconFilters {
    /**
     * The filters of the settings.
     *
     * @param folderColor     the color of the folders, null for the default one
     * @param monochromeColor the color of every icon ({@code monochrome}), null for none
     * @param opacity         1 for none
     * @param saturation      1 for none
     */
    record Settings(@Nullable String folderColor, @Nullable String monochromeColor, double opacity, double saturation) {
        static final Settings NONE = new Settings(null, null, 1, 1);

        /** Whether the icons are drawn as they are shipped */
        boolean isNone() {
            return folderColor == null && monochromeColor == null && opacity >= 1 && saturation >= 1;
        }

        /** A name for the files of these filters, the same for the same filters */
        @NotNull String key() {
            String text = folderColor + "|" + monochromeColor + "|" + opacity + "|" + saturation;
            return Integer.toHexString(text.hashCode());
        }
    }

    private CoolIconFilters() {
    }

    /** The icon {@code ref} of a style, its svg as shipped, drawn with the filters */
    static @NotNull String apply(@NotNull String svg, @NotNull String ref, @NotNull String style, @NotNull Settings settings) {
        if (settings.isNone()) return svg;
        String result = svg;
        if (settings.folderColor() != null && CoolRecoloredIcons.takesFolderColor(ref, style)) {
            result = CoolFolderColor.recolor(result, settings.folderColor(), style);
        }
        result = monochrome(result, settings.monochromeColor());
        result = opacity(result, settings.opacity());
        return saturation(result, settings.saturation());
    }

    // region MonochromeService

    private static final Map<String, String> NAMED_COLORS = Map.of("azure", "#f0ffff", "black", "#000000", "red", "#ff0000", "white", "#ffffff");
    private static final Pattern PAINT = Pattern.compile(
            "((?:fill|stroke|stop-color|flood-color|lighting-color)\\s*(?:=\\s*[\"']|:\\s*))(#[\\dA-Fa-f]{6}|#[\\dA-Fa-f]{3}|[a-z]+)\\b");
    private static final Pattern HEX_COLOR = Pattern.compile("^#([\\dA-Fa-f]{6}|[\\dA-Fa-f]{3})$");

    /**
     * Every paint in the color, as the ColorizeFilter of the JetBrains plugin: the hue of the color, its saturation and
     * brightness scaled by those of the paint.
     */
    static @NotNull String monochrome(@NotNull String svg, @Nullable String color) {
        if (color == null || !HEX_COLOR.matcher(color).matches()) return svg;
        double[] base = rgbToHsb(toRgb(color));
        Matcher paint = PAINT.matcher(svg);
        StringBuilder result = new StringBuilder();
        while (paint.find()) {
            String value = paint.group(2);
            String hex = value.startsWith("#") ? value : NAMED_COLORS.get(value.toLowerCase(Locale.ROOT));
            String replacement = paint.group();
            if (hex != null) {
                double[] hsb = rgbToHsb(toRgb(hex));
                replacement = paint.group(1) + hsbToHex(base[0], base[1] * hsb[1], base[2] * hsb[2]);
            }
            paint.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        paint.appendTail(result);
        return result.toString();
    }

    private static int[] toRgb(String hex) {
        String digits = hex.substring(1);
        if (digits.length() == 3) {
            StringBuilder full = new StringBuilder();
            for (char digit : digits.toCharArray()) full.append(digit).append(digit);
            digits = full.toString();
        }
        int value = Integer.parseInt(digits, 16);
        return new int[]{(value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF};
    }

    /** As java.awt.Color.RGBtoHSB, in doubles as Cool Theme Kit computes it */
    private static double[] rgbToHsb(int[] rgb) {
        int r = rgb[0];
        int g = rgb[1];
        int b = rgb[2];
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        double brightness = max / 255.0;
        double saturation = max == 0 ? 0 : (double) (max - min) / max;
        if (saturation == 0) return new double[]{0, 0, brightness};
        double rc = (double) (max - r) / (max - min);
        double gc = (double) (max - g) / (max - min);
        double bc = (double) (max - b) / (max - min);
        double hue;
        if (r == max) hue = bc - gc;
        else if (g == max) hue = 2 + rc - bc;
        else hue = 4 + gc - rc;
        hue /= 6;
        if (hue < 0) hue += 1;
        return new double[]{hue, saturation, brightness};
    }

    private static String channel(double value) {
        return String.format("%02x", Math.round(value * 255));
    }

    /** As java.awt.Color.HSBtoRGB, in doubles as Cool Theme Kit computes it */
    private static String hsbToHex(double hue, double saturation, double brightness) {
        double r = brightness;
        double g = brightness;
        double b = brightness;
        if (saturation != 0) {
            double h = (hue - Math.floor(hue)) * 6;
            double f = h - Math.floor(h);
            double p = brightness * (1 - saturation);
            double q = brightness * (1 - saturation * f);
            double t = brightness * (1 - saturation * (1 - f));
            double[][] sectors = {{brightness, t, p}, {q, brightness, p}, {p, brightness, t}, {p, q, brightness}, {t, p, brightness}, {brightness, p, q}};
            double[] sector = sectors[(int) Math.floor(h)];
            r = sector[0];
            g = sector[1];
            b = sector[2];
        }
        return "#" + channel(r) + channel(g) + channel(b);
    }

    // endregion

    // region OpacityService, SaturationService

    private static final Pattern ROOT = Pattern.compile("<svg[^>]*>");
    private static final Pattern OPACITY = Pattern.compile("\\sopacity=\"[\\d.]+\"");
    private static final Pattern FILTER = Pattern.compile("\\sfilter=\"[^\"]+?\"");
    private static final Pattern SATURATION_ELEMENT = Pattern.compile("<filter id=\"saturation\".+</filter>(.*</svg>)");

    /** The opacity on the root of the svg (the attribute taken out at 1) */
    static @NotNull String opacity(@NotNull String svg, double opacity) {
        Matcher root = ROOT.matcher(svg);
        if (!root.find()) return svg;
        String element = root.group();
        String updated;
        if (opacity < 1) {
            String attribute = " opacity=\"" + jsNumber(opacity) + "\"";
            Matcher existing = OPACITY.matcher(element);
            updated = existing.find() ? existing.replaceFirst(Matcher.quoteReplacement(attribute)) : attribute(element, attribute);
        } else {
            updated = OPACITY.matcher(element).replaceFirst("");
        }
        return svg.substring(0, root.start()) + updated + svg.substring(root.end());
    }

    /** The saturation: a filter on the root of the svg and its element before the end (both taken out at 1) */
    static @NotNull String saturation(@NotNull String svg, double saturation) {
        Matcher root = ROOT.matcher(svg);
        if (!root.find()) return svg;
        String element = root.group();
        boolean add = saturation < 1;
        String updated;
        if (add) {
            String attribute = " filter=\"url(#saturation)\"";
            Matcher existing = FILTER.matcher(element);
            updated = existing.find() ? existing.replaceFirst(Matcher.quoteReplacement(attribute)) : attribute(element, attribute);
        } else {
            updated = FILTER.matcher(element).replaceFirst("");
        }
        String result = svg.substring(0, root.start()) + updated + svg.substring(root.end());

        Matcher filter = SATURATION_ELEMENT.matcher(result);
        if (!add) return filter.find() ? filter.replaceFirst("$1") : result;
        String filterElement = "<filter id='saturation'><feColorMatrix type='saturate' values='" + jsNumber(saturation) + "'/></filter>";
        if (filter.find()) return filter.replaceFirst(Matcher.quoteReplacement(filterElement) + "$1");
        // before the end of the root: an svg can hold others (the glyph of a glass icon keeps its own viewBox)
        int end = result.lastIndexOf("</svg>");
        return end == -1 ? result : result.substring(0, end) + filterElement + result.substring(end);
    }

    /** The attribute right after {@code <svg}, where Cool Theme Kit puts it */
    private static String attribute(String root, String attribute) {
        return root.startsWith("<svg") ? "<svg" + attribute + root.substring(4) : root;
    }

    /** A number as JavaScript writes it ({@code 0.6}, {@code 1}, not {@code 1.0}) */
    static @NotNull String jsNumber(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    // endregion
}
