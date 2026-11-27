package com.woogajjangka.coolthemekit;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

/** The texts of the plugin ({@code messages/CoolThemeKitBundle.properties}, Korean in {@code _ko}). */
public final class CoolThemeKitBundle {
    static final String BUNDLE = "messages.CoolThemeKitBundle";
    private static final DynamicBundle INSTANCE = new DynamicBundle(CoolThemeKitBundle.class, BUNDLE);

    private CoolThemeKitBundle() {
    }

    static @Nls @NotNull String message(@PropertyKey(resourceBundle = BUNDLE) @NotNull String key, Object @NotNull ... params) {
        return INSTANCE.getMessage(key, params);
    }
}
