package com.woogajjangka.coolthemekit;

import com.google.gson.JsonObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The folder color: the same icons change as in VS Code, into the same SVG. {@code coolthemekit/folder-color.json} has
 * each icon VS Code draws with the default folder color and with a probe color ({@code bun run export:jetbrains}).
 */
public class CoolFolderColorTest {
    private static void sameIconsAsVsCode(String style) throws Exception {
        JsonObject fixture = CoolAccentTest.fixture("folder-color.json");
        assertEquals(CoolFolderColor.DEFAULT_COLOR, fixture.get("defaultColor").getAsString());
        String probe = fixture.get("probeColor").getAsString();
        JsonObject icons = fixture.getAsJsonObject(CoolIconsSettings.GLASS.equals(style) ? "icons" : style);
        assertNotNull(style, icons);
        assertTrue(icons.size() > 30);

        Set<String> listed = new TreeSet<>();
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, com.google.gson.JsonElement> entry : icons.entrySet()) {
            String icon = entry.getKey();
            listed.add(icon);
            assertTrue(icon, CoolRecoloredIcons.takesFolderColor(icon, style));
            String shipped;
            try (InputStream in = CoolFolderColorTest.class.getResourceAsStream(CoolIcons.root(style) + icon + ".svg")) {
                assertNotNull(icon, in);
                shipped = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            JsonObject svgs = entry.getValue().getAsJsonObject();
            assertEquals(icon, svgs.get("default").getAsString(), shipped);
            String recolored = CoolFolderColor.recolor(shipped, probe, style);
            if (!recolored.equals(svgs.get("probe").getAsString())) wrong.add(icon);
        }
        assertEquals(wrong.toString(), 0, wrong.size());
        assertTrue(listed.contains("folders/folder"));
        assertTrue(listed.contains("foldersOpen/folder"));
    }

    @Test
    public void sameGlassIconsAsVsCode() throws Exception {
        sameIconsAsVsCode(CoolIconsSettings.GLASS);
    }

    @Test
    public void sameFlatIconsAsVsCode() throws Exception {
        sameIconsAsVsCode(CoolIconsSettings.FLAT);
    }

    @Test
    public void sameGlossyIconsAsVsCode() throws Exception {
        sameIconsAsVsCode(CoolIconsSettings.GLOSSY);
    }

    @Test
    public void otherIconsKeepTheirColor() {
        for (String style : CoolIconsSettings.STYLES) {
            assertTrue(!CoolRecoloredIcons.takesFolderColor("folders/src", style));
            assertTrue(!CoolRecoloredIcons.takesFolderColor("files/python", style));
            assertTrue(!CoolRecoloredIcons.takesFolderColor("folders/folder-root", style));
        }
    }

    @Test
    public void lightColorsGetADarkGlyph() {
        // the glyph of a file tile is white, dark on a light tile (the yellow accent)
        assertEquals("#ffffff", CoolFolderColor.inkOn("#3871e1"));
        assertEquals(CoolFolderColor.mix("#ffd866", "#000000", 0.74), CoolFolderColor.inkOn("#ffd866"));
    }
}
