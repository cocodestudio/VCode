package com.cocode.vcode.ide.core.diagnostic;

/**
 * Standardized problem identifiers for diagnostic errors, warnings, and code quality issues.
 * Enables deterministic mapping between diagnostic issues and automated quick-fix intentions.
 */
public enum ProblemCode {
    SYNTAX_ERROR,
    BRACKET_MISMATCH,
    HTML_MISSING_ALT,
    HTML_BUTTON_MISSING_TYPE,
    HTML_EMPTY_TITLE,
    JS_CONST_REASSIGNMENT,
    JS_UNUSED_VARIABLE,
    JS_MISSING_SEMICOLON,
    JS_UNDEFINED_VARIABLE,
    CSS_UNKNOWN_PROPERTY,
    JSON_SYNTAX_ERROR,
    JSON_COMMENTS_DISALLOWED
}
