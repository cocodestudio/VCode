package com.cocode.vcode.ide.utils;

import android.content.Context;
import android.graphics.Typeface;

import java.util.HashMap;
import java.util.Map;

/**
 * Typeface manager and cache for application fonts.
 * Caches loaded fonts (JetBrains Mono for code, Sora for UI) to prevent redundant asset reads.
 */
public class FontManager {

    private static final String CODE_REGULAR = "fonts/JetBrainsMono-Regular.ttf";
    private static final String CODE_BOLD = "fonts/JetBrainsMono-Bold.ttf";
    private static final String UI_REGULAR = "fonts/Sora-Regular.ttf";
    private static final String UI_MEDIUM = "fonts/Sora-Medium.ttf";
    private static final String UI_SEMIBOLD = "fonts/Sora-SemiBold.ttf";
    private static volatile FontManager instance;
    private final Map<String, Typeface> cache = new HashMap<>();

    private FontManager() {
    }

    /**
     * Returns the singleton instance of FontManager.
     */
    public static FontManager getInstance() {
        if (instance == null) {
            synchronized (FontManager.class) {
                if (instance == null) {
                    instance = new FontManager();
                }
            }
        }
        return instance;
    }

    private Typeface load(Context ctx, String path) {
        Typeface cached = cache.get(path);
        if (cached != null) return cached;
        try {
            Typeface tf = Typeface.createFromAsset(ctx.getApplicationContext().getAssets(), path);
            cache.put(path, tf);
            return tf;
        } catch (Exception e) {
            return Typeface.DEFAULT;
        }
    }

    public Typeface getCodeFont(Context ctx) {
        return load(ctx, CODE_REGULAR);
    }

    public Typeface getCodeFontBold(Context ctx) {
        return load(ctx, CODE_BOLD);
    }

    public Typeface getUiFont(Context ctx) {
        return load(ctx, UI_REGULAR);
    }

    public Typeface getUiMedium(Context ctx) {
        return load(ctx, UI_MEDIUM);
    }

    public Typeface getUiSemiBold(Context ctx) {
        return load(ctx, UI_SEMIBOLD);
    }
}