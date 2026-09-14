package com.cocode.vcode.ide.core.language.css;

import com.cocode.vcode.ide.core.completion.staticdata.StaticAssetReader;
import com.cocode.vcode.ide.core.model.CompletionItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Static definitions of CSS properties, at-rules, pseudo-classes, pseudo-elements, and units.
 * Loaded dynamically from completions/css_definitions.json.
 */
public class CssDefinitions {
    public static final List<CompletionItem> PSEUDO_ITEMS = new ArrayList<>();
    public static final List<CompletionItem> AT_RULE_ITEMS = new ArrayList<>();
    public static final List<String> MEDIA_KEYWORDS = new ArrayList<>();
    public static final List<MediaFeatureEntry> MEDIA_FEATURES = new ArrayList<>();

    public static class MediaFeatureEntry {
        public final String label;
        public final String insertText;

        public MediaFeatureEntry(String label, String insertText) {
            this.label = label;
            this.insertText = insertText;
        }
    }

    static {
        loadCssDefinitions();
    }

    public static synchronized void loadCssDefinitions() {
        PSEUDO_ITEMS.clear();
        AT_RULE_ITEMS.clear();
        MEDIA_KEYWORDS.clear();
        MEDIA_FEATURES.clear();

        String json = StaticAssetReader.readAsset("completions/css_definitions.json");
        if (json == null || json.isEmpty()) {
            return;
        }
        try {
            JSONObject root = new JSONObject(json);
            if (root.has("pseudos")) {
                JSONArray pseudos = root.getJSONArray("pseudos");
                for (int i = 0; i < pseudos.length(); i++) {
                    JSONObject p = pseudos.getJSONObject(i);
                    PSEUDO_ITEMS.add(new CompletionItem(
                            p.getString("label"),
                            p.getString("insertText"),
                            p.optString("detail", ""),
                            CompletionItem.Type.CSS_VALUE,
                            0
                    ));
                }
            }
            if (root.has("atRules")) {
                JSONArray atRules = root.getJSONArray("atRules");
                for (int i = 0; i < atRules.length(); i++) {
                    JSONObject r = atRules.getJSONObject(i);
                    AT_RULE_ITEMS.add(new CompletionItem(
                            r.getString("label"),
                            r.getString("insertText"),
                            r.optString("detail", "At-rule"),
                            CompletionItem.Type.CSS_VALUE,
                            0
                    ));
                }
            }
            if (root.has("mediaKeywords")) {
                JSONArray mediaKws = root.getJSONArray("mediaKeywords");
                for (int i = 0; i < mediaKws.length(); i++) {
                    MEDIA_KEYWORDS.add(mediaKws.getString(i));
                }
            }
            if (root.has("mediaFeatures")) {
                JSONArray mediaFeats = root.getJSONArray("mediaFeatures");
                for (int i = 0; i < mediaFeats.length(); i++) {
                    JSONObject f = mediaFeats.getJSONObject(i);
                    MEDIA_FEATURES.add(new MediaFeatureEntry(
                            f.getString("label"),
                            f.getString("insertText")
                    ));
                }
            }
        } catch (Exception ignored) {
        }
    }
}
