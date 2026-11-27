package com.woogajjangka.coolthemekit;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** The Cool Theme Kit settings of a VS Code settings.json become the settings of the plugin. */
public class CoolVsCodeSettingsTest {
    private static final String SETTINGS = """
            {
                // comments, as VS Code writes them
                "workbench.iconTheme": "cool-theme-kit-icons",
                /* a block comment, with "quotes" */
                "a-file-icon-vscode.iconStyle": "glossy",
                "a-file-icon-vscode.roleMarks": false,
                "a-file-icon-vscode.filesAssociations": {
                    // a comment inside
                    "*.hwp": "~/.a-file-icon/hwp.svg",
                    "be.json": "json", // after a value
                    "url//not-a-comment": "a // b",
                },
                "a-file-icon-vscode.foldersAssociations": { "candles": "charts" },
                "a-file-icon-vscode.folderColor": "#e06c75",
                "a-file-icon-vscode.monochrome": true,
                "a-file-icon-vscode.opacity": 0.6,
                "a-file-icon-vscode.accentColor": "#79cb60",
                "a-file-icon-vscode.accentTargets": ["folders", "tabs", 3],
                "a-file-icon-vscode.iconThemeLayers": [],
                "a-file-icon-vscode.folderTheme": "classic",
                "settingsSync.ignoredSettings": ["a-file-icon-vscode.saturation"],
            }
            """;

    @Test
    public void readsTheSettingsOfCoolThemeKit() {
        CoolIconsSettings.State state = CoolVsCodeSettings.read(SETTINGS);
        assertEquals("glossy", state.iconStyle);
        assertFalse(state.roleMarks);
        assertEquals(Map.of("*.hwp", "~/.a-file-icon/hwp.svg", "be.json", "json", "url//not-a-comment", "a // b"), state.filesAssociations);
        assertEquals(Map.of("candles", "charts"), state.foldersAssociations);
        assertEquals("#e06c75", state.folderColor);
        assertTrue(state.monochrome);
        assertEquals(0.6, state.opacity, 0);
        assertEquals("#79cb60", state.accentColor);
        assertEquals(List.of("folders", "tabs"), state.accentTargets);
        assertFalse("an empty iconThemeLayers has no layer", state.iconThemeLayers);
        assertEquals("classic", state.folderTheme);
    }

    @Test
    public void aSettingVsCodeDoesNotHaveIsTheDefault() {
        CoolIconsSettings.State state = CoolVsCodeSettings.read(SETTINGS);
        CoolIconsSettings.State defaults = new CoolIconsSettings.State();
        assertEquals("a name in a list is no setting", defaults.saturation, state.saturation, 0);
        assertEquals(defaults.monochromeColor, state.monochromeColor);
        assertEquals(defaults.hideFileIcons, state.hideFileIcons);
        assertTrue(state.languagesAssociations.isEmpty());

        CoolIconsSettings.State empty = CoolVsCodeSettings.read("{}");
        assertEquals(defaults.iconStyle, empty.iconStyle);
        assertTrue("Material Icon Theme is the default layer", empty.iconThemeLayers);
        assertTrue(CoolVsCodeSettings.read("{\"a-file-icon-vscode.iconThemeLayers\": [{\"theme\": \"material-icon-theme\", \"files\": \"override\"}]}").iconThemeLayers);
    }

    @Test
    public void notASettingsFile() {
        assertThrows(IllegalArgumentException.class, () -> CoolVsCodeSettings.read("[1, 2]"));
        assertThrows(IllegalArgumentException.class, () -> CoolVsCodeSettings.read("{ \"a\": "));
    }

    @Test
    public void classicFoldersKeepOnlyTheAssociationsOfTheUser() {
        CoolAssociations associations = new CoolAssociations(Map.of(), Map.of("candles", "charts"), Map.of());
        assertEquals("folders/charts", associations.folderIcon("candles", false, false));
        assertEquals(CoolIcons.defaultFolderIcon(false), associations.folderIcon("src", false, false));
        assertEquals(CoolIcons.defaultFolderIcon(true), associations.folderIcon("src", true, false));
        assertEquals("specific: the icon of the name", CoolIcons.folderIcon("src", false), associations.folderIcon("src", false, true));
    }
}
