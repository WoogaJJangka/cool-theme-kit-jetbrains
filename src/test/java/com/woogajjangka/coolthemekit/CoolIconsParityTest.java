package com.woogajjangka.coolthemekit;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** What VS Code shows and the plugin did not: open folders, JetBrains language ids, role marks turned off. */
public class CoolIconsParityTest {
    private static List<String[]> mapping() throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (InputStream in = CoolIcons.class.getResourceAsStream("/coolicons/mapping.tsv")) {
            assertNotNull(in);
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isEmpty() && !line.startsWith("#")) rows.add(line.split("\t"));
            }
        }
        return rows;
    }

    @Test
    public void expandedFoldersGetTheOpenFolder() {
        assertEquals("foldersOpen/folder", CoolIcons.folderIcon("some-folder-without-an-icon", true));
        assertEquals("foldersOpen/folder-root", CoolIcons.rootFolderIcon(true));
        String closed = CoolIcons.folderIcon("src", false);
        String open = CoolIcons.folderIcon("src", true);
        assertTrue(closed, closed.startsWith("folders/"));
        assertTrue(open, open.startsWith("foldersOpen/"));
    }

    @Test
    public void everyFolderNameHasAnOpenIcon() throws Exception {
        List<String> closed = new ArrayList<>();
        List<String> open = new ArrayList<>();
        for (String[] row : mapping()) {
            if (row[0].equals("folderNames")) closed.add(row[1]);
            if (row[0].equals("folderNamesExpanded")) open.add(row[1]);
        }
        assertEquals(closed, open);
    }

    @Test
    public void jetbrainsLanguageIdsAreTranslated() {
        assertEquals(CoolIcons.languageIcon("shellscript"), CoolIcons.languageIcon("Shell Script"));
        assertEquals(CoolIcons.languageIcon("javascriptreact"), CoolIcons.languageIcon("JSX Harmony"));
        assertEquals(CoolIcons.languageIcon("typescriptreact"), CoolIcons.languageIcon("TypeScript JSX"));
        assertEquals(CoolIcons.languageIcon("restructuredtext"), CoolIcons.languageIcon("ReST"));
        assertEquals(CoolIcons.languageIcon("pip-requirements"), CoolIcons.languageIcon("Requirements"));
        assertEquals(CoolIcons.languageIcon("csharp"), CoolIcons.languageIcon("C#"));
        assertNotNull(CoolIcons.languageIcon("Shell Script"));
        // Same id in lower case: no entry needed.
        assertEquals(CoolIcons.languageIcon("python"), CoolIcons.languageIcon("Python"));
        // TextMate highlights files of many languages: not the language of a file.
        assertNull(CoolIcons.languageIcon("textmate"));
    }

    @Test
    public void withoutRoleMarksANameGetsWhatItsExtensionGives() throws Exception {
        int checked = 0;
        for (String[] row : mapping()) {
            if (!row[0].equals("fileNames") || !row[2].startsWith("files/role-")) continue;
            String name = row[1];
            int dot = name.indexOf('.');
            if (dot < 0) continue;
            String byExtension = CoolIcons.nameIcon("zz-not-a-known-name" + name.substring(dot));
            assertEquals(name, byExtension, CoolIcons.withoutRoleMark(row[2]));
            checked++;
        }
        assertTrue(checked > 20);
    }

    @Test
    public void iconsWithoutAMarkAreKept() {
        assertEquals("files/pycharm-python", CoolIcons.withoutRoleMark("files/pycharm-python"));
        assertEquals("files/pycharm-python", CoolIcons.withoutRoleMark("files/role-run-pycharm-python"));
        assertNotEquals(CoolIcons.fileIcon("main.py", null), CoolIcons.withoutRoleMark(CoolIcons.fileIcon("main.py", null)));
    }
}
