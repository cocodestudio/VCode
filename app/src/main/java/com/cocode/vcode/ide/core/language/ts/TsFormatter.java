package com.cocode.vcode.ide.core.language.ts;

import com.cocode.vcode.ide.core.language.js.JsFormatter;

/**
 * Formatter for TypeScript files.
 * Since TypeScript syntax structurally mirrors JavaScript regarding code blocks, strings,
 * parameters, and statement terminators, it inherits the core formatting engine from JsFormatter.
 */
public class TsFormatter extends JsFormatter {
    // Inherits all JS formatting capabilities.
    // Can be overridden in the future if TypeScript-specific syntax (like Enums or complex Interfaces)
    // requires custom line-breaking or indentation handling beyond standard ECMAScript.
}
