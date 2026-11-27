package com.woogajjangka.coolthemekit;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The icons drawn again with the filters of the settings ({@link CoolIconFilters}): the folder color (the icons of
 * {@code folder-color.txt} of {@code coolicons} and of each other style, for {@code folderColor} or
 * {@code accentTargets: folders}), monochrome, opacity and saturation. The IDE loads an icon from a file, so each set of
 * filters gets its files in the system folder of the IDE ({@code <system>/cool-theme-kit/icons/<style>/<filters>/}), the
 * dark variant ({@code <x>_dark.svg}) next to the icon, where the IDE looks for it.
 */
final class CoolRecoloredIcons {
    private static final Logger LOG = Logger.getInstance(CoolRecoloredIcons.class);
    /** By icon style ({@link CoolIconsSettings#STYLES}) */
    private static final Map<String, Set<String>> LISTS = new ConcurrentHashMap<>();
    private static final Map<String, Icon> LOADED = new ConcurrentHashMap<>();

    private CoolRecoloredIcons() {
    }

    private static Set<String> readList(String root) {
        Set<String> icons = new HashSet<>();
        try (InputStream in = CoolRecoloredIcons.class.getResourceAsStream(root + "folder-color.txt")) {
            if (in == null) return icons;
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String icon = line.strip();
                if (!icon.isEmpty() && !icon.startsWith("#")) icons.add(icon);
            }
        } catch (IOException e) {
            LOG.warn("Cannot read " + root + "folder-color.txt", e);
        }
        return icons;
    }

    /** Whether the icon ({@code <dir>/<stem>}) of the style is drawn in the folder color */
    static boolean takesFolderColor(@NotNull String ref, @NotNull String style) {
        return LISTS.computeIfAbsent(style, key -> readList(CoolIcons.root(key))).contains(ref);
    }

    static void clear() {
        LOG.debug("forgetting " + LOADED.size() + " recolored icons");
        LOADED.clear();
    }

    /** The icon with the filters, or null when it cannot be written */
    static @Nullable Icon load(@NotNull String ref, @NotNull String style, @NotNull CoolIconFilters.Settings filters) {
        return LOADED.computeIfAbsent(style + "/" + filters.key() + "/" + ref, key -> write(ref, style, filters));
    }

    private static @Nullable Icon write(String ref, String style, CoolIconFilters.Settings filters) {
        try {
            Path folder = PathManager.getSystemDir().resolve("cool-theme-kit/icons/" + style + "/" + filters.key());
            Path file = folder.resolve(ref + ".svg");
            if (!writeFiltered(ref, ref, style, filters, file)) return null;
            writeFiltered(ref + "_dark", ref, style, filters, folder.resolve(ref + "_dark.svg"));
            return IconLoader.findIcon(file.toUri().toURL());
        } catch (IOException | RuntimeException e) {
            LOG.warn("Cannot draw " + ref + " with " + filters, e);
            return null;
        }
    }

    /** Writes the resource {@code variant} of the icon {@code ref} with the filters; false when there is no such resource */
    private static boolean writeFiltered(String variant, String ref, String style, CoolIconFilters.Settings filters, Path file) throws IOException {
        try (InputStream in = CoolRecoloredIcons.class.getResourceAsStream(CoolIcons.root(style) + variant + ".svg")) {
            if (in == null) return false;
            String svg = CoolIconFilters.apply(new String(in.readAllBytes(), StandardCharsets.UTF_8), ref, style, filters);
            byte[] bytes = svg.getBytes(StandardCharsets.UTF_8);
            if (!Files.isRegularFile(file) || !java.util.Arrays.equals(Files.readAllBytes(file), bytes)) {
                Files.createDirectories(file.getParent());
                Path temporary = Files.createTempFile(file.getParent(), "icon", ".tmp");
                Files.write(temporary, bytes);
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            return true;
        }
    }
}
