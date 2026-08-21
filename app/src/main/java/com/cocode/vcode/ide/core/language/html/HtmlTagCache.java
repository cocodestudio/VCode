package com.cocode.vcode.ide.core.language.html;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Central dictionary data cache for HTML element properties.
 * Identifies void elements (self-closing tags) and block items to coordinate
 * real-time auto-indentation layout boundaries and completion logic.
 */
public class HtmlTagCache {

    private static final Set<String> VOID_ELEMENTS = new HashSet<>();
    private static final Set<String> BLOCK_ELEMENTS = new HashSet<>();
    private static boolean isLoaded = false;

    // Hardcode a tiny fallback list just in case the JSON file fails to load
    static {
        VOID_ELEMENTS.add("img");
        VOID_ELEMENTS.add("br");
        VOID_ELEMENTS.add("hr");
        VOID_ELEMENTS.add("input");
        VOID_ELEMENTS.add("meta");
        VOID_ELEMENTS.add("link");
        VOID_ELEMENTS.add("area");
        VOID_ELEMENTS.add("base");
        VOID_ELEMENTS.add("col");
        VOID_ELEMENTS.add("embed");
        VOID_ELEMENTS.add("param");
        VOID_ELEMENTS.add("source");
        VOID_ELEMENTS.add("track");
        VOID_ELEMENTS.add("wbr");

        // Block element fallback — covers the most common cases so indent works without load()
        BLOCK_ELEMENTS.add("div");
        BLOCK_ELEMENTS.add("p");
        BLOCK_ELEMENTS.add("section");
        BLOCK_ELEMENTS.add("article");
        BLOCK_ELEMENTS.add("aside");
        BLOCK_ELEMENTS.add("header");
        BLOCK_ELEMENTS.add("footer");
        BLOCK_ELEMENTS.add("main");
        BLOCK_ELEMENTS.add("nav");
        BLOCK_ELEMENTS.add("ul");
        BLOCK_ELEMENTS.add("ol");
        BLOCK_ELEMENTS.add("li");
        BLOCK_ELEMENTS.add("table");
        BLOCK_ELEMENTS.add("thead");
        BLOCK_ELEMENTS.add("tbody");
        BLOCK_ELEMENTS.add("tr");
        BLOCK_ELEMENTS.add("td");
        BLOCK_ELEMENTS.add("th");
        BLOCK_ELEMENTS.add("form");
        BLOCK_ELEMENTS.add("fieldset");
        BLOCK_ELEMENTS.add("figure");
        BLOCK_ELEMENTS.add("figcaption");
        BLOCK_ELEMENTS.add("details");
        BLOCK_ELEMENTS.add("summary");
        BLOCK_ELEMENTS.add("blockquote");
        BLOCK_ELEMENTS.add("pre");
        BLOCK_ELEMENTS.add("h1");
        BLOCK_ELEMENTS.add("h2");
        BLOCK_ELEMENTS.add("h3");
        BLOCK_ELEMENTS.add("h4");
        BLOCK_ELEMENTS.add("h5");
        BLOCK_ELEMENTS.add("h6");
        BLOCK_ELEMENTS.add("head");
        BLOCK_ELEMENTS.add("body");
        BLOCK_ELEMENTS.add("html");
        BLOCK_ELEMENTS.add("script");
        BLOCK_ELEMENTS.add("style");
        BLOCK_ELEMENTS.add("template");
        BLOCK_ELEMENTS.add("iframe");
        BLOCK_ELEMENTS.add("canvas");
        BLOCK_ELEMENTS.add("video");
        BLOCK_ELEMENTS.add("audio");
        BLOCK_ELEMENTS.add("dialog");
        BLOCK_ELEMENTS.add("menu");
        BLOCK_ELEMENTS.add("select");
        BLOCK_ELEMENTS.add("textarea");
    }

    /**
     * Reads and parses tag properties from asset configuration files.
     * Synchronized block prevents concurrent read state collisions on application startup.
     */
    public static synchronized void load(Context context) {
        if (isLoaded) return; // Prevent parsing multiple times if already cached
        try (InputStream is = context.getAssets().open("html_tags.json")) {
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            String jsonStr = new String(buffer, StandardCharsets.UTF_8);

            JSONArray tags = new JSONArray(jsonStr);
            VOID_ELEMENTS.clear();  // Wipe the hardcoded bootstrap records
            BLOCK_ELEMENTS.clear();

            for (int i = 0; i < tags.length(); i++) {
                JSONObject tagObj = tags.getJSONObject(i);
                String tagName = tagObj.getString("tag").toLowerCase();
                boolean isSelfClosing = tagObj.getBoolean("selfClosing");

                if (isSelfClosing) {
                    VOID_ELEMENTS.add(tagName);
                } else {
                    // If it is not self-closing and not inline, we treat it as a block tag for indentation
                    String detail = tagObj.optString("detail", "").toLowerCase();
                    if (!detail.contains("inline") && !detail.contains("text")) {
                        BLOCK_ELEMENTS.add(tagName);
                    }
                }
            }
            isLoaded = true;
        } catch (Exception e) {
            android.util.Log.e("VCode", "Failed to load html_tags.json", e);
        }
    }

    /**
     * Determines if a tag is a self-closing void element that cannot contain internal children.
     */
    public static boolean isVoidElement(String tag) {
        return tag != null && VOID_ELEMENTS.contains(tag.toLowerCase());
    }

    /**
     * Determines if a tag behaves as structural block-level markup demanding dedicated indentation lines.
     */
    public static boolean isBlockElement(String tag) {
        return tag != null && BLOCK_ELEMENTS.contains(tag.toLowerCase());
    }
}