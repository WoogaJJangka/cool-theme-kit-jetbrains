package com.woogajjangka.coolthemekit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The accent gives the colors VS Code gives: {@code coolthemekit/accent.json} is written by Cool Theme Kit from its
 * {@code accentColors} ({@code bun run export:jetbrains}, copied by {@code gradlew syncIcons}).
 */
public class CoolAccentTest {
    static JsonObject fixture(String name) throws Exception {
        try (InputStream in = CoolAccentTest.class.getResourceAsStream("/coolthemekit/" + name)) {
            assertNotNull("run gradlew syncIcons", in);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    public void sameColorsAsVsCode() throws Exception {
        JsonObject fixture = fixture("accent.json");
        assertEquals(CoolAccent.DEFAULT_ACCENT, fixture.get("defaultAccent").getAsString());
        List<String> wrong = new ArrayList<>();
        int count = 0;
        for (JsonElement element : fixture.getAsJsonArray("cases")) {
            JsonObject c = element.getAsJsonObject();
            String accent = c.get("accent").getAsString();
            String base = c.get("base").getAsString();
            String expected = c.get("expected").getAsString();
            String actual = switch (c.get("kind").getAsString()) {
                case "tint" -> CoolAccent.tint(base, accent);
                case "text" -> CoolAccent.text(base, accent, c.get("background").getAsString());
                case "onAccent" -> CoolAccent.onAccent(base, accent);
                case "target" -> CoolAccent.target(base, accent);
                default -> throw new IllegalArgumentException(c.toString());
            };
            count++;
            if (!expected.equals(actual)) wrong.add(c + " -> " + actual);
        }
        assertTrue(count > 800);
        assertEquals(String.join("\n", wrong), 0, wrong.size());
    }

    @Test
    public void samePresetsAsVsCode() throws Exception {
        JsonObject fixture = fixture("accent.json");
        List<String> expected = new ArrayList<>();
        for (JsonElement element : fixture.getAsJsonArray("presets")) {
            JsonObject preset = element.getAsJsonObject();
            String source = preset.has("source") ? preset.get("source").getAsString() : null;
            expected.add(preset.get("id").getAsString() + " " + preset.get("color").getAsString() + " " + source);
        }
        List<String> actual = CoolAccent.PRESETS.stream().map(p -> p.id() + " " + p.color() + " " + p.source()).toList();
        assertEquals(expected, actual);
    }

    @Test
    public void editorBackgroundsOfVsCode() throws Exception {
        JsonObject backgrounds = fixture("accent.json").getAsJsonObject("editorBackground");
        assertEquals(backgrounds.get("dark").getAsString(), CoolThemeCustomizer.THEMES.get("Islands Dark").editorBackground());
        assertEquals(backgrounds.get("light").getAsString(), CoolThemeCustomizer.THEMES.get("Islands Light").editorBackground());
    }

    @Test
    public void colorsOfTheIde() {
        assertEquals("#3871e1", CoolAccent.toHex(CoolAccent.toColor("#3871E1")));
        assertEquals("#2f5eb9b3", CoolAccent.toHex(CoolAccent.toColor("#2f5eb9b3")));
        assertEquals("#79cb60", CoolAccent.normalize(" #79CB60 "));
        assertEquals("#79cb60", CoolAccent.normalize("#79cb6080"));
        assertEquals(null, CoolAccent.normalize("green"));
    }

    /** The named colors of the Islands themes of the IDE the plugin is built against */
    private static Map<String, String> palette(String theme) {
        return CoolThemeCustomizer.readPalette(CoolThemeCustomizer.THEMES.get(theme).file());
    }

    @Test
    public void theAccentReplacesTheNamesOfTheBlueOfIslands() {
        for (String theme : CoolThemeCustomizer.THEMES.keySet()) {
            Map<String, String> palette = palette(theme);
            String editor = CoolThemeCustomizer.THEMES.get(theme).editorBackground();
            Map<String, String> colors = CoolThemeCustomizer.colors(palette, "#79cb60", List.of(), editor);
            // the accent itself: buttons, focus ring, progress bar, the line of the selected tool window tab
            assertEquals(theme, "#79cb60", colors.get("accent-brand-bg"));
            assertEquals(theme, "#79cb60", colors.get("accent-brand-border"));
            // tints: the selection, the selected tab, links (text: readable on the editor)
            for (String name : List.of("selection-bg-active", "tab-selected-bg-active", "tab-selected-border-active")) {
                assertEquals(theme + " " + name, CoolAccent.tint(CoolThemeCustomizer.resolve(palette, name), "#79cb60"), colors.get(name));
            }
            assertEquals(theme, CoolAccent.text(CoolThemeCustomizer.resolve(palette, "text-link"), "#79cb60", editor), colors.get("text-link"));
            // the palette itself is left alone: colored tags and badges named blue stay blue
            assertTrue(theme, colors.keySet().stream().noneMatch(name -> name.startsWith("blue-")));
            assertFalse(theme, colors.containsKey("tab-selected-border-inactive"));
        }
    }

    @Test
    public void theIslandsBlueChangesNothingButTheTargets() {
        for (String theme : CoolThemeCustomizer.THEMES.keySet()) {
            Map<String, String> palette = palette(theme);
            String editor = CoolThemeCustomizer.THEMES.get(theme).editorBackground();
            assertEquals(Map.of(), CoolThemeCustomizer.colors(palette, null, List.of(), editor));
            assertEquals(Map.of(), CoolThemeCustomizer.colors(palette, CoolAccent.DEFAULT_ACCENT, List.of("controls", "folders"), editor));
            // tabs: the line of the selected tab is the accent, the one of an unfocused tab the accent at half (VS Code)
            Map<String, String> tabs = CoolThemeCustomizer.colors(palette, null, List.of("tabs"), editor);
            assertEquals(theme, "#3871e1", tabs.get("tab-selected-border-active"));
            assertEquals(theme, "#3871e180", tabs.get("tab-selected-border-inactive"));
        }
    }

    @Test
    public void lightAccentsGetDarkTextOnButtons() {
        Map<String, String> palette = palette("Islands Dark");
        Map<String, String> yellow = CoolThemeCustomizer.colors(palette, "#ffd866", List.of(), "#191a1c");
        assertEquals("#000000", yellow.get("text-over-accent"));
        Map<String, String> violet = CoolThemeCustomizer.colors(palette, "#7e57c2", List.of(), "#191a1c");
        assertFalse(violet.containsKey("text-over-accent"));
    }
}
