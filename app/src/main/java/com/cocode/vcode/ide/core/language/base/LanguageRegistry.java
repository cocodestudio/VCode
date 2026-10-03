package com.cocode.vcode.ide.core.language.base;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.language.css.CssLanguageBackend;
import com.cocode.vcode.ide.core.language.html.HtmlLanguageBackend;
import com.cocode.vcode.ide.core.language.js.JsLanguageBackend;
import com.cocode.vcode.ide.core.language.json.JsonLanguageBackend;
import com.cocode.vcode.ide.core.language.md.MarkdownLanguageBackend;
import com.cocode.vcode.ide.core.language.svg.SvgLanguageBackend;
import com.cocode.vcode.ide.core.language.ts.TsLanguageBackend;
import com.cocode.vcode.ide.core.model.FileType;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central thread-safe registry for all {@link LanguageBackend} and {@link LanguageDefinition}
 * implementations in VCode. Manages language discovery by FileType, file extension, and LSP language ID.
 */
public final class LanguageRegistry {

    private static volatile LanguageRegistry sInstance;

    private final Map<FileType, LanguageBackend> byFileType = new EnumMap<>(FileType.class);
    private final Map<String, LanguageBackend> byExtension = new ConcurrentHashMap<>();
    private final Map<String, LanguageBackend> byLanguageId = new ConcurrentHashMap<>();

    private final Map<String, LanguageDefinition> definitionsById = new ConcurrentHashMap<>();
    private final Map<String, LanguageDefinition> definitionsByExtension = new ConcurrentHashMap<>();

    private LanguageRegistry() {
    }

    public static LanguageRegistry getInstance() {
        if (sInstance == null) {
            synchronized (LanguageRegistry.class) {
                if (sInstance == null) {
                    sInstance = new LanguageRegistry();
                    sInstance.registerDefaults();
                }
            }
        }
        return sInstance;
    }

    private void registerDefaults() {
        register(new HtmlLanguageBackend());
        register(new CssLanguageBackend());
        register(new JsLanguageBackend());
        register(new TsLanguageBackend());
        register(new JsonLanguageBackend());
        register(new MarkdownLanguageBackend());
        register(new SvgLanguageBackend());

        // Built-in Language Definitions
        registerDefinition(new LanguageDefinition.Builder("html")
                .displayName("HTML")
                .addExtension("html").addExtension("htm")
                .addMimeType("text/html")
                .blockComment("<!--", "-->")
                .icon(R.drawable.ic_html_icon)
                .color(R.color.vcode_lang_html)
                .fallbackFileType(FileType.HTML)
                .build());

        registerDefinition(new LanguageDefinition.Builder("css")
                .displayName("CSS")
                .addExtension("css")
                .addMimeType("text/css")
                .blockComment("/*", "*/")
                .icon(R.drawable.ic_css_icon)
                .color(R.color.vcode_lang_css)
                .fallbackFileType(FileType.CSS)
                .build());

        registerDefinition(new LanguageDefinition.Builder("javascript")
                .displayName("JavaScript")
                .addExtension("js").addExtension("mjs").addExtension("cjs")
                .addMimeType("text/javascript")
                .lineComment("//")
                .blockComment("/*", "*/")
                .icon(R.drawable.ic_js_icon)
                .color(R.color.vcode_lang_js)
                .fallbackFileType(FileType.JAVASCRIPT)
                .build());

        registerDefinition(new LanguageDefinition.Builder("typescript")
                .displayName("TypeScript")
                .addExtension("ts").addExtension("tsx")
                .addMimeType("application/typescript")
                .lineComment("//")
                .blockComment("/*", "*/")
                .icon(R.drawable.ic_typescript_icon)
                .color(R.color.vcode_lang_ts)
                .fallbackFileType(FileType.TYPESCRIPT)
                .build());

        registerDefinition(new LanguageDefinition.Builder("json")
                .displayName("JSON")
                .addExtension("json")
                .addMimeType("application/json")
                .icon(R.drawable.ic_json_icon)
                .color(R.color.vcode_lang_json)
                .fallbackFileType(FileType.JSON)
                .build());

        registerDefinition(new LanguageDefinition.Builder("markdown")
                .displayName("Markdown")
                .addExtension("md")
                .addMimeType("text/markdown")
                .blockComment("<!--", "-->")
                .icon(R.drawable.ic_md_icon)
                .color(R.color.vcode_lang_md)
                .fallbackFileType(FileType.MARKDOWN)
                .build());

        registerDefinition(new LanguageDefinition.Builder("svg")
                .displayName("SVG")
                .addExtension("svg")
                .addMimeType("image/svg+xml")
                .blockComment("<!--", "-->")
                .icon(R.drawable.ic_bezier_curve)
                .color(R.color.vcode_file_svg)
                .fallbackFileType(FileType.SVG)
                .build());
    }

    /**
     * Registers a new or custom {@link LanguageBackend}.
     */
    public synchronized void register(LanguageBackend backend) {
        if (backend == null) return;
        if (backend.getFileType() != null) {
            byFileType.put(backend.getFileType(), backend);
        }
        if (backend.getLanguageId() != null) {
            byLanguageId.put(backend.getLanguageId().toLowerCase(), backend);
        }
        if (backend.getSupportedExtensions() != null) {
            for (String ext : backend.getSupportedExtensions()) {
                if (ext != null && !ext.isEmpty()) {
                    byExtension.put(ext.toLowerCase(), backend);
                }
            }
        }
    }

    /**
     * Registers a metadata {@link LanguageDefinition} for a language.
     */
    public synchronized void registerDefinition(LanguageDefinition definition) {
        if (definition == null || definition.getId() == null) return;
        definitionsById.put(definition.getId().toLowerCase(), definition);
        for (String ext : definition.getExtensions()) {
            if (ext != null && !ext.isEmpty()) {
                definitionsByExtension.put(ext.toLowerCase(), definition);
            }
        }
    }

    public LanguageBackend getBackend(FileType type) {
        if (type == null) return null;
        return byFileType.get(type);
    }

    public LanguageBackend getBackendForLanguageId(String languageId) {
        if (languageId == null) return null;
        return byLanguageId.get(languageId.toLowerCase());
    }

    public LanguageBackend getBackendForExtension(String ext) {
        if (ext == null) return null;
        return byExtension.get(ext.toLowerCase());
    }

    public Collection<LanguageBackend> getAllBackends() {
        return Collections.unmodifiableCollection(byFileType.values());
    }

    public LanguageDefinition getDefinition(String languageId) {
        if (languageId == null) return null;
        return definitionsById.get(languageId.toLowerCase());
    }

    public LanguageDefinition getDefinitionForExtension(String ext) {
        if (ext == null) return null;
        return definitionsByExtension.get(ext.toLowerCase());
    }

    public Collection<LanguageDefinition> getAllDefinitions() {
        return Collections.unmodifiableCollection(definitionsById.values());
    }
}
