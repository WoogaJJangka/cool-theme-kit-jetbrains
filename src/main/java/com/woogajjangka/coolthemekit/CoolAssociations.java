package com.woogajjangka.coolthemekit;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The icons the user gives to names, as the VS Code settings {@code filesAssociations}, {@code foldersAssociations} and
 * {@code languagesAssociations} of Cool Theme Kit do (FileJsonGenerator, FolderJsonGenerator, LanguageJsonGenerator).
 *
 * <ul>
 *   <li>A file key {@code *.ext} or {@code **.ext} is an extension, any other key a whole file name; a folder key is a
 *       folder name, and gives the icon to {@code .name} and {@code _name} too; a language key is a language id.</li>
 *   <li>A value is the name of a bundled icon ({@code rust}, {@code docker}; the folder icon {@code tests}), in any case,
 *       or the path of an SVG file, absolute or from {@code ~}. A folder SVG {@code x.svg} is open as {@code x-open.svg}
 *       or {@code x_open.svg} next to it, when there is one.</li>
 *   <li>They win over the icons of the theme, but a file name of the theme still wins over an extension of the user, as
 *       the name comes first when a name is matched.</li>
 *   <li>A file name with a role ({@code main.py}) whose extension the user gives a bundled icon gets that icon with the
 *       mark of the role, unless the name has an icon of its own ({@code __init__.py} is the PyCharm Python icon): then it
 *       keeps that icon, without the mark.</li>
 * </ul>
 *
 * <p>Unlike VS Code, a value that is no bundled icon and no existing SVG file is left out (VS Code shows no icon at all),
 * and a role name whose mark VS Code draws over an icon the plugin has no marked version of gets the icon without the
 * mark (VS Code draws the marked icon when it generates the theme; the plugin has the marked icons of the theme only).
 */
final class CoolAssociations {
    /** No associations: the icons of the theme. */
    static final CoolAssociations NONE = new CoolAssociations(Map.of(), Map.of(), Map.of());

    /** The prefix of the icon of an SVG file of the user ({@link CoolIcons#load}). */
    static final String USER_FILE = "file:";

    private final Map<String, String> fileNames = new HashMap<>();
    private final Map<String, String> fileExtensions = new HashMap<>();
    private final Map<String, String> languageIds = new HashMap<>();
    private final Map<String, String> folderNames = new HashMap<>();
    private final Map<String, String> folderNamesExpanded = new HashMap<>();

    CoolAssociations(@NotNull Map<String, String> files, @NotNull Map<String, String> folders,
                     @NotNull Map<String, String> languages) {
        files.forEach((key, value) -> {
            String icon = fileIcon(value);
            if (key == null || key.isEmpty() || icon == null) return;
            String pattern = CoolIcons.normalize(key);
            if (pattern.startsWith("**.")) fileExtensions.put(pattern.substring(3), icon);
            else if (pattern.startsWith("*.")) fileExtensions.put(pattern.substring(2), icon);
            else fileNames.put(pattern, icon);
        });
        folders.forEach((key, value) -> {
            if (key == null || key.isEmpty()) return;
            String[] icons = folderIcons(value);
            if (icons == null) return;
            String name = CoolIcons.normalize(key);
            for (String variant : new String[]{name, "." + name, "_" + name}) {
                folderNames.put(variant, icons[0]);
                folderNamesExpanded.put(variant, icons[1]);
            }
        });
        languages.forEach((key, value) -> {
            String icon = fileIcon(value);
            if (key == null || key.isBlank() || icon == null) return;
            languageIds.put(CoolIcons.normalize(key), icon);
        });
    }

    boolean isEmpty() {
        return fileNames.isEmpty() && fileExtensions.isEmpty() && languageIds.isEmpty() && folderNames.isEmpty();
    }

    /**
     * The icon of a file from its name alone, or null: the name of the user, the name of the theme (a name with a role
     * under the extension of the user as described above), then the extensions from the longest, the user's first.
     */
    @Nullable String nameIcon(@NotNull String fileName, boolean roleMarks) {
        String name = CoolIcons.normalize(fileName);
        String icon = fileNames.get(name);
        if (icon != null) return icon;

        String role = CoolIcons.roleOf(name);
        if (role != null) {
            Extension extension = extension(name);
            if (extension != null && extension.ofUser) {
                String own = CoolIcons.roleOwnIcon(name);
                boolean bundled = !extension.icon.startsWith(USER_FILE);
                if (bundled && (own == null || own.equals(extension.icon))) {
                    if (!roleMarks) return extension.icon;
                    String marked = CoolIcons.bundledFileIcon("role-" + role + "-" + stem(extension.icon));
                    return marked != null ? marked : extension.icon;
                }
                return own != null ? own : extension.icon;
            }
        }

        icon = CoolIcons.fileNameIcon(name);
        if (icon != null) return roleMarks ? icon : CoolIcons.withoutRoleMark(icon);
        Extension extension = extension(name);
        return extension != null ? extension.icon : null;
    }

    /** The icon of a language id (VS Code or JetBrains), the user's first, or null. */
    @Nullable String languageIcon(@Nullable String languageId) {
        String id = CoolIcons.vscodeLanguageId(languageId);
        if (id == null) return null;
        String icon = languageIds.get(id);
        return icon != null ? icon : CoolIcons.languageIcon(id);
    }

    /** The icon of a folder from its name, the user's first, or the plain folder icon. */
    @Nullable String folderIcon(@NotNull String folderName, boolean expanded) {
        String icon = (expanded ? folderNamesExpanded : folderNames).get(CoolIcons.normalize(folderName));
        return icon != null ? icon : CoolIcons.folderIcon(folderName, expanded);
    }

    private record Extension(String icon, boolean ofUser) {
    }

    /** The icon of the longest extension of a name that has one, the user's before the theme's of the same extension. */
    private @Nullable Extension extension(String name) {
        for (int dot = name.indexOf('.'); dot >= 0; dot = name.indexOf('.', dot + 1)) {
            String key = name.substring(dot + 1);
            String icon = fileExtensions.get(key);
            if (icon != null) return new Extension(icon, true);
            icon = CoolIcons.fileExtensionIcon(key);
            if (icon != null) return new Extension(icon, false);
        }
        return null;
    }

    private static String stem(String icon) {
        return icon.substring(icon.indexOf('/') + 1);
    }

    /** A bundled file icon by name, or an SVG file of the user; null when it is neither. */
    private static @Nullable String fileIcon(@Nullable String value) {
        if (value == null || value.isBlank()) return null;
        if (isSvgPath(value)) {
            File file = userFile(value);
            return file != null ? USER_FILE + file.getPath() : null;
        }
        return CoolIcons.bundledFileIcon(value);
    }

    /** The closed and the open icon of a bundled folder icon by name or of an SVG file of the user, or null. */
    private static String @Nullable [] folderIcons(@Nullable String value) {
        if (value == null || value.isBlank()) return null;
        if (isSvgPath(value)) {
            File file = userFile(value);
            if (file == null) return null;
            String base = file.getPath().substring(0, file.getPath().length() - ".svg".length());
            File open = new File(base + "-open.svg");
            if (!open.isFile()) open = new File(base + "_open.svg");
            return new String[]{USER_FILE + file.getPath(), USER_FILE + (open.isFile() ? open : file).getPath()};
        }
        String closed = CoolIcons.bundledFolderIcon(value, false);
        if (closed == null) return null;
        String open = CoolIcons.bundledFolderIcon(value, true);
        return new String[]{closed, open != null ? open : closed};
    }

    static boolean isSvgPath(@NotNull String value) {
        return value.trim().toLowerCase(Locale.ROOT).endsWith(".svg");
    }

    /** The SVG file of a value, absolute or from {@code ~}, when it exists; a relative path has no sensible base. */
    static @Nullable File userFile(@NotNull String value) {
        String path = value.trim();
        File file;
        if (path.equals("~") || path.startsWith("~/") || path.startsWith("~\\")) {
            file = new File(System.getProperty("user.home"), path.substring(1));
        } else {
            file = new File(path);
            if (!file.isAbsolute()) return null;
        }
        return file.isFile() ? file.getAbsoluteFile() : null;
    }
}
