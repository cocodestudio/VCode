package com.cocode.vcode.ide.core.language.json;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Advanced Strict JSON Engine for VCode Mobile IDE.
 *
 * <p>Behavioural guarantees:
 * <ul>
 * <li>RFC-8259 strict: no comments, no trailing commas, no single quotes,
 * no unquoted keys, no leading zeroes, no multiple roots.</li>
 * <li>Character-level scanner – O(n) single pass with no regex and no
 * {@code String.split()}.</li>
 * <li>Contextual snippets (~30 chars) with an exact ^ pointer.</li>
 * <li>Quick-fix suggestions for common mistakes (single quotes, trailing commas,
 * unquoted keys, comments).</li>
 * <li>Error recovery: continues scanning so the IDE can show multiple errors.</li>
 * </ul>
 */
public class JsonValidator {

    private static JsonError makeNullError() {
        return new JsonError("Input is null.", 0, 0, 0, "ERROR",
                "null\n^", "Provide a valid JSON string");
    }

    /**
     * Builds the visual caret marker row mapping up directly against the error character offset location.
     */
    @NonNull
    private static StringBuilder getPointer(String raw, int errorIndexInRaw, List<Integer> widths) {
        StringBuilder pointer = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            if (i < errorIndexInRaw) {
                for (int w = 0; w < widths.get(i); w++) pointer.append(' ');
            } else if (i == errorIndexInRaw) {
                pointer.append('^'); // Plant the visual arrow marker exactly under the bad character
                for (int w = 1; w < widths.get(i); w++) pointer.append(' ');
            }
        }
        if (errorIndexInRaw >= raw.length()) {
            pointer.append('^');
        }
        return pointer;
    }

    /* -------------------------------------------------------------------- */
    /* Character Scanner                                                   */
    /* -------------------------------------------------------------------- */

    /**
     * Evaluates a payload string object to ensure conformity with RFC syntax standards.
     */
    public ValidationReport validate(String json) {
        if (json == null) {
            List<JsonError> err = new ArrayList<>();
            err.add(makeNullError());
            return new ValidationReport(err);
        }
        if (json.isEmpty()) {
            List<JsonError> err = new ArrayList<>();
            err.add(new JsonError(
                    "Empty input: expected a JSON value.",
                    1, 1, 0, "ERROR", "^", ""));
            return new ValidationReport(err);
        }
        Scanner scanner = new Scanner(json);
        scanner.parse();
        return new ValidationReport(scanner.errors);
    }

    /**
     * Stateful structural JSON parsing state machine.
     */
    private static class Scanner {
        private final String input;
        private final int length;
        private final List<JsonError> errors = new ArrayList<>();
        private final List<ContainerType> stack = new ArrayList<>(); // Track array/object parent nest layers

        // Current cursor state
        private int pos = 0;
        private int line = 1;
        private int column = 1;
        private State state = State.EXPECT_VALUE;

        // Trailing-comma detection helpers
        private boolean justAfterComma = false;
        private int lastCommaPos = -1;
        private int lastCommaLine = -1;
        private int lastCommaColumn = -1;

        Scanner(String input) {
            this.input = input;
            this.length = input.length();
        }

        /* ----------------------------------------------------------------- */
        // Character classification helpers.
        private static boolean isWhitespace(char c) {
            return c == ' ' || c == '\t' || c == '\n' || c == '\r';
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        private static boolean isHexDigit(char c) {
            return isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
        }

        private static boolean isIdentifierStart(char c) {
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == '$';
        }

        private static boolean isIdentifierPart(char c) {
            return isIdentifierStart(c) || isDigit(c);
        }

        /* ----------------------------------------------------------------- */

        /**
         * Core processing loop driving token resolution workflows.
         */
        void parse() {
            while (pos < length) {
                char c = input.charAt(pos);

                // Once the document is fully parsed, only whitespace is legal.
                if (state == State.END) {
                    if (!isWhitespace(c)) {
                        reportError("Multiple roots detected: content found after valid JSON document.",
                                pos, line, column);
                        return; // Halt; everything after is noise.
                    }
                    advance();
                    continue;
                }

                // Whitespace is never significant in JSON (outside strings).
                if (isWhitespace(c)) {
                    advance();
                    continue;
                }

                // Comments are illegal under RFC-8259; detect them early.
                if (c == '/') {
                    scanComment();
                    continue;
                }

                // Dispatch to the state handler.
                switch (state) {
                    case EXPECT_VALUE:
                        handleExpectValue(c);
                        break;
                    case EXPECT_KEY:
                        handleExpectKey(c);
                        break;
                    case EXPECT_COLON:
                        handleExpectColon(c);
                        break;
                    case EXPECT_COMMA_OR_END:
                        handleExpectCommaOrEnd(c);
                        break;
                    default:
                        advance(); // Defensive.
                }
            }

            // EOF validations
            if (errors.isEmpty()) {
                if (!stack.isEmpty()) {
                    ContainerType top = peek();
                    if (top == ContainerType.OBJECT) {
                        reportError("Unclosed object. Expected '\"' or '}' but reached end of input.",
                                pos, line, column);
                    } else {
                        reportError("Unclosed array. Expected value or ']' but reached end of input.",
                                pos, line, column);
                    }
                } else if (state != State.END) {
                    reportError("Incomplete JSON value: document ends prematurely.",
                            pos, line, column);
                }
            }
        }

        /* ----------------------------------------------------------------- */
        // State: EXPECT_VALUE  (root, after '[', after ':', after ',' in array)
        private void handleExpectValue(char c) {
            if (c == '{') {
                stack.add(ContainerType.OBJECT);
                state = State.EXPECT_KEY;
                justAfterComma = false;
                advance();
            } else if (c == '[') {
                stack.add(ContainerType.ARRAY);
                state = State.EXPECT_VALUE;
                justAfterComma = false;
                advance();
            } else if (c == '"') {
                scanString();
                afterValue();
            } else if (c == '\'') {
                // Trap single-quote violations immediately to provide quick-fixes
                reportError("Single quotes used instead of double quotes.",
                        pos, line, column);
                scanSingleQuotedStringRecovery();
                afterValue();
            } else if (c == '-' || isDigit(c)) {
                scanNumber();
                afterValue();
            } else if (c == 't' || c == 'f' || c == 'n') {
                scanLiteral();
                afterValue();
            } else if (c == ']' && !stack.isEmpty() && peek() == ContainerType.ARRAY) {
                // Empty array []  OR  trailing comma [1,]
                if (justAfterComma) {
                    reportError("Trailing comma detected at the end of the array.",
                            lastCommaPos, lastCommaLine, lastCommaColumn);
                }
                pop();
                advance();
                afterValue();
            } else if (c == '}' && !stack.isEmpty() && peek() == ContainerType.OBJECT) {
                reportError("Unexpected '}' in place of a value.", pos, line, column);
                pop();
                advance();
                afterValue();
            } else {
                reportError("Unexpected token '" + c + "' where a value is expected.",
                        pos, line, column);
                justAfterComma = false; // The comma was followed by garbage, not a close bracket.
                advance();
            }
        }

        // State: EXPECT_KEY  (inside object, need property key or '}')
        private void handleExpectKey(char c) {
            if (c == '"') {
                scanString(); // key
                state = State.EXPECT_COLON;
                justAfterComma = false;
            } else if (c == '}') {
                if (justAfterComma) {
                    reportError("Trailing comma detected at the end of the object.",
                            lastCommaPos, lastCommaLine, lastCommaColumn);
                }
                pop();
                advance();
                afterValue();
            } else if (c == '\'') {
                reportError("Single quotes used instead of double quotes for object key.",
                        pos, line, column);
                scanSingleQuotedStringRecovery();
                state = State.EXPECT_COLON;
            } else if (isIdentifierStart(c)) {
                int keyStart = pos;
                int keyLine = line;
                int keyColumn = column;
                scanIdentifier(); // consume so we can continue parsing
                reportError("Key found without surrounding double quotes.",
                        keyStart, keyLine, keyColumn);
                state = State.EXPECT_COLON;
            } else {
                reportError("Expected object key or '}' but found '" + c + "'.",
                        pos, line, column);
                justAfterComma = false;
                advance();
            }
        }

        // State: EXPECT_COLON
        private void handleExpectColon(char c) {
            if (c == ':') {
                state = State.EXPECT_VALUE;
                advance();
            } else {
                reportError("Expected ':' after object key but found '" + c + "'.",
                        pos, line, column);
                advance();
            }
        }

        // State: EXPECT_COMMA_OR_END
        private void handleExpectCommaOrEnd(char c) {
            if (c == ',') {
                justAfterComma = true;
                lastCommaPos = pos;
                lastCommaLine = line;
                lastCommaColumn = column;
                ContainerType top = peek();
                state = (top == ContainerType.OBJECT) ? State.EXPECT_KEY : State.EXPECT_VALUE;
                advance();
            } else if (c == '}') {
                if (peek() == ContainerType.OBJECT) {
                    pop();
                    advance();
                    afterValue();
                } else {
                    reportError("Unexpected '}' outside of object context.",
                            pos, line, column);
                    advance();
                }
            } else if (c == ']') {
                if (peek() == ContainerType.ARRAY) {
                    pop();
                    advance();
                    afterValue();
                } else {
                    reportError("Unexpected ']' outside of array context.",
                            pos, line, column);
                    advance();
                }
            } else {
                reportError("Expected ',' or closing bracket but found '" + c + "'.",
                        pos, line, column);
                advance();
            }
        }

        /* ----------------------------------------------------------------- */
        // Called whenever a complete value (string, number, literal, object, array) finishes.
        private void afterValue() {
            justAfterComma = false;
            state = stack.isEmpty() ? State.END : State.EXPECT_COMMA_OR_END;
        }

        private ContainerType peek() {
            return stack.get(stack.size() - 1);
        }

        private void pop() {
            stack.remove(stack.size() - 1);
        }

        /* ----------------------------------------------------------------- */
        // String scanner – strict RFC-8259 rules.
        private void scanString() {
            // Precondition: input[pos] == '"'
            advance(); // consume opening quote

            while (pos < length) {
                char c = input.charAt(pos);
                if (c == '"') {
                    advance();
                    return;
                } else if (c == '\\') {
                    advance(); // consume backslash
                    if (pos >= length) {
                        reportError("Invalid escape sequence: backslash at end of input.",
                                pos, line, column);
                        return;
                    }
                    char esc = input.charAt(pos);
                    if (esc == '"' || esc == '\\' || esc == '/' ||
                            esc == 'b' || esc == 'f' || esc == 'n' ||
                            esc == 'r' || esc == 't') {
                        advance();
                    } else if (esc == 'u') {
                        advance();
                        for (int i = 0; i < 4; i++) {
                            if (pos >= length) {
                                reportError("Invalid Unicode escape sequence: incomplete.",
                                        pos, line, column);
                                return;
                            }
                            char hex = input.charAt(pos);
                            if (!isHexDigit(hex)) {
                                reportError("Invalid Unicode escape sequence: expected hex digit but found '" + hex + "'.",
                                        pos, line, column);
                                return;
                            }
                            advance();
                        }
                    } else {
                        reportError("Invalid escape sequence: '\\" + esc + "'.",
                                pos, line, column);
                        advance();
                    }
                } else if (c < 0x20) {
                    reportError("Unescaped control character (0x" + Integer.toHexString(c) + ") in string.",
                            pos, line, column);
                    advance();
                } else {
                    advance();
                }
            }
            // Ran off the end without closing quote.
            reportError("Unterminated string literal.", pos, line, column);
        }

        // Recovery scanner for single-quoted strings so we don't spew errors on every char.
        private void scanSingleQuotedStringRecovery() {
            advance(); // consume opening single quote
            while (pos < length) {
                char c = input.charAt(pos);
                if (c == '\'') {
                    advance();
                    return;
                } else if (c == '\\') {
                    advance();
                    if (pos < length) advance();
                } else if (c == '\n' || c == '\r') {
                    reportError("Single-quoted string cannot span multiple lines.",
                            pos, line, column);
                    return;
                } else {
                    advance();
                }
            }
            reportError("Unterminated single-quoted string.", pos, line, column);
        }

        /* ----------------------------------------------------------------- */
        // Number scanner – no leading zeros, strict exponent rules.
        private void scanNumber() {
            int start = pos;
            int startLine = line;
            int startColumn = column;

            if (input.charAt(pos) == '-') {
                advance();
            }

            if (pos >= length || !isDigit(input.charAt(pos))) {
                reportError("Invalid number: expected digit after minus sign.",
                        start, startLine, startColumn);
                return;
            }

            char c = input.charAt(pos);
            advance();// first 1-9 digit
            if (c == '0') {
                if (pos < length && isDigit(input.charAt(pos))) {
                    reportError("Invalid number: leading zeros not allowed.",
                            start, startLine, startColumn);
                    while (pos < length && isDigit(input.charAt(pos))) advance();
                }
            } else {
                while (pos < length && isDigit(input.charAt(pos))) advance();
            }

            // Fractional part
            if (pos < length && input.charAt(pos) == '.') {
                advance();
                int fracStart = pos;
                while (pos < length && isDigit(input.charAt(pos))) advance();
                if (pos == fracStart) {
                    reportError("Invalid number: expected digit after decimal point.",
                            fracStart - 1, line, column);
                }
            }

            // Exponent part
            if (pos < length && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
                advance();
                if (pos < length && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) {
                    advance();
                }
                int expStart = pos;
                while (pos < length && isDigit(input.charAt(pos))) advance();
                if (pos == expStart) {
                    reportError("Invalid number: expected digit in exponent.",
                            expStart - 1, line, column);
                }
            }
        }

        /* ----------------------------------------------------------------- */
        // Literal scanner – true, false, null only.
        private void scanLiteral() {
            int start = pos;
            int startLine = line;
            int startColumn = column;

            char c = input.charAt(pos);
            String expected = (c == 't') ? "true" : (c == 'f') ? "false" : "null";

            for (int i = 0; i < expected.length(); i++) {
                if (pos >= length || input.charAt(pos) != expected.charAt(i)) {
                    reportError("Invalid literal: expected '" + expected + "'.",
                            start, startLine, startColumn);
                    while (pos < length && isIdentifierPart(input.charAt(pos))) advance();
                    return;
                }
                advance();
            }

            // Ensure the literal isn't a prefix of a longer word (e.g. "trueish").
            if (pos < length && isIdentifierPart(input.charAt(pos))) {
                while (pos < length && isIdentifierPart(input.charAt(pos))) advance();
                reportError("Invalid literal: not a valid JSON literal.",
                        start, startLine, startColumn);
            }
        }

        /* ----------------------------------------------------------------- */
        // Comment detection – always an error in strict mode, but we consume it
        // so the user gets a single diagnostic rather than one per character.
        private void scanComment() {
            int start = pos;
            int startLine = line;
            int startColumn = column;
            advance(); // consume '/'

            if (pos >= length) {
                reportError("Unexpected end of input after '/'.", start, startLine, startColumn);
                return;
            }

            char next = input.charAt(pos);
            if (next == '/') {
                reportError("Non-standard JSON comment detected (line comment).",
                        start, startLine, startColumn);
                while (pos < length && input.charAt(pos) != '\n' && input.charAt(pos) != '\r') {
                    advance();
                }
            } else if (next == '*') {
                reportError("Non-standard JSON comment detected (block comment).",
                        start, startLine, startColumn);
                advance(); // consume '*'
                while (pos < length - 1) {
                    if (input.charAt(pos) == '*' && input.charAt(pos + 1) == '/') {
                        advance();
                        advance();
                        return;
                    }
                    advance();
                }
                if (pos >= length - 1) {
                    reportError("Unterminated block comment.", start, startLine, startColumn);
                }
            } else {
                reportError("Unexpected token '/'.", start, startLine, startColumn);
            }
        }

        /* ----------------------------------------------------------------- */
        // Identifiers are only consumed for error-recovery of unquoted keys.
        private void scanIdentifier() {
            while (pos < length && isIdentifierPart(input.charAt(pos))) {
                advance();
            }
        }

        /* ----------------------------------------------------------------- */
        // Position tracking – handles \r\n as a single line terminator.
        private void advance() {
            if (pos >= length) return;
            char c = input.charAt(pos);
            pos++;
            if (c == '\n') {
                line++;
                column = 1;
            } else if (c == '\r') {
                line++;
                column = 1;
                // Collapse CR+LF into a single line break for accurate line counting.
                if (pos < length && input.charAt(pos) == '\n') {
                    pos++;
                }
            } else {
                column++;
            }
        }

        /* ----------------------------------------------------------------- */
        // Snippet builder: extracts ~15 chars on each side, escapes control
        // characters, and builds an aligned ^ pointer.
        private String buildSnippet(int errorPos) {
            int snippetStart = Math.max(0, errorPos - 15);
            int snippetEnd = Math.min(length, errorPos + 15);
            String raw = input.substring(snippetStart, snippetEnd);
            int errorIndexInRaw = errorPos - snippetStart;

            StringBuilder text = new StringBuilder();
            List<Integer> widths = new ArrayList<>();

            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if (c == '\n') {
                    text.append("\\n");
                    widths.add(2);
                } else if (c == '\t') {
                    text.append("\\t");
                    widths.add(2);
                } else if (c == '\r') {
                    text.append("\\r");
                    widths.add(2);
                } else if (c < 0x20) {
                    text.append(String.format("\\u%04x", (int) c));
                    widths.add(6);
                } else {
                    text.append(c);
                    widths.add(1);
                }
            }

            StringBuilder pointer = getPointer(raw, errorIndexInRaw, widths);

            return text + "\n" + pointer;
        }

        /* ----------------------------------------------------------------- */
        // Quick-fix generator: returns the corrected text for the offending segment.
        private String buildFix(String message, int errorPos) {
            if (message.contains("Single quotes used instead of double quotes")) {
                return "\"";
            }
            if (message.contains("Trailing comma detected")) {
                return ""; // Remove the comma
            }
            if (message.contains("Key found without surrounding double quotes")) {
                int end = errorPos;
                while (end < length && isIdentifierPart(input.charAt(end))) end++;
                if (end > errorPos) {
                    return "\"" + input.substring(errorPos, end) + "\"";
                }
                return "\"key\"";
            }
            return "";
        }

        /* ----------------------------------------------------------------- */
        private void reportError(String message, int errorPos, int errorLine, int errorColumn) {
            errors.add(new JsonError(
                    message,
                    errorLine,
                    errorColumn,
                    errorPos,
                    "ERROR",
                    buildSnippet(errorPos),
                    buildFix(message, errorPos)
            ));
        }

        // ---- State machine -------------------------------------------------
        private enum State {
            EXPECT_VALUE,       // Root, after '[', after ':', after ',' in array
            EXPECT_KEY,         // Inside object, need key or '}'
            EXPECT_COLON,       // After object key, need ':'
            EXPECT_COMMA_OR_END,// After value, need ',' or closing bracket
            END                 // Valid document complete; any non-ws = multiple roots
        }

        private enum ContainerType {OBJECT, ARRAY}
    }
}