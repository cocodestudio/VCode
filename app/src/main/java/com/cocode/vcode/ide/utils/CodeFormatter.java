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
 * Dispatcher that routes source code formatting requests to language-specific formatters
 * (HTML, CSS, JavaScript, TypeScript, JSON).
 */
public class CodeFormatter {

    private static final Map<FileType, BaseFormatter> FORMATTERS = new EnumMap<>(FileType.class);

    static {
        FORMATTERS.put(FileType.JSON, new JsonFormatter());
        FORMATTERS.put(FileType.HTML, new HtmlFormatter());
        FORMATTERS.put(FileType.CSS, new CssFormatter());
        FORMATTERS.put(FileType.JAVASCRIPT, new JsFormatter());
        FORMATTERS.put(FileType.TYPESCRIPT, new TsFormatter());
    }

    /**
     * Returns true if code formatting is supported for the given file type.
     */
    public static boolean isFormatSupported(FileType language) {
        return FORMATTERS.containsKey(language);
    }

    /**
     * Formats the given source code based on its language type.
     *
     * @param code     the raw source code string
     * @param language the file type of the code
     * @return the formatted code string, or the original code if unsupported or on syntax errors
     */
    public static String format(String code, FileType language) {
        if (code == null || code.trim().isEmpty()) return code;

        try {
            BaseFormatter formatter = FORMATTERS.get(language);
            if (formatter != null) {
                return formatter.format(code);
            }
            return code;
        } catch (Exception e) {
            android.util.Log.e("VCode", "Formatting failed", e);
            return code;
        }
    }
}