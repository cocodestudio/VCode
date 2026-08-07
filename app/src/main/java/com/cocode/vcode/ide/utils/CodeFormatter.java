package com.cocode.vcode.ide.utils;

import com.cocode.vcode.ide.core.language.base.BaseFormatter;
import com.cocode.vcode.ide.core.language.css.CssFormatter;
import com.cocode.vcode.ide.core.language.html.HtmlFormatter;
import com.cocode.vcode.ide.core.language.js.JsFormatter;
import com.cocode.vcode.ide.core.language.json.JsonFormatter;
import com.cocode.vcode.ide.core.language.ts.TsFormatter;
import com.cocode.vcode.ide.core.model.FileType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Central routing service for source code formatting.
 * Matches specific programming languages with their corresponding
 * base formatter implementation to handle formatting requests.
 */
public class CodeFormatter {

    // An optimized map lookup structure mapped to language enum keys
    private static final Map<FileType, BaseFormatter> FORMATTERS = new EnumMap<>(FileType.class);

    static {
        FORMATTERS.put(FileType.JSON, new JsonFormatter());
        FORMATTERS.put(FileType.HTML, new HtmlFormatter());
        FORMATTERS.put(FileType.CSS, new CssFormatter());
        FORMATTERS.put(FileType.JAVASCRIPT, new JsFormatter());
        FORMATTERS.put(FileType.TYPESCRIPT, new TsFormatter());
    }

    public static boolean isFormatSupported(FileType language) {
        return FORMATTERS.containsKey(language);
    }

    /**
     * Formats incoming source text block based on target language style standards.
     *
     * @param code     The unformatted raw code text block.
     * @param language The targeted language configuration identifier.
     * @return The beautified structural code string.
     */
    public static String format(String code, FileType language) {
        // Return original string immediately if empty or null to avoid unneeded allocation steps
        if (code == null || code.trim().isEmpty()) return code;

        try {
            BaseFormatter formatter = FORMATTERS.get(language);
            if (formatter != null) {
                return formatter.format(code);
            }
            return code; // Return unmodified string if language does not have a dedicated formatter class
        } catch (Exception e) {
            android.util.Log.e("VCode", "Formatting failed", e);
            return code; // Structural fallback to protect data integrity on invalid syntax states
        }
    }
}