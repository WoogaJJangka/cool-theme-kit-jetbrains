package com.woogajjangka.coolthemekit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The settings of Cool Theme Kit in VS Code ({@code a-file-icon-vscode.*} of a {@code settings.json}), read into the
 * settings of the plugin, so both editors show the same icons and accent without typing them twice. A setting VS Code
 * does not have gets its default value, as in VS Code.
 *
 * <p>Pure: reads text, no platform service.
 */
final class CoolVsCodeSettings {
    private static final String PREFIX = "a-file-icon-vscode.";

    private CoolVsCodeSettings() {
    }

    /** The user settings of VS Code (the default profile) on this system */
    static @NotNull Path defaultPath() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return Path.of(appData != null ? appData : home + "\\AppData\\Roaming", "Code", "User", "settings.json");
        }
        if (os.contains("mac")) return Path.of(home, "Library", "Application Support", "Code", "User", "settings.json");
        String config = System.getenv("XDG_CONFIG_HOME");
        return Path.of(config != null && !config.isEmpty() ? config : home + "/.config", "Code", "User", "settings.json");
    }

    /**
     * The settings of the plugin that a VS Code {@code settings.json} gives (JSON with comments and trailing commas).
     *
     * @throws IllegalArgumentException when the text is no JSON object
     */
    static @NotNull CoolIconsSettings.State read(@NotNull String jsonc) {
        JsonElement root;
        try {
            root = JsonParser.parseString(withoutComments(jsonc));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("not a settings.json: " + e.getMessage(), e);
        }
        if (!root.isJsonObject()) throw new IllegalArgumentException("not a settings.json: no object");
        JsonObject json = root.getAsJsonObject();
        CoolIconsSettings.State state = new CoolIconsSettings.State();

        state.iconStyle = string(json, "iconStyle", state.iconStyle);
        state.roleMarks = bool(json, "roleMarks", state.roleMarks);
        state.filesAssociations = map(json, "filesAssociations");
        state.foldersAssociations = map(json, "foldersAssociations");
        state.languagesAssociations = map(json, "languagesAssociations");
        state.folderColor = string(json, "folderColor", state.folderColor);
        state.folderTheme = string(json, "folderTheme", state.folderTheme);
        state.hideFileIcons = bool(json, "hideFileIcons", state.hideFileIcons);
        state.monochrome = bool(json, "monochrome", state.monochrome);
        state.monochromeColor = string(json, "monochromeColor", state.monochromeColor);
        state.opacity = number(json, "opacity", state.opacity);
        state.saturation = number(json, "saturation", state.saturation);
        state.accentColor = string(json, "accentColor", state.accentColor);
        state.accentTargets = list(json, "accentTargets");
        JsonElement layers = json.get(PREFIX + "iconThemeLayers");
        if (layers != null) state.iconThemeLayers = hasMaterialIconTheme(layers);
        return state;
    }

    /** {@code iconThemeLayers}: the plugin has the layer of Material Icon Theme, its default, or none */
    private static boolean hasMaterialIconTheme(JsonElement layers) {
        if (!layers.isJsonArray()) return true; // VS Code takes a value that is no list as the default
        for (JsonElement layer : layers.getAsJsonArray()) {
            JsonElement theme = layer.isJsonObject() ? layer.getAsJsonObject().get("theme") : layer;
            if (theme instanceof JsonPrimitive primitive && primitive.isString() && "material-icon-theme".equals(primitive.getAsString().trim())) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable JsonPrimitive primitive(JsonObject json, String key) {
        JsonElement value = json.get(PREFIX + key);
        return value instanceof JsonPrimitive primitive ? primitive : null;
    }

    private static String string(JsonObject json, String key, String fallback) {
        JsonPrimitive value = primitive(json, key);
        return value != null && value.isString() ? value.getAsString() : fallback;
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        JsonPrimitive value = primitive(json, key);
        return value != null && value.isBoolean() ? value.getAsBoolean() : fallback;
    }

    private static double number(JsonObject json, String key, double fallback) {
        JsonPrimitive value = primitive(json, key);
        return value != null && value.isNumber() ? value.getAsDouble() : fallback;
    }

    private static Map<String, String> map(JsonObject json, String key) {
        Map<String, String> result = new LinkedHashMap<>();
        JsonElement value = json.get(PREFIX + key);
        if (value == null || !value.isJsonObject()) return result;
        for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
            if (entry.getValue() instanceof JsonPrimitive primitive && primitive.isString()) result.put(entry.getKey(), primitive.getAsString());
        }
        return result;
    }

    private static List<String> list(JsonObject json, String key) {
        List<String> result = new ArrayList<>();
        JsonElement value = json.get(PREFIX + key);
        if (!(value instanceof JsonArray array)) return result;
        for (JsonElement item : array) {
            if (item instanceof JsonPrimitive primitive && primitive.isString()) result.add(primitive.getAsString());
        }
        return result;
    }

    /** JSON with comments ({@code //}, {@code /* *}{@code /}) and trailing commas made plain JSON, strings kept as they are */
    static @NotNull String withoutComments(@NotNull String text) {
        StringBuilder out = new StringBuilder(text.length());
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (c == '"') {
                int end = i + 1;
                while (end < length && text.charAt(end) != '"') end += text.charAt(end) == '\\' ? 2 : 1;
                out.append(text, i, Math.min(end + 1, length));
                i = end;
            } else if (c == '/' && i + 1 < length && text.charAt(i + 1) == '/') {
                while (i < length && text.charAt(i) != '\n') i++;
                out.append('\n');
            } else if (c == '/' && i + 1 < length && text.charAt(i + 1) == '*') {
                int end = text.indexOf("*/", i + 2);
                i = end < 0 ? length : end + 1;
                out.append(' ');
            } else if (c == ',') {
                int next = i + 1;
                while (next < length && Character.isWhitespace(text.charAt(next))) next++;
                // a comment between the comma and the end is removed on its own; only a comma right before } or ] goes
                if (next < length && (text.charAt(next) == '}' || text.charAt(next) == ']')) continue;
                if (next + 1 < length && text.charAt(next) == '/' && (text.charAt(next + 1) == '/' || text.charAt(next + 1) == '*')
                        && closesAfterComments(text, next)) {
                    continue;
                }
                out.append(c);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Whether only comments and spaces come before the next {@code }} or {@code ]} from {@code index} */
    private static boolean closesAfterComments(String text, int index) {
        int i = index;
        int length = text.length();
        while (i < length) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '/' && i + 1 < length && text.charAt(i + 1) == '/') {
                while (i < length && text.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < length && text.charAt(i + 1) == '*') {
                int end = text.indexOf("*/", i + 2);
                if (end < 0) return false;
                i = end + 2;
            } else {
                return c == '}' || c == ']';
            }
        }
        return false;
    }
}
