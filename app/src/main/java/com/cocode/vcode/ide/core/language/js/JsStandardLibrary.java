package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.completion.staticdata.StaticAssetReader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Standard library definitions and built-in objects for JavaScript autocompletion and linting.
 * Loaded lazily from assets/types/js_standard_library.json on first access.
 */
public class JsStandardLibrary {

    private static final Object lock = new Object();
    private static volatile boolean loaded = false;

    public static String[][] DOT_METHODS;
    public static String[] EVENT_NAMES;
    public static final Map<String, String[]> PROTOTYPE_METHODS = new HashMap<>();
    public static final Map<String, String> CHAIN_RETURN_TYPES = new HashMap<>();
    public static final Set<String> PROMISE_FUNCTIONS = new HashSet<>();

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
        String jsonStr = StaticAssetReader.readAsset("types/js_standard_library.json");
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            loadFallback();
            return;
        }

        try {
            JSONObject root = new JSONObject(jsonStr);

            // 1. dotMethods
            if (root.has("dotMethods")) {
                JSONObject dotObj = root.getJSONObject("dotMethods");
                int size = dotObj.length();
                String[][] dotMethods = new String[size][2];
                Iterator<String> keys = dotObj.keys();
                int idx = 0;
                while (keys.hasNext()) {
                    String objName = keys.next();
                    JSONArray arr = dotObj.getJSONArray(objName);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < arr.length(); i++) {
                        if (i > 0) sb.append(',');
                        sb.append(arr.getString(i));
                    }
                    dotMethods[idx][0] = objName;
                    dotMethods[idx][1] = sb.toString();
                    idx++;
                }
                DOT_METHODS = dotMethods;
            } else {
                DOT_METHODS = new String[0][0];
            }

            // 2. eventNames
            if (root.has("eventNames")) {
                JSONArray evArr = root.getJSONArray("eventNames");
                String[] eventNames = new String[evArr.length()];
                for (int i = 0; i < evArr.length(); i++) {
                    eventNames[i] = evArr.getString(i);
                }
                EVENT_NAMES = eventNames;
            } else {
                EVENT_NAMES = new String[0];
            }

            // 3. prototypeMethods
            PROTOTYPE_METHODS.clear();
            if (root.has("prototypeMethods")) {
                JSONObject protoObj = root.getJSONObject("prototypeMethods");
                Iterator<String> keys = protoObj.keys();
                while (keys.hasNext()) {
                    String typeName = keys.next();
                    JSONArray arr = protoObj.getJSONArray(typeName);
                    String[] methods = new String[arr.length()];
                    for (int i = 0; i < arr.length(); i++) {
                        methods[i] = arr.getString(i);
                    }
                    PROTOTYPE_METHODS.put(typeName, methods);
                }
            }

            // 4. chainReturnTypes
            CHAIN_RETURN_TYPES.clear();
            if (root.has("chainReturnTypes")) {
                JSONObject chainObj = root.getJSONObject("chainReturnTypes");
                Iterator<String> keys = chainObj.keys();
                while (keys.hasNext()) {
                    String method = keys.next();
                    CHAIN_RETURN_TYPES.put(method, chainObj.getString(method));
                }
            }

            // 5. promiseFunctions
            PROMISE_FUNCTIONS.clear();
            if (root.has("promiseFunctions")) {
                JSONArray pfArr = root.getJSONArray("promiseFunctions");
                for (int i = 0; i < pfArr.length(); i++) {
                    PROMISE_FUNCTIONS.add(pfArr.getString(i));
                }
            }

        } catch (Exception e) {
            loadFallback();
        }
    }

    private static void loadFallback() {
        if (DOT_METHODS == null) DOT_METHODS = new String[0][0];
        if (EVENT_NAMES == null) EVENT_NAMES = new String[0];
        if (PROMISE_FUNCTIONS.isEmpty()) {
            Collections.addAll(PROMISE_FUNCTIONS, "fetch", "axios", "axios.get", "axios.post", "axios.put", "axios.delete");
        }
    }
}
