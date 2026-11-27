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
 * The icons drawn in the folder color ({@code folder-color.txt} of {@code coolicons} and {@code coolicons-flat}), drawn
 * again in another color ({@link CoolFolderColor}) for {@code accentTargets: folders}. The IDE loads an icon from a
 * file, so each color gets its files in the system folder of the IDE
 * ({@code <system>/cool-theme-kit/folder-color/<glass|flat>/<rrggbb>/}).
 */
final class CoolRecoloredIcons {
    private static final Logger LOG = Logger.getInstance(CoolRecoloredIcons.class);
    private static final Set<String> GLASS = readList("/coolicons/");
    private static final Set<String> FLAT = readList("/coolicons-flat/");
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
    static boolean takesFolderColor(@NotNull String ref, boolean flat) {
        return (flat ? FLAT : GLASS).contains(ref);
    }

    static void clear() {
        LOG.debug("forgetting " + LOADED.size() + " recolored icons");
        LOADED.clear();
    }

    /** The icon in the color ({@code #rrggbb}), or null when it cannot be written */
    static @Nullable Icon load(@NotNull String ref, @NotNull String color, boolean flat) {
        String style = flat ? "flat" : "glass";
        return LOADED.computeIfAbsent(style + "/" + color + "/" + ref, key -> write(ref, color, flat));
    }

    private static @Nullable Icon write(String ref, String color, boolean flat) {
        String root = flat ? "/coolicons-flat/" : "/coolicons/";
        try (InputStream in = CoolRecoloredIcons.class.getResourceAsStream(root + ref + ".svg")) {
            if (in == null) return null;
            String original = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String svg = flat ? CoolFolderColor.recolorFlat(original, color) : CoolFolderColor.recolor(original, color);
            String folder = "cool-theme-kit/folder-color/" + (flat ? "flat/" : "glass/") + color.substring(1) + "/";
            Path file = PathManager.getSystemDir().resolve(folder + ref + ".svg");
            byte[] bytes = svg.getBytes(StandardCharsets.UTF_8);
            if (!Files.isRegularFile(file) || !java.util.Arrays.equals(Files.readAllBytes(file), bytes)) {
                Files.createDirectories(file.getParent());
                Path temporary = Files.createTempFile(file.getParent(), "icon", ".tmp");
                Files.write(temporary, bytes);
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            return IconLoader.findIcon(file.toUri().toURL());
        } catch (IOException | RuntimeException e) {
            LOG.warn("Cannot draw " + ref + " in " + color, e);
            return null;
        }
    }
}
