package com.woogajjangka.coolthemekit;

import org.junit.Test;

import java.io.InputStream;
import java.text.Normalizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Name matching follows VS Code, so a name gets the icon Cool Theme Kit gives it there. */
public class CoolIconsTest {
    @Test
    public void fileNameWinsOverExtension() {
        assertEquals("files/role-run-pycharm-python", CoolIcons.fileIcon("main.py", null));
        assertEquals("files/role-package-pycharm-python", CoolIcons.fileIcon("__init__.py", null));
        assertEquals("files/pycharm-python", CoolIcons.fileIcon("model.py", null));
    }

    @Test
    public void namesAreCaseInsensitive() {
        assertEquals(CoolIcons.fileIcon("main.py", null), CoolIcons.fileIcon("MAIN.PY", null));
        assertEquals(CoolIcons.folderIcon("src"), CoolIcons.folderIcon("Src"));
    }

    @Test
    public void longestExtensionFirst() {
        String declaration = CoolIcons.fileIcon("index.d.ts", null);
        String typescript = CoolIcons.fileIcon("index.ts", null);
        assertNotNull(declaration);
        assertNotNull(typescript);
        org.junit.Assert.assertNotEquals(declaration, typescript);
    }

    @Test
    public void dotFilesMatchTheirExtension() {
        assertNotNull(CoolIcons.fileIcon(".gitignore", null));
        org.junit.Assert.assertNotEquals("files/file", CoolIcons.fileIcon(".gitignore", null));
    }

    @Test
    public void languageThenDefault() {
        assertEquals("files/pycharm-python", CoolIcons.fileIcon("no-extension", "Python"));
        assertEquals("files/file", CoolIcons.fileIcon("no-extension", null));
        assertEquals("folders/folder", CoolIcons.folderIcon("some-folder-without-an-icon"));
        assertEquals("folders/folder-root", CoolIcons.rootFolderIcon());
    }

    @Test
    public void koreanNamesInNfdMatch() {
        String nfd = Normalizer.normalize("한글.py", Normalizer.Form.NFD);
        assertEquals(CoolIcons.fileIcon("한글.py", null), CoolIcons.fileIcon(nfd, null));
    }

    @Test
    public void namesWithoutACoolIconGetTheOneOfMaterialIconTheme() throws Exception {
        // iconThemeLayers by default: Material Icon Theme fills the names Cool Icons has no icon for, never the others
        assertEquals("layers/material-icon-theme_xml", CoolIcons.nameIcon("Strings.resx"));
        assertTrue(CoolIcons.folderIcon("-rust").startsWith(CoolIcons.LAYERS));
        assertFalse("a name of Cool Icons keeps its icon", CoolIcons.nameIcon("x.py").startsWith(CoolIcons.LAYERS));
        assertNotNull("the license of Material Icon Theme ships with its icons",
                CoolIcons.class.getResource("/coolicons/layers/LICENSE-material-icon-theme.txt"));
        long layered;
        try (InputStream in = CoolIcons.class.getResourceAsStream("/coolicons/mapping.tsv")) {
            assertNotNull(in);
            layered = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines()
                    .filter(line -> line.contains("\t" + CoolIcons.LAYERS)).count();
        }
        assertTrue(layered + " names from other themes", layered > 1000);
    }

    @Test
    public void everyIconOfTheMappingIsBundled() throws Exception {
        try (InputStream in = CoolIcons.class.getResourceAsStream("/coolicons/mapping.tsv")) {
            assertNotNull(in);
            for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("roleName\t")) continue; // a role, not an icon
                String ref = line.split("\t")[2];
                assertNotNull(ref, CoolIcons.class.getResource("/coolicons/" + ref + ".svg"));
            }
        }
    }
}
