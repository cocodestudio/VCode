package com.cocode.vcode.ide.core.language.css;

import com.cocode.vcode.ide.core.completion.staticdata.StaticAssetReader;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Lookup table of Emmet CSS abbreviations and their expansions.
 * Loaded lazily from assets/completions/emmet_definitions.json on first access.
 */
public class EmmetCssDefinitions {
    // CSS Named Abbreviations
    public static final Map<String, String> CSS_ABBREVS = new HashMap<>();
    // CSS numeric property map
    public static final Map<String, String> CSS_PROP_MAP = new HashMap<>();
    private static final Object lock = new Object();
    private static volatile boolean loaded = false;

    static {
        ensureLoaded();
    }

    public static void ensureLoaded() {
        if (loaded) return;
        synchronized (lock) {
            if (loaded) return;
            loadFromAssets();
            loaded = true;
        }
    }

    private static void loadFromAssets() {
        String jsonStr = StaticAssetReader.readAsset("completions/emmet_definitions.json");
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return;
        }

        try {
            JSONObject root = new JSONObject(jsonStr);

            if (root.has("cssAbbreviations")) {
                JSONObject abbrObj = root.getJSONObject("cssAbbreviations");
                Iterator<String> keys = abbrObj.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    CSS_ABBREVS.put(k, abbrObj.getString(k));
                }
            }

            if (root.has("cssPropertyMap")) {
                JSONObject propObj = root.getJSONObject("cssPropertyMap");
                Iterator<String> keys = propObj.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    CSS_PROP_MAP.put(k, propObj.getString(k));
                }
            }
        } catch (Exception ignored) {
        }
    }
}
