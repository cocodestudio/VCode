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

    public static final Map<String, Map<String, SignatureInfo>> SIGNATURES_BY_CONTAINER = new HashMap<>();
    public static final Map<String, SignatureInfo> GLOBAL_SIGNATURES = new HashMap<>();
    public static final Map<String, SignatureInfo> PROTOTYPE_SIGNATURES_BY_NAME = new HashMap<>();

    public static final class SignatureInfo {
        public final String name;
        public final String detail;
        public final String doc;
        public final java.util.List<String> parameters;

        public SignatureInfo(String name, String detail, String doc, java.util.List<String> parameters) {
            this.name = name;
            this.detail = detail != null ? detail : name;
            this.doc = doc != null ? doc : "";
            this.parameters = parameters != null ? Collections.unmodifiableList(parameters) : Collections.emptyList();
        }
    }

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

    public static void reloadForTest() {
        synchronized (lock) {
            loaded = false;
            ensureLoaded();
        }
    }

    private static void loadFromAssets() {
        String jsonStr = StaticAssetReader.readAsset("types/js_standard_library.json");
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            loadFallback();
            return;
        }

        PROTOTYPE_METHODS.clear();
        CHAIN_RETURN_TYPES.clear();
        PROMISE_FUNCTIONS.clear();
        SIGNATURES_BY_CONTAINER.clear();
        GLOBAL_SIGNATURES.clear();
        PROTOTYPE_SIGNATURES_BY_NAME.clear();

        try {
            JSONObject root = new JSONObject(jsonStr);

            // Load static container methods
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

            // Load DOM event names
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

            // Load prototype methods by receiver type
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

            // Load fluent chain return types
            CHAIN_RETURN_TYPES.clear();
            if (root.has("chainReturnTypes")) {
                JSONObject chainObj = root.getJSONObject("chainReturnTypes");
                Iterator<String> keys = chainObj.keys();
                while (keys.hasNext()) {
                    String method = keys.next();
                    CHAIN_RETURN_TYPES.put(method, chainObj.getString(method));
                }
            }

            // Load asynchronous and Promise-returning functions
            PROMISE_FUNCTIONS.clear();
            if (root.has("promiseFunctions")) {
                JSONArray pfArr = root.getJSONArray("promiseFunctions");
                for (int i = 0; i < pfArr.length(); i++) {
                    PROMISE_FUNCTIONS.add(pfArr.getString(i));
                }
            }

            // Load LSP signatures and member metadata
            SIGNATURES_BY_CONTAINER.clear();
            GLOBAL_SIGNATURES.clear();
            PROTOTYPE_SIGNATURES_BY_NAME.clear();
            if (root.has("lspMembers")) {
                JSONObject lspObj = root.getJSONObject("lspMembers");
                Iterator<String> containerKeys = lspObj.keys();
                while (containerKeys.hasNext()) {
                    String container = containerKeys.next();
                    JSONArray members = lspObj.getJSONArray(container);
                    Map<String, SignatureInfo> containerMap = SIGNATURES_BY_CONTAINER.computeIfAbsent(container, k -> new HashMap<>());

                    for (int i = 0; i < members.length(); i++) {
                        JSONObject m = members.getJSONObject(i);
                        String name = m.optString("name", "");
                        if (name.isEmpty()) continue;
                        String kind = m.optString("kind", "fn");
                        if (!"fn".equals(kind)) continue;

                        String detail = m.optString("detail", name);
                        String doc = m.optString("doc", "");
                        java.util.List<String> params = new java.util.ArrayList<>();

                        if (m.has("params")) {
                            JSONArray pArr = m.getJSONArray("params");
                            for (int p = 0; p < pArr.length(); p++) {
                                params.add(pArr.getString(p));
                            }
                        } else {
                            int open = detail.indexOf('(');
                            int close = detail.lastIndexOf(')');
                            if (open >= 0 && close > open) {
                                String paramStr = detail.substring(open + 1, close).trim();
                                if (!paramStr.isEmpty()) {
                                    String[] parts = paramStr.split(",");
                                    for (String part : parts) {
                                        params.add(part.trim());
                                    }
                                }
                            }
                        }

                        SignatureInfo sig = new SignatureInfo(name, detail, doc, params);
                        containerMap.put(name, sig);

                        if ("__global__".equals(container) || "window".equals(container)) {
                            GLOBAL_SIGNATURES.putIfAbsent(name, sig);
                        }
                        if (container.startsWith("__") && !"__global__".equals(container)) {
                            PROTOTYPE_SIGNATURES_BY_NAME.putIfAbsent(name, sig);
                        }
                    }
                }
            }

        } catch (Exception e) {
            loadFallback();
        }
    }

    /**
     * Resolves built-in signature info for a function or method invocation.
     *
     * @param funcName full function or member expression string (e.g. "console.log", "fetch", "arr.push")
     * @param receiverType optional inferred receiver type (e.g. "@ARRAY", "@STRING", "element")
     * @return matching SignatureInfo or null if not found
     */
    public static SignatureInfo getBuiltinSignature(String funcName, String receiverType) {
        if (funcName == null || funcName.isEmpty()) return null;
        ensureLoaded();

        int dotIdx = funcName.lastIndexOf('.');
        if (dotIdx >= 0) {
            String container = funcName.substring(0, dotIdx);
            String method = funcName.substring(dotIdx + 1);

            // Look up direct container object (e.g. console, Math, document, JSON, URL)
            Map<String, SignatureInfo> cMap = SIGNATURES_BY_CONTAINER.get(container);
            if (cMap != null && cMap.containsKey(method)) {
                return cMap.get(method);
            }

            // Look up inferred receiver type
            if (receiverType != null) {
                String protoContainer = mapTypeToContainer(receiverType);
                if (protoContainer != null) {
                    Map<String, SignatureInfo> pMap = SIGNATURES_BY_CONTAINER.get(protoContainer);
                    if (pMap != null && pMap.containsKey(method)) {
                        return pMap.get(method);
                    }
                }
            }

            // Fallback to standard prototype method by name (e.g. push, slice, replace, addEventListener)
            SignatureInfo protoSig = PROTOTYPE_SIGNATURES_BY_NAME.get(method);
            if (protoSig != null) {
                return protoSig;
            }

            // Check global identifiers when container is window
            if ("window".equals(container)) {
                return GLOBAL_SIGNATURES.get(method);
            }

            return null;
        } else {
            // Standalone function or constructor call (e.g. "fetch", "setTimeout", "Promise", "Date")
            SignatureInfo globalSig = GLOBAL_SIGNATURES.get(funcName);
            if (globalSig != null) {
                return globalSig;
            }
            // Fallback to prototype method (e.g. if invoked directly or bound)
            return PROTOTYPE_SIGNATURES_BY_NAME.get(funcName);
        }
    }

    private static String mapTypeToContainer(String type) {
        if (type == null) return null;
        switch (type.toUpperCase()) {
            case "@ARRAY":
            case "ARRAY":
                return "__array__";
            case "@STRING":
            case "STRING":
                return "__string__";
            case "@NUMBER":
            case "NUMBER":
                return "__number__";
            case "@DATE":
            case "DATE":
                return "__date__";
            case "@MAP":
            case "MAP":
                return "__map__";
            case "@SET":
            case "SET":
                return "__set__";
            case "@PROMISE":
            case "PROMISE":
                return "__promise__";
            case "ELEMENT":
            case "HTMLELEMENT":
                return "__element__";
            case "RESPONSE":
                return "__response__";
            case "EVENT":
                return "__event__";
            case "CLASSLIST":
                return "__classlist__";
            case "STYLE":
                return "__style__";
            case "CANVASCONTEXT":
                return "__canvascontext__";
            case "FILEREADER":
                return "__filereader__";
            case "REGEXP":
                return "__regexp__";
            default:
                return null;
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
