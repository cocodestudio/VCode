package com.cocode.vcode.ide.core.language.base;

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
 * Central thread-safe registry for all {@link LanguageBackend} implementations in VCode.
 * Manages language discovery by FileType, file extension, and LSP language ID.
 */
public final class LanguageRegistry {

    private static volatile LanguageRegistry sInstance;

    private final Map<FileType, LanguageBackend> byFileType = new EnumMap<>(FileType.class);
    private final Map<String, LanguageBackend> byExtension = new ConcurrentHashMap<>();
    private final Map<String, LanguageBackend> byLanguageId = new ConcurrentHashMap<>();

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
}
