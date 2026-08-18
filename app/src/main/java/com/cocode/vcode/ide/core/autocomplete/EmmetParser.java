package com.cocode.vcode.ide.core.autocomplete;

import androidx.annotation.NonNull;

import com.cocode.vcode.ide.core.language.css.EmmetCssDefinitions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Production-grade Emmet abbreviation expander supporting HTML and CSS.
 *
 * <p>HTML features:
 * <ul>
 *   <li>Tag names, IDs (#), classes (.), multiplication (*), grouping (())</li>
 *   <li>Child (>), sibling (+), climb-up (^) operators</li>
 *   <li>Text content with {curly braces}</li>
 *   <li>Custom attributes with [attr=value]</li>
 *   <li>Item numbering with $ placeholder</li>
 *   <li>Implicit div when starting with . or #</li>
 * </ul>
 *
 * <p>CSS features:
 * <ul>
 *   <li>70+ named shorthand abbreviations (flexbox, grid, positioning, etc.)</li>
 *   <li>Numeric property shorthands (m10 → margin: 10px, w100p → width: 100%)</li>
 *   <li>Multi-value shorthands (m10-20 → margin: 10px 20px)</li>
 * </ul>
 */
public class EmmetParser {

    // ─── HTML Patterns ──────────────────────────────────────────────────────────
    private static final Pattern PAT_ABBR = Pattern.compile("^[a-zA-Z0-9_.#*()+>^\\[\\]=\"{} $!:\\-]+$");
    private static final Pattern PAT_EMMET_PARSE = Pattern.compile(
            "^([a-zA-Z0-9_-]*)(#[a-zA-Z0-9_$\\-]+)?((?:\\.[a-zA-Z0-9_$\\-]+)*)(?:\\[([^]]+)])?(?:\\{([^}]*)\\})?(?:\\*([0-9]+))?$");

    // ─── CSS Patterns ───────────────────────────────────────────────────────────
    private static final Pattern PAT_CSS_NUMERIC = Pattern.compile(
            "^([a-z]+)(-?[0-9]+(?:-[0-9]+)*)([a-z%]*)$");


    // ─── Public API ─────────────────────────────────────────────────────────────

    /**
     * Expands an HTML Emmet abbreviation. Returns null if the abbreviation is invalid.
     */
    public static String expandHtml(String abbr, String boilerplate) {
        if (abbr == null || abbr.trim().isEmpty()) return null;

        if (abbr.equals("!")) {
            if (boilerplate != null && !boilerplate.isEmpty()) return boilerplate;
            return "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n    <meta charset=\"UTF-8\">\n" +
                    "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                    "    <title>Document</title>\n</head>\n<body>\n    |\n</body>\n</html>";
        }

        if (!PAT_ABBR.matcher(abbr).matches()) return null;

        if (abbr.startsWith("lorem")) {
            if (abbr.equals("lorem")) {
                return generateLorem(30);
            }
            try {
                int count = Integer.parseInt(abbr.substring(5));
                return generateLorem(count);
            } catch (NumberFormatException ignored) {
            }
        }

        try {
            return parseEmmet(abbr);
        } catch (Exception e) {
            return null;
        }
    }

    private static final String[] LOREM_WORDS = {
            "lorem", "ipsum", "dolor", "sit", "amet", "consectetur", "adipiscing", "elit", "sed", "do",
            "eiusmod", "tempor", "incididunt", "ut", "labore", "et", "dolore", "magna", "aliqua", "enim",
            "ad", "minim", "veniam", "quis", "nostrud", "exercitation", "ullamco", "laboris", "nisi", "ut",
            "aliquip", "ex", "ea", "commodo", "consequat", "duis", "aute", "irure", "dolor", "in",
            "reprehenderit", "in", "voluptate", "velit", "esse", "cillum", "dolore", "eu", "fugiat", "nulla",
            "pariatur", "excepteur", "sint", "occaecat", "cupidatat", "non", "proident", "sunt", "in", "culpa",
            "qui", "officia", "deserunt", "mollit", "anim", "id", "est", "laborum"
    };

    private static String generateLorem(int count) {
        if (count <= 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            String word = LOREM_WORDS[i % LOREM_WORDS.length];
            if (i == 0) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            } else {
                sb.append(word);
            }
            if (i < count - 1) {
                // Add periods to make it look like sentences occasionally
                if (i > 0 && i % 8 == 0) {
                    sb.append(". ");
                } else {
                    sb.append(" ");
                }
            } else {
                sb.append(".");
            }
        }
        return sb.toString();
    }

    /**
     * Expands a CSS Emmet abbreviation. Returns null if not recognized.
     *
     * <p>Supports:
     * <ul>
     *   <li>Named abbreviations: "df" → "display: flex;"</li>
     *   <li>Numeric shorthands: "m10" → "margin: 10px;", "w100p" → "width: 100%"</li>
     *   <li>Multi-value numeric: "m10-20" → "margin: 10px 20px;"</li>
     *   <li>Zero without unit: "m0" → "margin: 0;"</li>
     * </ul>
     */
    public static String expandCss(String abbr) {
        if (abbr == null || abbr.isEmpty()) return null;

        // 1. Check named abbreviations first (exact match)
        String named = EmmetCssDefinitions.CSS_ABBREVS.get(abbr);
        if (named != null) return named;

        // 2. Numeric property shorthand (e.g. m10, p20-30, w100p)
        Matcher m = PAT_CSS_NUMERIC.matcher(abbr);
        if (m.matches()) {
            String propAbbr = m.group(1);
            String numPart = m.group(2);     // e.g. "10" or "10-20" or "-5"
            String unitSuffix = m.group(3);  // e.g. "" or "p" or "em" or "rem" or "%"

            String property = EmmetCssDefinitions.CSS_PROP_MAP.get(propAbbr);
            if (property == null) return null;

            // Resolve unit
            String unit;
            switch (Objects.requireNonNull(unitSuffix)) {
                case "p":
                case "%":
                    unit = "%";
                    break;
                case "e":
                    unit = "em";
                    break;
                case "r":
                    unit = "rem";
                    break;
                case "x":
                    unit = "px";
                    break;
                case "vh":
                    unit = "vh";
                    break;
                case "vw":
                    unit = "vw";
                    break;
                default:
                    unit = unitSuffix.isEmpty() ? "px" : unitSuffix;
                    break;
            }

            // Handle multi-value (e.g. "10-20-30")
            StringBuilder value = getValue(numPart, property, unit);

            if (value.length() == 0) return null;
            return property + ": " + value + ";";
        }

        return null;
    }

    @NonNull
    private static StringBuilder getValue(String numPart, String property, String unit) {
        String[] parts = Objects.requireNonNull(numPart).split("-");
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            String val = parts[i];
            // Zero doesn't need a unit
            if (val.equals("0")) {
                value.append("0");
            } else {
                // z-index, opacity, font-weight, line-height don't use px
                if (property.equals("z-index") || property.equals("opacity")
                        || property.equals("font-weight") || property.equals("line-height")) {
                    value.append(val);
                } else {
                    value.append(val).append(unit);
                }
            }
            if (i < parts.length - 1) value.append(" ");
        }
        return value;
    }

    // ─── HTML Parser ────────────────────────────────────────────────────────────

    private static String parseEmmet(String abbr) {
        List<Node> roots = new ArrayList<>();
        Node current = null;
        int i = 0;
        int len = abbr.length();

        while (i < len) {
            char c = abbr.charAt(i);

            // Determine the operator
            char op = 0;
            if (c == '>' || c == '+' || c == '^') {
                op = c;
                i++;
                if (i >= len) return null;
            }

            // Handle grouping with parentheses
            if (abbr.charAt(i) == '(') {
                int closeIdx = findMatchingParen(abbr, i);
                if (closeIdx < 0) return null;
                String groupAbbr = abbr.substring(i + 1, closeIdx);
                i = closeIdx + 1;

                // Check for multiplier after group
                int mult = 1;
                if (i < len && abbr.charAt(i) == '*') {
                    i++;
                    int numStart = i;
                    while (i < len && Character.isDigit(abbr.charAt(i))) i++;
                    if (i > numStart) mult = Integer.parseInt(abbr.substring(numStart, i));
                }

                // Recursively expand the group
                String groupExpanded = parseEmmet(groupAbbr);
                if (groupExpanded == null) return null;

                // Create a virtual node containing the group result
                for (int g = 0; g < mult; g++) {
                    Node groupNode = new Node("__group__");
                    groupNode.textContent = groupExpanded.replace("$", String.valueOf(g + 1));

                    if (op == '>' && current != null) {
                        current.addChild(groupNode);
                    } else if (op == '+' && current != null && current.parent != null) {
                        current.parent.addChild(groupNode);
                    } else {
                        roots.add(groupNode);
                    }
                    current = groupNode;
                }
                continue;
            }

            // Handle climb-up (^) — move up in the tree
            if (op == '^') {
                if (current != null && current.parent != null) {
                    current = current.parent;
                    if (current.parent != null) current = current.parent;
                }
                // Don't consume another char — the next iteration will parse the element
                if (abbr.charAt(i) != '>' && abbr.charAt(i) != '+' && abbr.charAt(i) != '^') {
                    // Parse the element at current position
                } else {
                    continue;
                }
            }

            // Extract the element token (tag#id.class[attr]{text}*n)
            int tokenStart = i;
            i = extractToken(abbr, i);
            if (i == tokenStart) return null; // no progress
            String token = abbr.substring(tokenStart, i);

            // Parse the token into nodes
            Node[] nodes = parseNode(token);
            if (nodes == null) return null;

            // Attach nodes based on operator
            if (op == '>' && current != null) {
                for (Node n : nodes) current.addChild(n);
                current = nodes[nodes.length - 1];
            } else if (op == '+' && current != null) {
                Node parent = current.parent;
                if (parent != null) {
                    for (Node n : nodes) parent.addChild(n);
                } else {
                    Collections.addAll(roots, nodes);
                }
                current = nodes[nodes.length - 1];
            } else {
                // First element or after climb-up
                Collections.addAll(roots, nodes);
                current = nodes[nodes.length - 1];
            }
        }

        if (roots.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < roots.size(); r++) {
            renderNode(roots.get(r), sb, 0, r == roots.size() - 1);
        }

        String result = sb.toString();
        // If no cursor marker exists, place it inside the first empty tag
        if (!result.contains("|")) {
            int firstClose = result.indexOf("></");
            if (firstClose != -1) {
                result = result.substring(0, firstClose + 1) + "|" + result.substring(firstClose + 1);
            } else {
                result = result + "|";
            }
        }
        return result;
    }

    private static int extractToken(String abbr, int start) {
        int i = start;
        int len = abbr.length();
        while (i < len) {
            char c = abbr.charAt(i);
            if (c == '>' || c == '+' || c == '^' || c == '(') break;
            if (c == '[') {
                // Skip to closing ]
                int close = abbr.indexOf(']', i);
                if (close < 0) return i;
                i = close + 1;
            } else if (c == '{') {
                // Skip to closing }
                int close = abbr.indexOf('}', i);
                if (close < 0) return i;
                i = close + 1;
            } else {
                i++;
            }
        }
        return i;
    }

    private static int findMatchingParen(String s, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < s.length(); i++) {
            if (s.charAt(i) == '(') depth++;
            else if (s.charAt(i) == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static Node[] parseNode(String str) {
        Matcher m = PAT_EMMET_PARSE.matcher(str);
        if (!m.matches()) return null;

        String tag = m.group(1);
        String idStr = m.group(2);
        String classesStr = m.group(3);
        String attrStr = m.group(4);
        String textContent = m.group(5);
        String multStr = m.group(6);

        // Implicit div when starting with . or #
        if ((tag == null || tag.isEmpty()) && (idStr != null || (classesStr != null && !classesStr.isEmpty()))) {
            tag = "div";
        }
        if (tag == null || tag.isEmpty()) return null;

        String id = (idStr != null && idStr.length() > 1) ? idStr.substring(1) : null;

        List<String> classes = new ArrayList<>();
        if (classesStr != null && !classesStr.isEmpty()) {
            String[] cls = classesStr.substring(1).split("\\.");
            for (String c : cls) if (!c.isEmpty()) classes.add(c);
        }

        int mult = 1;
        if (multStr != null && !multStr.isEmpty()) {
            mult = Integer.parseInt(multStr);
            if (mult > 100) mult = 100; // Safety cap
        }

        Node[] nodes = new Node[mult];
        for (int i = 0; i < mult; i++) {
            Node n = new Node(tag);
            n.id = id;
            if (id != null && mult > 1) n.id = id + (i + 1);
            n.classes = new ArrayList<>(classes);
            if (mult > 1) {
                // Replace $ with item number in classes and text
                for (int j = 0; j < n.classes.size(); j++) {
                    n.classes.set(j, n.classes.get(j).replace("$", String.valueOf(i + 1)));
                }
            }
            if (attrStr != null) n.attributes = attrStr;
            if (textContent != null) {
                n.textContent = mult > 1
                        ? textContent.replace("$", String.valueOf(i + 1))
                        : textContent;
            }
            nodes[i] = n;
        }
        return nodes;
    }

    private static void renderNode(Node node, StringBuilder sb, int indent, boolean isLast) {
        String ind = getIndent(indent);

        // Group nodes render their pre-expanded content directly
        if ("__group__".equals(node.tag)) {
            if (node.textContent != null) {
                String[] lines = node.textContent.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    sb.append(ind).append(lines[i]);
                    if (i < lines.length - 1 || !isLast) sb.append("\n");
                }
            }
            return;
        }

        sb.append(ind).append("<").append(node.tag);
        if (node.id != null) sb.append(" id=\"").append(node.id).append("\"");
        if (node.classes != null && !node.classes.isEmpty()) {
            sb.append(" class=\"");
            for (int i = 0; i < node.classes.size(); i++) {
                sb.append(node.classes.get(i));
                if (i < node.classes.size() - 1) sb.append(" ");
            }
            sb.append("\"");
        }
        if (node.attributes != null) {
            sb.append(" ").append(node.attributes);
        }

        boolean isSelfClosing = isVoidElement(node.tag);

        sb.append(">");
        if (!isSelfClosing) {
            if (node.textContent != null) {
                sb.append(node.textContent).append("</").append(node.tag).append(">");
            } else if (node.children.isEmpty()) {
                sb.append("</").append(node.tag).append(">");
            } else {
                sb.append("\n");
                for (int i = 0; i < node.children.size(); i++) {
                    renderNode(node.children.get(i), sb, indent + 1, i == node.children.size() - 1);
                }
                sb.append(ind).append("</").append(node.tag).append(">");
            }
        }
        if (!isLast) sb.append("\n");
    }

    private static boolean isVoidElement(String tag) {
        switch (tag) {
            case "img":
            case "input":
            case "br":
            case "hr":
            case "meta":
            case "link":
            case "area":
            case "base":
            case "col":
            case "embed":
            case "source":
            case "track":
            case "wbr":
                return true;
            default:
                return false;
        }
    }

    private static String getIndent(int levels) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < levels; i++) sb.append("    ");
        return sb.toString();
    }

    static class Node {
        String tag;
        String id;
        List<String> classes;
        String attributes;
        String textContent;
        List<Node> children = new ArrayList<>();
        Node parent;

        Node(String tag) {
            this.tag = tag;
        }

        void addChild(Node c) {
            c.parent = this;
            children.add(c);
        }
    }
}
