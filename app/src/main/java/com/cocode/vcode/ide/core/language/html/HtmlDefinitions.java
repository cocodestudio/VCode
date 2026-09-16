package com.cocode.vcode.ide.core.language.html;

import com.cocode.vcode.ide.core.completion.staticdata.StaticAssetReader;
import com.cocode.vcode.ide.core.model.CompletionItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Static definitions of HTML tags, attributes, event handlers, and entities.
 * Loaded dynamically from completions/html_definitions.json.
 */
public class HtmlDefinitions {
    public static final List<CompletionItem> GLOBAL_ATTRS = new ArrayList<>();
    public static final List<CompletionItem> EVENT_ATTRS = new ArrayList<>();
    public static final List<CompletionItem> DOCTYPE_ITEMS = new ArrayList<>();
    public static final List<CompletionItem> ENTITY_ITEMS = new ArrayList<>();
    public static final Map<String, String[]> ATTR_VALUES = new HashMap<>();
    private static final Object LOCK = new Object();
    private static volatile boolean initialized = false;

    public static void ensureLoaded() {
        if (!initialized) {
            synchronized (LOCK) {
                if (!initialized) {
                    loadHtmlDefinitions();
                    initialized = true;
                }
            }
        }
    }

    public static void resetForTest() {
        synchronized (LOCK) {
            initialized = false;
            GLOBAL_ATTRS.clear();
            EVENT_ATTRS.clear();
            DOCTYPE_ITEMS.clear();
            ENTITY_ITEMS.clear();
            ATTR_VALUES.clear();
        }
    }

    public static List<CompletionItem> getGlobalAttrs() {
        ensureLoaded();
        return GLOBAL_ATTRS;
    }

    public static List<CompletionItem> getEventAttrs() {
        ensureLoaded();
        return EVENT_ATTRS;
    }

    public static List<CompletionItem> getDoctypeItems() {
        ensureLoaded();
        return DOCTYPE_ITEMS;
    }

    public static List<CompletionItem> getEntityItems() {
        ensureLoaded();
        return ENTITY_ITEMS;
    }

    public static String[] getAttributeValues(String attrName) {
        return getAttributeValues(null, attrName);
    }

    public static String[] getAttributeValues(String tagName, String attrName) {
        ensureLoaded();
        if (attrName == null) return null;
        String lowerAttr = attrName.toLowerCase();
        if (tagName != null) {
            String lowerTag = tagName.toLowerCase();
            if ("type".equals(lowerAttr)) {
                if ("button".equals(lowerTag)) {
                    return new String[]{"button", "submit", "reset"};
                } else if ("script".equals(lowerTag)) {
                    return new String[]{"module", "text/javascript", "application/json"};
                } else if ("style".equals(lowerTag) || "link".equals(lowerTag)) {
                    return new String[]{"text/css"};
                }
            } else if ("method".equals(lowerAttr) && "form".equals(lowerTag)) {
                return new String[]{"get", "post", "dialog"};
            }
        }
        return ATTR_VALUES.get(lowerAttr);
    }

    public static synchronized void loadHtmlDefinitions() {
        GLOBAL_ATTRS.clear();
        EVENT_ATTRS.clear();
        DOCTYPE_ITEMS.clear();
        ENTITY_ITEMS.clear();
        ATTR_VALUES.clear();

        String json = StaticAssetReader.readAsset("completions/html_definitions.json");
        if (json == null || json.isEmpty()) {
            return;
        }
        try {
            JSONObject root = new JSONObject(json);
            if (root.has("globalAttributes")) {
                JSONArray globals = root.getJSONArray("globalAttributes");
                for (int i = 0; i < globals.length(); i++) {
                    JSONObject item = globals.getJSONObject(i);
                    GLOBAL_ATTRS.add(new CompletionItem(
                            item.getString("label"),
                            item.getString("insertText"),
                            item.optString("detail", ""),
                            CompletionItem.Type.ATTRIBUTE,
                            0
                    ));
                }
            }

            if (root.has("eventAttributes")) {
                JSONArray events = root.getJSONArray("eventAttributes");
                for (int i = 0; i < events.length(); i++) {
                    JSONObject item = events.getJSONObject(i);
                    CompletionItem eventItem = new CompletionItem(
                            item.getString("label"),
                            item.getString("insertText"),
                            item.optString("detail", ""),
                            CompletionItem.Type.ATTRIBUTE,
                            0
                    );
                    EVENT_ATTRS.add(eventItem);
                    GLOBAL_ATTRS.add(eventItem);
                }
            }

            if (root.has("doctypes")) {
                JSONArray doctypes = root.getJSONArray("doctypes");
                for (int i = 0; i < doctypes.length(); i++) {
                    JSONObject item = doctypes.getJSONObject(i);
                    DOCTYPE_ITEMS.add(new CompletionItem(
                            item.getString("label"),
                            item.getString("insertText"),
                            item.optString("detail", ""),
                            CompletionItem.Type.SNIPPET,
                            0
                    ));
                }
            }

            if (root.has("entities")) {
                JSONArray entities = root.getJSONArray("entities");
                for (int i = 0; i < entities.length(); i++) {
                    JSONObject item = entities.getJSONObject(i);
                    ENTITY_ITEMS.add(new CompletionItem(
                            item.getString("label"),
                            item.getString("insertText"),
                            item.optString("detail", ""),
                            CompletionItem.Type.VALUE,
                            0
                    ));
                }
            }

            if (root.has("attributeValues")) {
                JSONObject attrValues = root.getJSONObject("attributeValues");
                Iterator<String> keys = attrValues.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    JSONArray arr = attrValues.getJSONArray(key);
                    String[] values = new String[arr.length()];
                    for (int i = 0; i < arr.length(); i++) {
                        values[i] = arr.getString(i);
                    }
                    ATTR_VALUES.put(key, values);
                }
            }
        } catch (Exception ignored) {
        }
    }
}
