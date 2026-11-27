package com.woogajjangka.coolthemekit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The filters of the icons give the svg VS Code writes. {@code coolthemekit/filters.json} has sample icons of each style
 * as Cool Theme Kit draws them with {@code folderColor}, {@code monochrome}, {@code opacity} and {@code saturation}
 * ({@code bun run export:jetbrains-icons}); the plugin draws them from the icons it ships.
 */
public class CoolIconFiltersTest {
    private static String lf(String svg) {
        return svg.replace("\r\n", "\n");
    }

    private static CoolIconFilters.Settings settings(JsonObject json) {
        String folderColor = json.has("folderColor") ? json.get("folderColor").getAsString() : null;
        boolean monochrome = json.has("monochrome") && json.get("monochrome").getAsBoolean();
        String monochromeColor = monochrome ? json.get("monochromeColor").getAsString() : null;
        double opacity = json.has("opacity") ? json.get("opacity").getAsDouble() : 1;
        double saturation = json.has("saturation") ? json.get("saturation").getAsDouble() : 1;
        return new CoolIconFilters.Settings(folderColor, monochromeColor, opacity, saturation);
    }

    private static String shipped(String style, String ref) throws Exception {
        try (InputStream in = CoolIconFiltersTest.class.getResourceAsStream(CoolIcons.root(style) + ref + ".svg")) {
            assertNotNull(style + " " + ref, in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void sameIconsAsVsCode() throws Exception {
        JsonArray cases;
        try (InputStream in = CoolIconFiltersTest.class.getResourceAsStream("/coolthemekit/filters.json")) {
            assertNotNull("run gradlew syncIcons", in);
            cases = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
        }
        Set<String> seen = new HashSet<>();
        List<String> wrong = new ArrayList<>();
        int icons = 0;
        for (JsonElement element : cases) {
            JsonObject filterCase = element.getAsJsonObject();
            String style = filterCase.get("style").getAsString();
            String name = filterCase.get("name").getAsString();
            seen.add(style + "/" + name);
            CoolIconFilters.Settings settings = settings(filterCase.getAsJsonObject("settings"));
            for (Map.Entry<String, JsonElement> icon : filterCase.getAsJsonObject("icons").entrySet()) {
                icons++;
                String ref = icon.getKey();
                String expected = lf(icon.getValue().getAsString());
                String actual = lf(CoolIconFilters.apply(shipped(style, ref), CoolIcons.withoutDark(ref), style, settings));
                if (!expected.equals(actual)) wrong.add(style + "/" + name + " " + ref);
            }
        }
        assertEquals(wrong.toString(), List.of(), wrong);
        for (String style : CoolIconsSettings.STYLES) {
            for (String name : List.of("folderColor", "monochrome", "opacity", "saturation", "all")) {
                assertTrue("no case " + style + "/" + name, seen.contains(style + "/" + name));
            }
        }
        assertTrue(icons > 200);
    }

    @Test
    public void noFiltersIsTheShippedIcon() throws Exception {
        String svg = shipped(CoolIconsSettings.GLASS, "files/python");
        assertEquals(svg, CoolIconFilters.apply(svg, "files/python", CoolIconsSettings.GLASS, CoolIconFilters.Settings.NONE));
        assertTrue(CoolIconFilters.Settings.NONE.isNone());
    }

    @Test
    public void numbersAreWrittenAsInJavaScript() {
        assertEquals("0.6", CoolIconFilters.jsNumber(0.6));
        assertEquals("1", CoolIconFilters.jsNumber(1));
        assertEquals("0", CoolIconFilters.jsNumber(0));
        assertEquals("0.0001", CoolIconFilters.jsNumber(0.0001));
    }

    @Test
    public void anInvalidMonochromeColorChangesNothing() {
        String svg = "<svg><path fill=\"#ff0000\"/></svg>";
        assertEquals(svg, CoolIconFilters.monochrome(svg, "red"));
        assertEquals(svg, CoolIconFilters.monochrome(svg, null));
        assertEquals("<svg><path fill=\"#00ff00\"/></svg>", CoolIconFilters.monochrome(svg, "#0f0"));
    }
}
