package com.woogajjangka.coolthemekit;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The Cool Icons names, read from {@code /coolicons/mapping.tsv} (written by {@code gradlew syncIcons}).
 *
 * <p>A name is matched the way VS Code matches a file icon theme, so it gets the icon Cool Theme Kit gives it there:
 * the whole file name, then its extensions from the longest ({@code d.ts} before {@code ts}), then the language, then the
 * default file icon. Names are compared in NFC and lower case, so a Korean name in NFD (macOS) matches too.
 *
 * <p>Pure lookups: no platform service is needed, so the tests run without an IDE.
 */
final class CoolIcons {
    private static final Logger LOG = Logger.getInstance(CoolIcons.class);
    private static final String ROOT = "/coolicons/";

    private static final Map<String, String> FILE_NAMES = new HashMap<>();
    private static final Map<String, String> FILE_EXTENSIONS = new HashMap<>();
    private static final Map<String, String> LANGUAGE_IDS = new HashMap<>();
    private static final Map<String, String> FOLDER_NAMES = new HashMap<>();
    private static final Map<String, String> FOLDER_NAMES_EXPANDED = new HashMap<>();
    private static final Map<String, String> DEFAULTS = new HashMap<>();
    /** Every bundled icon by its VS Code name in lower case, for the associations of the user ({@link CoolAssociations}). */
    private static final Map<String, String> FILE_ICONS = new HashMap<>();
    private static final Map<String, String> FOLDER_ICONS = new HashMap<>();
    private static final Map<String, String> FOLDER_ICONS_EXPANDED = new HashMap<>();
    /** The file names with a role ({@code main.py} -> {@code run}), and the icon of those that have one of their own. */
    private static final Map<String, String> ROLE_NAMES = new HashMap<>();
    private static final Map<String, String> ROLE_OWN = new HashMap<>();


    /**
     * The VS Code language id of a JetBrains language whose id is not the same in lower case. An empty value: VS Code has
     * no such language (TextMate is how the IDE highlights files of many languages, not a language of the file).
     */
    private static final Map<String, String> VSCODE_LANGUAGE_IDS = Map.ofEntries(
            Map.entry("shell script", "shellscript"),
            Map.entry("ecmascript 6", "javascript"),
            Map.entry("flow js", "javascript"),
            Map.entry("jsx harmony", "javascriptreact"),
            Map.entry("typescript jsx", "typescriptreact"),
            Map.entry("xhtml", "html"),
            Map.entry("json lines", "jsonl"),
            Map.entry("jsonlines", "jsonl"),
            Map.entry("rest", "restructuredtext"),
            Map.entry("requirements", "pip-requirements"),
            Map.entry("djangotemplate", "django-html"),
            Map.entry("jinja2", "jinja"),
            Map.entry("ignorelang", "ignore"),
            Map.entry("gitignore", "ignore"),
            Map.entry("gitexclude", "ignore"),
            Map.entry("http request", "http"),
            Map.entry("genericsql", "sql"),
            Map.entry("sql92", "sql"),
            Map.entry("c#", "csharp"),
            Map.entry("objectivec", "objective-c"),
            Map.entry("perl5", "perl"),
            Map.entry("hcl-terraform", "terraform"),
            Map.entry("textmate", ""));

    /** {@code files/role-run-pycharm-python}: the icon of a file under the mark of its role (Cool Theme Kit roles.ts). */
    private static final Pattern ROLE_MARK = Pattern.compile("^files/role-(?:run|package|test|config)-");

    static {
        load();
    }

    private CoolIcons() {
    }

    private static void load() {
        try (InputStream in = CoolIcons.class.getResourceAsStream(ROOT + "mapping.tsv")) {
            if (in == null) {
                LOG.warn("coolicons/mapping.tsv is missing: run `gradlew syncIcons`");
                return;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                String[] parts = line.split("\t", 3);
                if (parts.length != 3) continue;
                Map<String, String> target = switch (parts[0]) {
                    case "fileNames" -> FILE_NAMES;
                    case "fileExtensions" -> FILE_EXTENSIONS;
                    case "languageIds" -> LANGUAGE_IDS;
                    case "folderNames" -> FOLDER_NAMES;
                    case "folderNamesExpanded" -> FOLDER_NAMES_EXPANDED;
                    case "default" -> DEFAULTS;
                    case "fileIcon" -> FILE_ICONS;
                    case "folderIcon" -> FOLDER_ICONS;
                    case "folderIconExpanded" -> FOLDER_ICONS_EXPANDED;
                    case "roleName" -> ROLE_NAMES;
                    case "roleOwn" -> ROLE_OWN;
                    default -> null;
                };
                if (target != null) target.put(parts[1], parts[2]);
            }
        } catch (IOException e) {
            LOG.warn("Cannot read coolicons/mapping.tsv", e);
        }
    }

    static String normalize(@NotNull String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

    /** The icon of a file from its name alone (the whole name, then its extensions from the longest), or null. */
    static @Nullable String nameIcon(@NotNull String fileName) {
        String name = normalize(fileName);
        String icon = FILE_NAMES.get(name);
        if (icon != null) return icon;
        for (int dot = name.indexOf('.'); dot >= 0; dot = name.indexOf('.', dot + 1)) {
            icon = FILE_EXTENSIONS.get(name.substring(dot + 1));
            if (icon != null) return icon;
        }
        return null;
    }

    /** The icon of a language, from its VS Code id or its JetBrains id ({@code Shell Script}), or null. */
    static @Nullable String languageIcon(@Nullable String languageId) {
        String id = vscodeLanguageId(languageId);
        return id == null ? null : LANGUAGE_IDS.get(id);
    }

    /** The VS Code id of a language id, VS Code or JetBrains, in lower case; null for none. */
    static @Nullable String vscodeLanguageId(@Nullable String languageId) {
        if (languageId == null) return null;
        String id = normalize(languageId);
        id = VSCODE_LANGUAGE_IDS.getOrDefault(id, id);
        return id.isEmpty() ? null : id;
    }

    /** The icon of a whole file name in the theme ({@code name} normalized), or null. */
    static @Nullable String fileNameIcon(@NotNull String name) {
        return FILE_NAMES.get(name);
    }

    /** The icon of one extension in the theme ({@code d.ts}, normalized), or null. */
    static @Nullable String fileExtensionIcon(@NotNull String extension) {
        return FILE_EXTENSIONS.get(extension);
    }

    /** A bundled file icon by its VS Code name ({@code rust}, {@code typeScript}), in any case, or null. */
    static @Nullable String bundledFileIcon(@NotNull String name) {
        return FILE_ICONS.get(normalize(name.trim()));
    }

    /** A bundled folder icon by its VS Code name ({@code tests} is {@code folder-tests}), closed or open, or null. */
    static @Nullable String bundledFolderIcon(@NotNull String name, boolean expanded) {
        return (expanded ? FOLDER_ICONS_EXPANDED : FOLDER_ICONS).get(normalize(name.trim()));
    }

    /** Whether an icon ({@code files/rust}) is one of the plugin. */
    static boolean isBundled(@Nullable String ref) {
        return ref != null && (FILE_ICONS.containsValue(ref) || FOLDER_ICONS.containsValue(ref) || FOLDER_ICONS_EXPANDED.containsValue(ref));
    }

    /** The role of a file name ({@code run} for {@code main.py}, normalized), or null. */
    static @Nullable String roleOf(@NotNull String name) {
        return ROLE_NAMES.get(name);
    }

    /** The icon a name with a role has of its own, without the mark ({@code __init__.py}: PyCharm Python), or null. */
    static @Nullable String roleOwnIcon(@NotNull String name) {
        return ROLE_OWN.get(name);
    }

    static @Nullable String defaultFileIcon() {
        return DEFAULTS.get("file");
    }

    /** The icon of a file, from its name and (when known) its language id. */
    static @Nullable String fileIcon(@NotNull String fileName, @Nullable String languageId) {
        String icon = nameIcon(fileName);
        if (icon == null) icon = languageIcon(languageId);
        return icon != null ? icon : defaultFileIcon();
    }

    /**
     * The icon without the mark of a role ({@code roleMarks} off in VS Code): the icon of the file under the mark, the one
     * the name gets without it. Any other icon is returned as it is.
     */
    static @Nullable String withoutRoleMark(@Nullable String icon) {
        if (icon == null) return null;
        return ROLE_MARK.matcher(icon).replaceFirst("files/");
    }

    /** The icon of a folder from its name, or the plain folder icon; {@code expanded}: the icon of the open folder. */
    static @Nullable String folderIcon(@NotNull String folderName, boolean expanded) {
        String icon = (expanded ? FOLDER_NAMES_EXPANDED : FOLDER_NAMES).get(normalize(folderName));
        return icon != null ? icon : DEFAULTS.get(expanded ? "folderexpanded" : "folder");
    }

    static @Nullable String folderIcon(@NotNull String folderName) {
        return folderIcon(folderName, false);
    }

    static @Nullable String rootFolderIcon(boolean expanded) {
        return DEFAULTS.get(expanded ? "rootfolderexpanded" : "rootfolder"); // the keys are stored in lower case
    }

    static @Nullable String rootFolderIcon() {
        return rootFolderIcon(false);
    }

    /**
     * Loads {@code <dir>/<stem>.svg}; the IDE picks {@code <stem>_dark.svg} in a dark theme when there is one. An icon
     * drawn in the folder color is drawn again in the accent when {@code accentTargets} has {@code folders}.
     */
    static @Nullable Icon load(@Nullable String ref) {
        if (ref == null) return null;
        if (ref.startsWith(CoolAssociations.USER_FILE)) return loadUserFile(ref.substring(CoolAssociations.USER_FILE.length()));
        String style = CoolIconsSettings.iconStyle();
        String folderColor = CoolIconsSettings.folderColor();
        // each style lists the icons it draws in the folder color (folder-color.txt of coolicons and coolicons-flat)
        if (folderColor != null && CoolRecoloredIcons.takesFolderColor(ref, style)) {
            Icon recolored = CoolRecoloredIcons.load(ref, folderColor, style);
            if (recolored != null) return recolored;
        }
        return IconLoader.findIcon(root(style) + ref + ".svg", CoolIcons.class.getClassLoader());
    }

    /**
     * The resources of an icon style ({@link CoolIconsSettings#STYLES}): {@code coolicons/} for glass, the default, and
     * {@code coolicons-<style>/} for the others (the same names, drawn otherwise; copied by {@code syncIcons}).
     */
    static @NotNull String root(@NotNull String style) {
        return CoolIconsSettings.GLASS.equals(style) ? ROOT : "/coolicons-" + style + "/";
    }

    /**
     * An SVG file of the user, at the size of the icons of the theme (VS Code draws every icon of a theme at the same size,
     * whatever the size of its svg). The same file in dark and light themes, as in VS Code.
     */
    private static @Nullable Icon loadUserFile(@NotNull String path) {
        try {
            Icon icon = IconLoader.findIcon(new java.io.File(path).toURI().toURL(), true);
            Icon reference = IconLoader.findIcon(ROOT + defaultFileIcon() + ".svg", CoolIcons.class.getClassLoader());
            if (icon == null || reference == null || icon.getIconWidth() <= 0) return icon;
            return com.intellij.util.IconUtil.scaleByIconWidth(icon, null, reference);
        } catch (java.net.MalformedURLException e) {
            LOG.warn("Cannot load " + path, e);
            return null;
        }
    }

    /** Forgets the icons drawn in another folder color (the accent changed) */
    static void clearRecolored() {
        CoolRecoloredIcons.clear();
    }

    /** A folder icon that is the open folder while its row is expanded in a tree ({@link CoolFolderIcon}). */
    static @Nullable Icon loadFolder(@Nullable String closedRef, @Nullable String openRef) {
        Icon closed = load(closedRef);
        if (closed == null) return null;
        Icon open = load(openRef);
        return open == null || open.equals(closed) ? closed : new CoolFolderIcon(closed, open);
    }
}
