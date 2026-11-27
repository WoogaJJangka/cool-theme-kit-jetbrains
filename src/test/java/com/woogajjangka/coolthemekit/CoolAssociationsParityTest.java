package com.woogajjangka.coolthemekit;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The associations and the icon style give the icons VS Code gives with the same settings. The expected icons are those
 * of the theme Cool Theme Kit generates with sample associations ({@code coolthemekit/associations.tsv}, written by
 * {@code gradlew syncIcons} from {@code bun run export:jetbrains-icons}).
 */
public class CoolAssociationsParityTest {
    private record Fixture(CoolAssociations associations, List<String[]> files, List<String[]> folders) {
    }

    private static Fixture fixture() throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        Map<String, String> folders = new LinkedHashMap<>();
        Map<String, String> languages = new LinkedHashMap<>();
        List<String[]> fileCases = new ArrayList<>();
        List<String[]> folderCases = new ArrayList<>();
        try (InputStream in = CoolAssociationsParityTest.class.getResourceAsStream("/coolthemekit/associations.tsv")) {
            assertNotNull("coolthemekit/associations.tsv is missing: run `gradlew syncIcons`", in);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\t", -1);
                switch (parts[0]) {
                    case "setting" -> (switch (parts[1]) {
                        case "files" -> files;
                        case "folders" -> folders;
                        default -> languages;
                    }).put(parts[2], parts[3]);
                    case "file" -> fileCases.add(parts);
                    case "folder" -> folderCases.add(parts);
                    default -> throw new AssertionError("unknown line " + line);
                }
            }
        }
        return new Fixture(new CoolAssociations(files, folders, languages), fileCases, folderCases);
    }

    /**
     * What the plugin gives where VS Code gives an icon the plugin has not: no icon for a value that names none (the
     * plugin leaves the association out), the icon without its mark for a marked icon the theme has no file of.
     */
    private static String expected(String vscode, String withoutAssociation) {
        if (CoolIcons.isBundled(vscode)) return vscode;
        String unmarked = CoolIcons.withoutRoleMark(vscode);
        if (!unmarked.equals(vscode) && CoolIcons.isBundled(unmarked)) return unmarked;
        return withoutAssociation;
    }

    private static String fileIcon(CoolAssociations associations, String name, String language) {
        String icon = associations.nameIcon(name, true);
        if (icon == null && !language.isEmpty()) icon = associations.languageIcon(language);
        return icon != null ? icon : CoolIcons.defaultFileIcon();
    }

    @Test
    public void filesGetTheIconsOfVsCode() throws IOException {
        Fixture fixture = fixture();
        assertFalse(fixture.files().isEmpty());
        for (String[] file : fixture.files()) {
            String name = file[1];
            String language = file[2];
            String without = fileIcon(CoolAssociations.NONE, name, language);
            assertEquals(name + " " + language, expected(file[3], without), fileIcon(fixture.associations(), name, language));
        }
    }

    @Test
    public void foldersGetTheIconsOfVsCode() throws IOException {
        Fixture fixture = fixture();
        assertFalse(fixture.folders().isEmpty());
        for (String[] folder : fixture.folders()) {
            String name = folder[1];
            assertEquals(name, expected(folder[2], CoolIcons.folderIcon(name, false)), fixture.associations().folderIcon(name, false));
            assertEquals(name, expected(folder[3], CoolIcons.folderIcon(name, true)), fixture.associations().folderIcon(name, true));
        }
    }

    @Test
    public void anAssociationChangesTheIcon() throws IOException {
        // the fixture would pass with no associations at all if every sample kept its icon
        Fixture fixture = fixture();
        long changed = fixture.files().stream()
                .filter(file -> !fileIcon(fixture.associations(), file[1], file[2]).equals(fileIcon(CoolAssociations.NONE, file[1], file[2])))
                .count();
        assertTrue("only " + changed + " files change", changed >= 10);
    }

    @Test
    public void noAssociationsIsTheTheme() {
        for (String name : List.of("main.py", "__init__.py", "README.md", "x.d.ts", "Dockerfile", "unknown.zz")) {
            assertEquals(name, CoolIcons.nameIcon(name), CoolAssociations.NONE.nameIcon(name, true));
            assertEquals(name, CoolIcons.withoutRoleMark(CoolIcons.nameIcon(name)), CoolAssociations.NONE.nameIcon(name, false));
        }
        assertEquals(CoolIcons.folderIcon("src", true), CoolAssociations.NONE.folderIcon("src", true));
        assertEquals(CoolIcons.languageIcon("python"), CoolAssociations.NONE.languageIcon("Python"));
    }

    @Test
    public void iconNamesAreNotCaseSensitive() {
        CoolAssociations associations = new CoolAssociations(Map.of("*.zz", "TYPESCRIPT", "A.TXT", "Rust"), Map.of("Lab", "Docs"), Map.of());
        assertEquals("files/typeScript", associations.nameIcon("x.ZZ", true));
        assertEquals("files/rust", associations.nameIcon("a.txt", true));
        assertEquals("folders/docs", associations.folderIcon("_lab", false));
        assertEquals("foldersOpen/docs", associations.folderIcon(".LAB", true));
    }

    @Test
    public void svgFilesOfTheUser() throws IOException {
        Path folder = Files.createTempDirectory("cool-associations");
        try {
            Path file = Files.writeString(folder.resolve("mine.svg"), "<svg xmlns=\"http://www.w3.org/2000/svg\"/>");
            Path box = Files.writeString(folder.resolve("box.svg"), "<svg xmlns=\"http://www.w3.org/2000/svg\"/>");
            Path boxOpen = Files.writeString(folder.resolve("box-open.svg"), "<svg xmlns=\"http://www.w3.org/2000/svg\"/>");
            CoolAssociations associations = new CoolAssociations(
                    Map.of("*.mine", file.toString(), "*.gone", folder.resolve("gone.svg").toString(), "*.rel", "icons/mine.svg"),
                    Map.of("stuff", box.toString(), "things", file.toString()),
                    Map.of());
            assertEquals(CoolAssociations.USER_FILE + file.toFile().getAbsolutePath(), associations.nameIcon("a.mine", true));
            assertNull("a missing file is left out", associations.nameIcon("a.gone", true));
            assertNull("a relative path has no base", associations.nameIcon("a.rel", true));
            assertEquals(CoolAssociations.USER_FILE + boxOpen.toFile().getAbsolutePath(), associations.folderIcon("stuff", true));
            assertEquals("no open file: the same svg", CoolAssociations.USER_FILE + file.toFile().getAbsolutePath(), associations.folderIcon("things", true));
            // a role name under an svg of the user gets no mark
            CoolAssociations python = new CoolAssociations(Map.of("*.py", file.toString()), Map.of(), Map.of());
            assertEquals(CoolAssociations.USER_FILE + file.toFile().getAbsolutePath(), python.nameIcon("main.py", true));
        } finally {
            try (Stream<Path> paths = Files.walk(folder)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }

    @Test
    public void everyIconHasOneInEachStyle() throws IOException {
        // the sources (the tests run in the project folder): the resources may be in a jar
        Path glassRoot = Path.of("src/main/resources/coolicons");
        assertTrue(Files.isDirectory(glassRoot));
        for (String style : CoolIconsSettings.STYLES) {
            if (style.equals(CoolIconsSettings.GLASS)) continue;
            Path styleRoot = Path.of("src/main/resources/coolicons-" + style);
            assertTrue("coolicons-" + style + " is missing: run `gradlew syncIcons`", Files.isDirectory(styleRoot));
            int different = 0;
            int count = 0;
            try (Stream<Path> paths = Files.walk(glassRoot)) {
                for (Path path : paths.filter(path -> path.toString().endsWith(".svg")).toList()) {
                    Path other = styleRoot.resolve(glassRoot.relativize(path).toString());
                    assertTrue("no " + style + " " + other, Files.isRegularFile(other));
                    count++;
                    if (!Files.readString(path).equals(Files.readString(other))) different++;
                }
            }
            assertTrue(count > 1000);
            assertNotEquals("the " + style + " icons are drawn differently", 0, different);
        }
    }
}
