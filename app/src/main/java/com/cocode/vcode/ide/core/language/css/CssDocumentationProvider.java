package com.cocode.vcode.ide.core.language.css;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Documentation provider for CSS properties, pseudo-classes, pseudo-elements,
 * at-rules, functions, and selector specificity.
 * Provides reference documentation and MDN links for hover popups.
 */
public final class CssDocumentationProvider {

    public static final class PropertyInfo {
        public final String name;
        public final String syntax;
        public final String description;
        public final String[] values;
        public final String mdnUrl;

        public PropertyInfo(String name, String syntax, String description, String[] values, String mdnUrl) {
            this.name = name;
            this.syntax = syntax;
            this.description = description;
            this.values = values != null ? values : new String[0];
            this.mdnUrl = mdnUrl;
        }
    }

    public static final class DocEntry {
        public final String label;
        public final String syntax;
        public final String description;
        public final String mdnUrl;

        public DocEntry(String label, String syntax, String description, String mdnUrl) {
            this.label = label;
            this.syntax = syntax;
            this.description = description;
            this.mdnUrl = mdnUrl;
        }
    }

    private static final Map<String, PropertyInfo> PROPERTIES = new HashMap<>();
    private static final Map<String, DocEntry> PSEUDO_CLASSES = new HashMap<>();
    private static final Map<String, DocEntry> PSEUDO_ELEMENTS = new HashMap<>();
    private static final Map<String, DocEntry> AT_RULES = new HashMap<>();
    private static final Map<String, DocEntry> FUNCTIONS = new HashMap<>();

    static {
        initProperties();
        initPseudoClasses();
        initPseudoElements();
        initAtRules();
        initFunctions();
    }

    private CssDocumentationProvider() {}

    /**
     * Returns rich markdown documentation for a CSS property.
     */
    public static String getPropertyMarkdown(String propName) {
        if (propName == null || propName.isEmpty()) return null;
        String key = propName.trim().toLowerCase(Locale.US);
        PropertyInfo info = PROPERTIES.get(key);
        if (info == null) {
            return "### `" + key + "`\n\nCSS Property";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("### `").append(info.name).append("`\n\n");
        sb.append(info.description).append("\n\n");
        if (info.syntax != null && !info.syntax.isEmpty()) {
            sb.append("**Syntax:** `").append(info.syntax).append("`\n\n");
        }
        if (info.values.length > 0) {
            sb.append("**Values:** `").append(String.join("`, `", info.values)).append("`\n\n");
        }
        if (info.mdnUrl != null && !info.mdnUrl.isEmpty()) {
            sb.append("[MDN Reference](").append(info.mdnUrl).append(")");
        }
        return sb.toString().trim();
    }

    /**
     * Returns rich markdown documentation for a CSS pseudo-class (e.g. ":hover", ":nth-child").
     */
    public static String getPseudoClassMarkdown(String pseudoName) {
        if (pseudoName == null || pseudoName.isEmpty()) return null;
        String clean = pseudoName.trim().toLowerCase(Locale.US);
        if (!clean.startsWith(":")) clean = ":" + clean;
        if (clean.startsWith("::")) clean = clean.substring(1); // was pseudo-element

        DocEntry entry = PSEUDO_CLASSES.get(clean);
        if (entry == null) {
            // Check without arguments like :nth-child(2n) -> :nth-child
            int paren = clean.indexOf('(');
            if (paren > 0) {
                entry = PSEUDO_CLASSES.get(clean.substring(0, paren) + "()");
            }
        }
        if (entry == null) {
            return "### `" + clean + "`\n\nCSS Pseudo-class";
        }
        return formatDocEntry(entry, "Pseudo-class");
    }

    /**
     * Returns rich markdown documentation for a CSS pseudo-element (e.g. "::before", "::placeholder").
     */
    public static String getPseudoElementMarkdown(String pseudoName) {
        if (pseudoName == null || pseudoName.isEmpty()) return null;
        String clean = pseudoName.trim().toLowerCase(Locale.US);
        if (!clean.startsWith("::")) {
            clean = clean.startsWith(":") ? ":" + clean : "::" + clean;
        }

        DocEntry entry = PSEUDO_ELEMENTS.get(clean);
        if (entry == null) {
            return "### `" + clean + "`\n\nCSS Pseudo-element";
        }
        return formatDocEntry(entry, "Pseudo-element");
    }

    /**
     * Returns rich markdown documentation for a CSS at-rule (e.g. "@media", "@keyframes").
     */
    public static String getAtRuleMarkdown(String atRuleName) {
        if (atRuleName == null || atRuleName.isEmpty()) return null;
        String clean = atRuleName.trim().toLowerCase(Locale.US);
        if (!clean.startsWith("@")) clean = "@" + clean;

        DocEntry entry = AT_RULES.get(clean);
        if (entry == null) {
            return "### `" + clean + "`\n\nCSS At-rule";
        }
        return formatDocEntry(entry, "At-rule");
    }

    /**
     * Returns rich markdown documentation for a CSS function (e.g. "calc()", "var()", "clamp()").
     */
    public static String getFunctionMarkdown(String funcName) {
        if (funcName == null || funcName.isEmpty()) return null;
        String clean = funcName.trim().toLowerCase(Locale.US);
        if (!clean.endsWith("()")) {
            int open = clean.indexOf('(');
            if (open > 0) clean = clean.substring(0, open) + "()";
            else clean = clean + "()";
        }

        DocEntry entry = FUNCTIONS.get(clean);
        if (entry == null) {
            return "### `" + clean + "`\n\nCSS Function";
        }
        return formatDocEntry(entry, "Function");
    }

    /**
     * Evaluates a CSS token or word at hover position and returns matching documentation.
     */
    public static String getDocForToken(String word) {
        if (word == null || word.isEmpty()) return null;
        String clean = word.trim();

        if (clean.startsWith("::")) {
            return getPseudoElementMarkdown(clean);
        } else if (clean.startsWith(":") && !clean.startsWith("::")) {
            return getPseudoClassMarkdown(clean);
        } else if (clean.startsWith("@")) {
            return getAtRuleMarkdown(clean);
        } else if (clean.endsWith("(") || clean.endsWith("()")) {
            return getFunctionMarkdown(clean);
        } else if (clean.startsWith(".")) {
            return "### `" + clean + "`\n\nClass selector. Matches all elements that have the `" + clean.substring(1) + "` class specified in their `class` attribute.\n\n[MDN Reference](https://developer.mozilla.org/docs/Web/CSS/Class_selectors)";
        } else if (clean.startsWith("#")) {
            return "### `" + clean + "`\n\nID selector. Matches an element based on the value of the element's `id` attribute, which should be unique within the document.\n\n[MDN Reference](https://developer.mozilla.org/docs/Web/CSS/ID_selectors)";
        } else {
            // Check property first
            String lower = clean.toLowerCase(Locale.US);
            if (PROPERTIES.containsKey(lower)) {
                return getPropertyMarkdown(lower);
            }
            if (FUNCTIONS.containsKey(lower + "()")) {
                return getFunctionMarkdown(lower + "()");
            }
            if (PSEUDO_CLASSES.containsKey(":" + lower)) {
                return getPseudoClassMarkdown(":" + lower);
            }
            if (PSEUDO_ELEMENTS.containsKey("::" + lower)) {
                return getPseudoElementMarkdown("::" + lower);
            }
            if (AT_RULES.containsKey("@" + lower)) {
                return getAtRuleMarkdown("@" + lower);
            }
        }
        return null;
    }

    private static String formatDocEntry(DocEntry entry, String category) {
        StringBuilder sb = new StringBuilder();
        sb.append("### `").append(entry.label).append("`\n\n");
        sb.append(entry.description).append("\n\n");
        if (entry.syntax != null && !entry.syntax.isEmpty()) {
            sb.append("**Syntax:** `").append(entry.syntax).append("`\n\n");
        }
        if (entry.mdnUrl != null && !entry.mdnUrl.isEmpty()) {
            sb.append("[MDN Reference](").append(entry.mdnUrl).append(")");
        }
        return sb.toString().trim();
    }

    private static void initProperties() {
        // Layout & Display
        putProp("display", "[ <display-outside> || <display-inside> ] | <display-listitem> | <display-internal> | <display-box> | <display-legacy>",
                "Sets whether an element is treated as a block or inline element and the layout used for its children, such as flow layout, grid or flex.",
                new String[]{"block", "inline", "inline-block", "flex", "inline-flex", "grid", "inline-grid", "flow-root", "none", "contents", "table", "list-item"},
                "https://developer.mozilla.org/docs/Web/CSS/display");
        putProp("position", "static | relative | absolute | sticky | fixed",
                "Sets how an element is positioned in a document. The top, right, bottom, and left properties determine the final location of positioned elements.",
                new String[]{"static", "relative", "absolute", "sticky", "fixed"},
                "https://developer.mozilla.org/docs/Web/CSS/position");
        putProp("top", "<length> | <percentage> | auto",
                "Participates in specifying the vertical position of a positioned element. Has no effect on non-positioned elements.",
                new String[]{"0", "auto", "50%", "100%"}, "https://developer.mozilla.org/docs/Web/CSS/top");
        putProp("bottom", "<length> | <percentage> | auto",
                "Participates in specifying the vertical position of a positioned element. Has no effect on non-positioned elements.",
                new String[]{"0", "auto", "50%", "100%"}, "https://developer.mozilla.org/docs/Web/CSS/bottom");
        putProp("left", "<length> | <percentage> | auto",
                "Participates in specifying the horizontal position of a positioned element. Has no effect on non-positioned elements.",
                new String[]{"0", "auto", "50%", "100%"}, "https://developer.mozilla.org/docs/Web/CSS/left");
        putProp("right", "<length> | <percentage> | auto",
                "Participates in specifying the horizontal position of a positioned element. Has no effect on non-positioned elements.",
                new String[]{"0", "auto", "50%", "100%"}, "https://developer.mozilla.org/docs/Web/CSS/right");
        putProp("inset", "<'top'>{1,4}",
                "A shorthand that corresponds to the top, right, bottom, and/or left properties. Has no effect on non-positioned elements.",
                new String[]{"0", "auto"}, "https://developer.mozilla.org/docs/Web/CSS/inset");
        putProp("z-index", "auto | <integer>",
                "Sets the z-order of a positioned element and its descendants or flex/grid items. Overlapping elements with a larger z-index cover those with a smaller one.",
                new String[]{"auto", "0", "1", "10", "100", "999", "-1"}, "https://developer.mozilla.org/docs/Web/CSS/z-index");
        putProp("float", "left | right | none | inline-start | inline-end",
                "Places an element on the left or right side of its container, allowing text and inline elements to wrap around it.",
                new String[]{"left", "right", "none"}, "https://developer.mozilla.org/docs/Web/CSS/float");
        putProp("clear", "none | left | right | both | inline-start | inline-end",
                "Sets whether an element must be moved below (cleared) floating elements that precede it.",
                new String[]{"none", "left", "right", "both"}, "https://developer.mozilla.org/docs/Web/CSS/clear");
        putProp("visibility", "visible | hidden | collapse",
                "Shows or hides an element without changing the layout of a document (unlike display: none, hidden elements still take up space).",
                new String[]{"visible", "hidden", "collapse"}, "https://developer.mozilla.org/docs/Web/CSS/visibility");
        putProp("opacity", "<number> | <percentage>",
                "Sets the opacity of an element. Opacity is the degree to which content behind an element is hidden, and is the opposite of transparency.",
                new String[]{"0", "0.5", "1", "100%"}, "https://developer.mozilla.org/docs/Web/CSS/opacity");

        // Flexbox
        putProp("flex", "none | [ <'flex-grow'> <'flex-shrink'>? || <'flex-basis'> ]",
                "Shorthand property that sets how a flex item will grow or shrink to fit the space available in its flex container.",
                new String[]{"1", "auto", "none", "0 1 auto", "1 1 0%"}, "https://developer.mozilla.org/docs/Web/CSS/flex");
        putProp("flex-direction", "row | row-reverse | column | column-reverse",
                "Sets how flex items are placed in the flex container defining the main axis and the direction (normal or reversed).",
                new String[]{"row", "row-reverse", "column", "column-reverse"}, "https://developer.mozilla.org/docs/Web/CSS/flex-direction");
        putProp("flex-wrap", "nowrap | wrap | wrap-reverse",
                "Sets whether flex items are forced onto one line or can wrap onto multiple lines.",
                new String[]{"nowrap", "wrap", "wrap-reverse"}, "https://developer.mozilla.org/docs/Web/CSS/flex-wrap");
        putProp("flex-flow", "<'flex-direction'> || <'flex-wrap'>",
                "Shorthand property for flex-direction and flex-wrap, defining a flex container's main and cross axes.",
                new String[]{"row nowrap", "column wrap"}, "https://developer.mozilla.org/docs/Web/CSS/flex-flow");
        putProp("flex-grow", "<number>",
                "Sets the flex grow factor of a flex item's main size.",
                new String[]{"0", "1", "2"}, "https://developer.mozilla.org/docs/Web/CSS/flex-grow");
        putProp("flex-shrink", "<number>",
                "Sets the flex shrink factor of a flex item. If the size of all flex items is larger than the flex container, items shrink to fit according to flex-shrink.",
                new String[]{"1", "0"}, "https://developer.mozilla.org/docs/Web/CSS/flex-shrink");
        putProp("flex-basis", "<'width'>",
                "Sets the initial main size of a flex item before free space is distributed according to flex factors.",
                new String[]{"auto", "0", "100%"}, "https://developer.mozilla.org/docs/Web/CSS/flex-basis");
        putProp("justify-content", "normal | <content-distribution> | <overflow-position>? [ <content-position> | left | right ]",
                "Defines how the browser distributes space between and around content items along the main axis of a flex or grid container.",
                new String[]{"flex-start", "flex-end", "center", "space-between", "space-around", "space-evenly", "start", "end"}, "https://developer.mozilla.org/docs/Web/CSS/justify-content");
        putProp("align-items", "normal | stretch | <baseline-position> | [ <overflow-position>? <self-position> ]",
                "Sets the align-self value on all direct children as a group. Controls alignment along the cross axis in flexbox or block axis in grid.",
                new String[]{"stretch", "center", "flex-start", "flex-end", "baseline", "start", "end"}, "https://developer.mozilla.org/docs/Web/CSS/align-items");
        putProp("align-self", "auto | normal | stretch | <baseline-position> | [ <overflow-position>? <self-position> ]",
                "Overrides a grid or flex item's align-items value to align the item individually along the cross axis.",
                new String[]{"auto", "stretch", "center", "flex-start", "flex-end", "baseline"}, "https://developer.mozilla.org/docs/Web/CSS/align-self");
        putProp("align-content", "normal | <content-distribution> | <overflow-position>? <content-position> | baseline",
                "Sets the distribution of space between and around content items along a flexbox's cross-axis or a grid's block axis.",
                new String[]{"flex-start", "flex-end", "center", "space-between", "space-around", "stretch"}, "https://developer.mozilla.org/docs/Web/CSS/align-content");
        putProp("gap", "<'row-gap'> <'column-gap'>?",
                "Sets the gaps (gutters) between rows and columns in grid, flex, and multi-column layouts.",
                new String[]{"0", "8px", "16px", "1rem", "1.5rem", "2rem"}, "https://developer.mozilla.org/docs/Web/CSS/gap");
        putProp("row-gap", "normal | <length-percentage>",
                "Sets the size of the gap (gutter) between an element's rows in grid and flex layouts.",
                new String[]{"0", "8px", "16px", "1rem"}, "https://developer.mozilla.org/docs/Web/CSS/row-gap");
        putProp("column-gap", "normal | <length-percentage>",
                "Sets the size of the gap (gutter) between an element's columns in grid, flex, and multi-column layouts.",
                new String[]{"0", "8px", "16px", "1rem"}, "https://developer.mozilla.org/docs/Web/CSS/column-gap");

        // Grid
        putProp("grid-template-columns", "none | <track-list> | <auto-track-list>",
                "Defines the line names and track sizing functions of the grid columns.",
                new String[]{"repeat(auto-fit, minmax(200px, 1fr))", "1fr 1fr", "1fr 2fr", "repeat(3, 1fr)", "none"}, "https://developer.mozilla.org/docs/Web/CSS/grid-template-columns");
        putProp("grid-template-rows", "none | <track-list> | <auto-track-list>",
                "Defines the line names and track sizing functions of the grid rows.",
                new String[]{"auto", "1fr", "repeat(3, 1fr)", "none"}, "https://developer.mozilla.org/docs/Web/CSS/grid-template-rows");
        putProp("grid-template-areas", "none | <string>+",
                "Specifies named grid areas, establishing the cells in the grid and assigning them names.",
                new String[]{"none"}, "https://developer.mozilla.org/docs/Web/CSS/grid-template-areas");
        putProp("grid-column", "<grid-line> [ / <grid-line> ]?",
                "Shorthand property for grid-column-start and grid-column-end specifying a grid item's size and location within the grid column.",
                new String[]{"1 / -1", "span 2", "1 / 3"}, "https://developer.mozilla.org/docs/Web/CSS/grid-column");
        putProp("grid-row", "<grid-line> [ / <grid-line> ]?",
                "Shorthand property for grid-row-start and grid-row-end specifying a grid item's size and location within the grid row.",
                new String[]{"1 / -1", "span 2", "1 / 3"}, "https://developer.mozilla.org/docs/Web/CSS/grid-row");

        // Box Model & Sizing
        putProp("box-sizing", "content-box | border-box",
                "Sets how the total width and height of an element is calculated. 'border-box' tells the browser to account for any border and padding in the values you specify for an element's width and height.",
                new String[]{"border-box", "content-box"}, "https://developer.mozilla.org/docs/Web/CSS/box-sizing");
        putProp("width", "auto | <length> | <percentage> | min-content | max-content | fit-content",
                "Sets an element's width. By default, it sets the width of the content area, but if box-sizing is set to border-box, it sets the width of the border area.",
                new String[]{"auto", "100%", "100vw", "max-content", "min-content", "fit-content"}, "https://developer.mozilla.org/docs/Web/CSS/width");
        putProp("height", "auto | <length> | <percentage> | min-content | max-content | fit-content",
                "Sets an element's height. By default, it sets the height of the content area, but if box-sizing is set to border-box, it sets the height of the border area.",
                new String[]{"auto", "100%", "100vh", "max-content", "min-content", "fit-content"}, "https://developer.mozilla.org/docs/Web/CSS/height");
        putProp("max-width", "none | <length-percentage> | min-content | max-content | fit-content",
                "Sets the maximum width of an element. It prevents the used value of the width property from becoming larger than the value specified by max-width.",
                new String[]{"none", "100%", "1200px", "800px"}, "https://developer.mozilla.org/docs/Web/CSS/max-width");
        putProp("min-width", "auto | <length-percentage> | min-content | max-content | fit-content",
                "Sets the minimum width of an element. It prevents the used value of the width property from becoming smaller than the value specified for min-width.",
                new String[]{"0", "auto", "100%"}, "https://developer.mozilla.org/docs/Web/CSS/min-width");
        putProp("max-height", "none | <length-percentage> | min-content | max-content | fit-content",
                "Sets the maximum height of an element. It prevents the used value of the height property from becoming larger than the value specified for max-height.",
                new String[]{"none", "100%", "100vh"}, "https://developer.mozilla.org/docs/Web/CSS/max-height");
        putProp("min-height", "auto | <length-percentage> | min-content | max-content | fit-content",
                "Sets the minimum height of an element. It prevents the used value of the height property from becoming smaller than the value specified for min-height.",
                new String[]{"0", "auto", "100vh", "100%"}, "https://developer.mozilla.org/docs/Web/CSS/min-height");
        putProp("margin", "[ <length> | <percentage> | auto ]{1,4}",
                "Sets the margin area on all four sides of an element. Margins create extra space around an element.",
                new String[]{"0", "0 auto", "auto", "8px", "16px", "1rem"}, "https://developer.mozilla.org/docs/Web/CSS/margin");
        putProp("margin-top", "<length> | <percentage> | auto",
                "Sets the margin area on the top of an element. A positive value puts it farther from its neighbors, while a negative value puts it closer.",
                new String[]{"0", "auto", "8px", "16px", "1rem"}, "https://developer.mozilla.org/docs/Web/CSS/margin-top");
        putProp("margin-bottom", "<length> | <percentage> | auto",
                "Sets the margin area on the bottom of an element.",
                new String[]{"0", "auto", "8px", "16px", "1rem"}, "https://developer.mozilla.org/docs/Web/CSS/margin-bottom");
        putProp("margin-left", "<length> | <percentage> | auto",
                "Sets the margin area on the left of an element.",
                new String[]{"0", "auto", "8px", "16px", "1rem"}, "https://developer.mozilla.org/docs/Web/CSS/margin-left");
        putProp("margin-right", "<length> | <percentage> | auto",
                "Sets the margin area on the right of an element.",
                new String[]{"0", "auto", "8px", "16px", "1rem"}, "https://developer.mozilla.org/docs/Web/CSS/margin-right");
        putProp("padding", "[ <length> | <percentage> ]{1,4}",
                "Sets the padding area on all four sides of an element at once. An element's padding is the space between its content and its border.",
                new String[]{"0", "8px", "16px", "1rem", "1.5rem", "2rem"}, "https://developer.mozilla.org/docs/Web/CSS/padding");
        putProp("padding-top", "<length> | <percentage>",
                "Sets the height of the padding area on the top of an element.",
                new String[]{"0", "8px", "16px", "1rem"}, "https://developer.mozilla.org/docs/Web/CSS/padding-top");
        putProp("padding-bottom", "<length> | <percentage>",
                "Sets the height of the padding area on the bottom of an element.",
                new String[]{"0", "8px", "16px", "1rem"}, "https://developer.mozilla.org/docs/Web/CSS/padding-bottom");
        putProp("padding-left", "<length> | <percentage>",
                "Sets the width of the padding area on the left of an element.",
                new String[]{"0", "8px", "16px", "1rem"}, "https://developer.mozilla.org/docs/Web/CSS/padding-left");
        putProp("padding-right", "<length> | <percentage>",
                "Sets the width of the padding area on the right of an element.",
                new String[]{"0", "8px", "16px", "1rem"}, "https://developer.mozilla.org/docs/Web/CSS/padding-right");

        // Typography & Colors
        putProp("color", "<color>",
                "Sets the foreground color value of an element's text and text decorations, and sets the currentcolor value.",
                new String[]{"inherit", "currentcolor", "transparent", "#ffffff", "#000000", "rgb(0,0,0)", "hsl(0,0%,0%)"}, "https://developer.mozilla.org/docs/Web/CSS/color");
        putProp("font-family", "[ <family-name> | <generic-family> ]#",
                "Specifies a prioritized list of one or more font family names and/or generic family names for the selected element.",
                new String[]{"sans-serif", "serif", "monospace", "system-ui", "inherit"}, "https://developer.mozilla.org/docs/Web/CSS/font-family");
        putProp("font-size", "<absolute-size> | <relative-size> | <length-percentage>",
                "Sets the size of the font. Changing the font size also updates the sizes of the font-size-relative <length> units, such as em and rem.",
                new String[]{"14px", "16px", "18px", "1rem", "1.25rem", "1.5rem", "2rem", "small", "medium", "large"}, "https://developer.mozilla.org/docs/Web/CSS/font-size");
        putProp("font-weight", "<font-weight-absolute> | bolder | lighter",
                "Sets the weight (or boldness) of the font. The weights available depend on the font-family that is currently set.",
                new String[]{"normal", "bold", "100", "200", "300", "400", "500", "600", "700", "800", "900"}, "https://developer.mozilla.org/docs/Web/CSS/font-weight");
        putProp("line-height", "normal | <number> | <length> | <percentage>",
                "Sets the height of a line box. It's commonly used to set the distance between lines of text.",
                new String[]{"normal", "1", "1.2", "1.5", "1.6", "2"}, "https://developer.mozilla.org/docs/Web/CSS/line-height");
        putProp("text-align", "start | end | left | right | center | justify | justify-all | match-parent",
                "Sets the horizontal alignment of the inline-level content inside a block element or table-cell box.",
                new String[]{"left", "right", "center", "justify", "start", "end"}, "https://developer.mozilla.org/docs/Web/CSS/text-align");
        putProp("text-decoration", "<'text-decoration-line'> || <'text-decoration-style'> || <'text-decoration-color'> || <'text-decoration-thickness'>",
                "Sets the appearance of decorative lines used on text (underline, overline, line-through).",
                new String[]{"none", "underline", "line-through", "none"}, "https://developer.mozilla.org/docs/Web/CSS/text-decoration");
        putProp("text-transform", "none | capitalize | uppercase | lowercase | full-width | full-size-kana",
                "Specifies how to capitalize an element's text. It can be used to make text appear in all-uppercase or all-lowercase, or with each word capitalized.",
                new String[]{"none", "capitalize", "uppercase", "lowercase"}, "https://developer.mozilla.org/docs/Web/CSS/text-transform");
        putProp("letter-spacing", "normal | <length>",
                "Sets the horizontal spacing behavior between text characters.",
                new String[]{"normal", "0.05em", "1px", "2px"}, "https://developer.mozilla.org/docs/Web/CSS/letter-spacing");
        putProp("white-space", "normal | nowrap | pre | pre-wrap | pre-line | break-spaces",
                "Sets how white space inside an element is handled.",
                new String[]{"normal", "nowrap", "pre", "pre-wrap", "pre-line"}, "https://developer.mozilla.org/docs/Web/CSS/white-space");
        putProp("overflow-wrap", "normal | break-word | anywhere",
                "Applies to inline elements, setting whether the browser should insert line breaks within an otherwise unbreakable string to prevent text from overflowing its line box.",
                new String[]{"normal", "break-word", "anywhere"}, "https://developer.mozilla.org/docs/Web/CSS/overflow-wrap");
        putProp("word-break", "normal | break-all | keep-all | break-word",
                "Sets whether line breaks appear wherever the text would otherwise overflow its content box.",
                new String[]{"normal", "break-all", "keep-all"}, "https://developer.mozilla.org/docs/Web/CSS/word-break");

        // Backgrounds & Borders
        putProp("background", "[ <bg-layer> , ]* <final-bg-layer>",
                "Shorthand property for background-color, background-image, background-position, background-size, background-repeat, background-origin, background-clip, and background-attachment.",
                new String[]{"none", "transparent", "#ffffff", "url()"}, "https://developer.mozilla.org/docs/Web/CSS/background");
        putProp("background-color", "<color>",
                "Sets the background color of an element.",
                new String[]{"transparent", "#ffffff", "#000000", "inherit"}, "https://developer.mozilla.org/docs/Web/CSS/background-color");
        putProp("background-image", "<bg-image>#",
                "Sets one or more background images on an element (e.g. url(...) or linear-gradient(...)).",
                new String[]{"none", "url()", "linear-gradient()", "radial-gradient()"}, "https://developer.mozilla.org/docs/Web/CSS/background-image");
        putProp("background-size", "<bg-size>#",
                "Specifies the size of the element's background images. The image can be left to its natural size, stretched, or constrained to fit the available space.",
                new String[]{"cover", "contain", "auto", "100% 100%"}, "https://developer.mozilla.org/docs/Web/CSS/background-size");
        putProp("background-position", "<position>#",
                "Sets the initial position for each background image relative to the position layer set by background-origin.",
                new String[]{"center", "top", "bottom", "left", "right"}, "https://developer.mozilla.org/docs/Web/CSS/background-position");
        putProp("background-repeat", "<repeat-style>#",
                "Sets how background images are repeated. A background image can be repeated along the horizontal and vertical axes, or not repeated at all.",
                new String[]{"repeat", "no-repeat", "repeat-x", "repeat-y", "space", "round"}, "https://developer.mozilla.org/docs/Web/CSS/background-repeat");
        putProp("border", "<line-width> || <line-style> || <color>",
                "Shorthand that sets an element's border-width, border-style, and border-color.",
                new String[]{"none", "1px solid black", "1px solid transparent"}, "https://developer.mozilla.org/docs/Web/CSS/border");
        putProp("border-radius", "<length-percentage>{1,4} [ / <length-percentage>{1,4} ]?",
                "Rounds the corners of an element's outer border edge. You can set a single radius to make circular corners, or two radii to make elliptical corners.",
                new String[]{"0", "4px", "8px", "12px", "16px", "50%", "9999px"}, "https://developer.mozilla.org/docs/Web/CSS/border-radius");
        putProp("border-color", "<color>{1,4}",
                "Shorthand property for setting the color of all four sides of an element's border.",
                new String[]{"transparent", "currentColor", "inherit"}, "https://developer.mozilla.org/docs/Web/CSS/border-color");
        putProp("border-style", "<line-style>{1,4}",
                "Shorthand property that sets the line style for all four sides of an element's border.",
                new String[]{"none", "solid", "dashed", "dotted", "double", "groove", "ridge", "inset", "outset"}, "https://developer.mozilla.org/docs/Web/CSS/border-style");
        putProp("border-width", "<line-width>{1,4}",
                "Sets the width of an element's border.",
                new String[]{"medium", "thin", "thick", "0", "1px", "2px"}, "https://developer.mozilla.org/docs/Web/CSS/border-width");
        putProp("box-shadow", "none | <shadow>#",
                "Adds shadow effects around an element's frame. You can set multiple effects separated by commas. A box shadow is described by X and Y offsets relative to the element, blur and spread radius, and color.",
                new String[]{"none", "0 2px 4px rgba(0,0,0,0.1)", "0 4px 6px -1px rgba(0,0,0,0.1)"}, "https://developer.mozilla.org/docs/Web/CSS/box-shadow");
        putProp("outline", "[ <'outline-color'> || <'outline-style'> || <'outline-width'> ]",
                "Shorthand for setting one or more of the individual outline properties (outline-style, outline-width and outline-color) in a single rule.",
                new String[]{"none", "auto", "1px solid currentColor"}, "https://developer.mozilla.org/docs/Web/CSS/outline");

        // Transforms, Transitions & Animations
        putProp("transform", "none | <transform-list>",
                "Lets you rotate, scale, skew, or translate an element. It modifies the coordinate space of the CSS visual formatting model.",
                new String[]{"none", "translate(0, 0)", "scale(1)", "rotate(0deg)"}, "https://developer.mozilla.org/docs/Web/CSS/transform");
        putProp("transition", "<single-transition>#",
                "Shorthand property for transition-property, transition-duration, transition-timing-function, and transition-delay.",
                new String[]{"all 0.3s ease", "opacity 0.2s ease-in-out", "transform 0.2s ease"}, "https://developer.mozilla.org/docs/Web/CSS/transition");
        putProp("animation", "<single-animation>#",
                "Shorthand property that applies an animation between styles. It is a shorthand for animation-name, animation-duration, animation-timing-function, animation-delay, animation-iteration-count, animation-direction, animation-fill-mode, and animation-play-state.",
                new String[]{"none", "spin 1s linear infinite"}, "https://developer.mozilla.org/docs/Web/CSS/animation");
        putProp("cursor", "[ [ <url> [ <x> <y> ]? , ]* [ auto | default | pointer | wait | text | move | not-allowed | grab | grabbing ] ]",
                "Sets the mouse cursor to display when the mouse pointer is over an element.",
                new String[]{"auto", "default", "pointer", "text", "move", "not-allowed", "grab", "grabbing"}, "https://developer.mozilla.org/docs/Web/CSS/cursor");
        putProp("pointer-events", "auto | none",
                "Sets under what circumstances (if any) a particular graphic element can become the target of pointer events.",
                new String[]{"auto", "none"}, "https://developer.mozilla.org/docs/Web/CSS/pointer-events");
        putProp("user-select", "auto | text | none | contain | all",
                "Controls whether the user can select text.",
                new String[]{"auto", "none", "text", "all"}, "https://developer.mozilla.org/docs/Web/CSS/user-select");
        putProp("overflow", "[ visible | hidden | clip | scroll | auto ]{1,2}",
                "Sets what to do when content overflows an element's frame. Shorthand for overflow-x and overflow-y.",
                new String[]{"visible", "hidden", "clip", "scroll", "auto"}, "https://developer.mozilla.org/docs/Web/CSS/overflow");
        putProp("overflow-x", "visible | hidden | clip | scroll | auto",
                "Sets what shows when content overflows a block-level element's left and right edges.",
                new String[]{"visible", "hidden", "scroll", "auto"}, "https://developer.mozilla.org/docs/Web/CSS/overflow-x");
        putProp("overflow-y", "visible | hidden | clip | scroll | auto",
                "Sets what shows when content overflows a block-level element's top and bottom edges.",
                new String[]{"visible", "hidden", "scroll", "auto"}, "https://developer.mozilla.org/docs/Web/CSS/overflow-y");
    }

    private static void initPseudoClasses() {
        putPC(":hover", ":hover",
                "Matches when the user interacts with an element with a pointing device, but does not necessarily activate it. Generally triggered when the user hovers over an element with the cursor.",
                "https://developer.mozilla.org/docs/Web/CSS/:hover");
        putPC(":active", ":active",
                "Matches when an element is being activated by the user. When using a mouse, activation typically starts when the user presses down the primary mouse button.",
                "https://developer.mozilla.org/docs/Web/CSS/:active");
        putPC(":focus", ":focus",
                "Represents an element (such as a form input) that has received focus. It is generally triggered when the user clicks or taps on an element or selects it using the keyboard's Tab key.",
                "https://developer.mozilla.org/docs/Web/CSS/:focus");
        putPC(":focus-visible", ":focus-visible",
                "Matches when an element has focus and the user agent determines via heuristics (like keyboard navigation) that focus should be visibly indicated.",
                "https://developer.mozilla.org/docs/Web/CSS/:focus-visible");
        putPC(":focus-within", ":focus-within",
                "Matches an element if the element itself has focus or if any of its descendants has focus.",
                "https://developer.mozilla.org/docs/Web/CSS/:focus-within");
        putPC(":visited", ":visited",
                "Matches links that have been visited by the user. For privacy reasons, the styles that can be modified using this selector are very limited.",
                "https://developer.mozilla.org/docs/Web/CSS/:visited");
        putPC(":link", ":link",
                "Matches links (elements with an href attribute) that have not yet been visited.",
                "https://developer.mozilla.org/docs/Web/CSS/:link");
        putPC(":target", ":target",
                "Represents a unique element (the target element) with an id matching the URL's fragment.",
                "https://developer.mozilla.org/docs/Web/CSS/:target");
        putPC(":first-child", ":first-child",
                "Represents the first element among a group of sibling elements.",
                "https://developer.mozilla.org/docs/Web/CSS/:first-child");
        putPC(":last-child", ":last-child",
                "Represents the last element among a group of sibling elements.",
                "https://developer.mozilla.org/docs/Web/CSS/:last-child");
        putPC(":only-child", ":only-child",
                "Represents an element that has no siblings at all.",
                "https://developer.mozilla.org/docs/Web/CSS/:only-child");
        putPC(":nth-child()", ":nth-child(An+B [of S]?)",
                "Matches elements based on their position among a group of siblings using an algebraic expression or keywords ('even', 'odd').",
                "https://developer.mozilla.org/docs/Web/CSS/:nth-child");
        putPC(":nth-last-child()", ":nth-last-child(An+B [of S]?)",
                "Matches elements based on their position among a group of siblings, counting from the end.",
                "https://developer.mozilla.org/docs/Web/CSS/:nth-last-child");
        putPC(":first-of-type", ":first-of-type",
                "Represents the first element of its type among a group of sibling elements.",
                "https://developer.mozilla.org/docs/Web/CSS/:first-of-type");
        putPC(":last-of-type", ":last-of-type",
                "Represents the last element of its type among a group of sibling elements.",
                "https://developer.mozilla.org/docs/Web/CSS/:last-of-type");
        putPC(":only-of-type", ":only-of-type",
                "Represents an element that has no other siblings of the same element type.",
                "https://developer.mozilla.org/docs/Web/CSS/:only-of-type");
        putPC(":nth-of-type()", ":nth-of-type(An+B)",
                "Matches elements based on their position among siblings of the same type.",
                "https://developer.mozilla.org/docs/Web/CSS/:nth-of-type");
        putPC(":root", ":root",
                "Matches the root element of a tree representing the document. In HTML, this is always the <html> element and represents high-level custom property scoping.",
                "https://developer.mozilla.org/docs/Web/CSS/:root");
        putPC(":empty", ":empty",
                "Represents any element that has no children at all, other than whitespace characters.",
                "https://developer.mozilla.org/docs/Web/CSS/:empty");
        putPC(":checked", ":checked",
                "Represents any radio (<input type='radio'>), checkbox (<input type='checkbox'>), or option (<option> in a <select>) element that is checked or toggled to an on state.",
                "https://developer.mozilla.org/docs/Web/CSS/:checked");
        putPC(":disabled", ":disabled",
                "Represents any disabled element. An element is disabled if it cannot be activated (selected, clicked on, typed into, etc.) or accept focus.",
                "https://developer.mozilla.org/docs/Web/CSS/:disabled");
        putPC(":enabled", ":enabled",
                "Represents any enabled element. An element is enabled if it can be activated or accept focus.",
                "https://developer.mozilla.org/docs/Web/CSS/:enabled");
        putPC(":required", ":required",
                "Matches any <input>, <select>, or <textarea> element that has the required attribute set on it.",
                "https://developer.mozilla.org/docs/Web/CSS/:required");
        putPC(":optional", ":optional",
                "Matches any <input>, <select>, or <textarea> element that does not have the required attribute set on it.",
                "https://developer.mozilla.org/docs/Web/CSS/:optional");
        putPC(":valid", ":valid",
                "Represents any <form>, <fieldset>, <input> or other form element whose contents validate successfully.",
                "https://developer.mozilla.org/docs/Web/CSS/:valid");
        putPC(":invalid", ":invalid",
                "Represents any <form>, <fieldset>, <input> or other form element whose contents fail to validate.",
                "https://developer.mozilla.org/docs/Web/CSS/:invalid");
        putPC(":not()", ":not(<complex-selector-list>)",
                "The negation pseudo-class represents elements that do not match a list of selectors.",
                "https://developer.mozilla.org/docs/Web/CSS/:not");
        putPC(":is()", ":is(<forgiving-selector-list>)",
                "Takes a selector list as its argument, and selects any element that can be selected by one of the selectors in that list. Useful for grouping.",
                "https://developer.mozilla.org/docs/Web/CSS/:is");
        putPC(":where()", ":where(<forgiving-selector-list>)",
                "Takes a selector list as its argument, and selects any element that can be selected by one of the selectors in that list, with 0 specificity.",
                "https://developer.mozilla.org/docs/Web/CSS/:where");
        putPC(":has()", ":has(<relative-selector-list>)",
                "The relational pseudo-class represents an element if any of the relative selectors match when anchored against the element.",
                "https://developer.mozilla.org/docs/Web/CSS/:has");
    }

    private static void initPseudoElements() {
        putPE("::before", "::before",
                "Creates a pseudo-element that is the first child of the selected element. It is often used to add cosmetic content to an element with the content property. It is inline by default.",
                "https://developer.mozilla.org/docs/Web/CSS/::before");
        putPE("::after", "::after",
                "Creates a pseudo-element that is the last child of the selected element. It is often used to add cosmetic content to an element with the content property. It is inline by default.",
                "https://developer.mozilla.org/docs/Web/CSS/::after");
        putPE("::placeholder", "::placeholder",
                "Represents the placeholder text in an <input> or <textarea> element.",
                "https://developer.mozilla.org/docs/Web/CSS/::placeholder");
        putPE("::selection", "::selection",
                "Applies styles to the part of a document that has been highlighted by the user (such as clicking and dragging the mouse across text).",
                "https://developer.mozilla.org/docs/Web/CSS/::selection");
        putPE("::marker", "::marker",
                "Selects the marker box of a list item, which typically contains a bullet or number.",
                "https://developer.mozilla.org/docs/Web/CSS/::marker");
        putPE("::backdrop", "::backdrop",
                "A box the size of the viewport which is rendered immediately below any element being presented in the top layer (like <dialog> or fullscreen elements).",
                "https://developer.mozilla.org/docs/Web/CSS/::backdrop");
        putPE("::file-selector-button", "::file-selector-button",
                "Represents the button of an <input> of type='file'.",
                "https://developer.mozilla.org/docs/Web/CSS/::file-selector-button");
        putPE("::first-letter", "::first-letter",
                "Applies styles to the first letter of the first line of a block-level element, but only when not preceded by other content (such as images or inline tables).",
                "https://developer.mozilla.org/docs/Web/CSS/::first-letter");
        putPE("::first-line", "::first-line",
                "Applies styles to the first line of a block-level element.",
                "https://developer.mozilla.org/docs/Web/CSS/::first-line");
    }

    private static void initAtRules() {
        putAR("@media", "@media <media-query-list> { <stylesheet> }",
                "Can be used to apply part of a style sheet based on the result of one or more media queries (e.g. screen width, orientation, resolution).",
                "https://developer.mozilla.org/docs/Web/CSS/@media");
        putAR("@keyframes", "@keyframes <custom-ident> { <keyframe-block-list> }",
                "Controls the intermediate steps in a CSS animation sequence by defining styles for keyframes along the animation duration.",
                "https://developer.mozilla.org/docs/Web/CSS/@keyframes");
        putAR("@font-face", "@font-face { <font-description> }",
                "Specifies a custom font with which to display text; the font can be loaded from either a remote server or a locally-installed font.",
                "https://developer.mozilla.org/docs/Web/CSS/@font-face");
        putAR("@import", "@import [ <url> | <string> ] [ layer | supports | <media-query-list> ]* ;",
                "Used to import style rules from other style sheets.",
                "https://developer.mozilla.org/docs/Web/CSS/@import");
        putAR("@supports", "@supports <supports-condition> { <stylesheet> }",
                "Associates a block of statements with a condition consisting of testing for CSS feature support.",
                "https://developer.mozilla.org/docs/Web/CSS/@supports");
        putAR("@layer", "@layer <layer-name> { <stylesheet> }",
                "Declares a cascade layer and can also be used to define the order of precedence in case of multiple cascade layers.",
                "https://developer.mozilla.org/docs/Web/CSS/@layer");
        putAR("@container", "@container <container-condition> { <stylesheet> }",
                "Applies styles to an element based on the dimensions or style of a query container element.",
                "https://developer.mozilla.org/docs/Web/CSS/@container");
        putAR("@page", "@page <page-selector-list>? { <page-body> }",
                "Modifies different aspects of printed pages, such as dimensions, orientation, and margins.",
                "https://developer.mozilla.org/docs/Web/CSS/@page");
        putAR("@charset", "@charset \"<charset>\";",
                "Specifies the character encoding used in the style sheet. It must be the first element in the style sheet.",
                "https://developer.mozilla.org/docs/Web/CSS/@charset");
        putAR("@property", "@property <custom-property-name> { <declaration-list> }",
                "Defines a CSS custom property registration directly in a stylesheet without having to run JavaScript.",
                "https://developer.mozilla.org/docs/Web/CSS/@property");
    }

    private static void initFunctions() {
        putFn("calc()", "calc(<calc-sum>)",
                "Allows you to perform calculations when specifying CSS property values. It can be used anywhere a <length>, <frequency>, <angle>, <time>, <percentage>, <number>, or <integer> is allowed.",
                "https://developer.mozilla.org/docs/Web/CSS/calc");
        putFn("var()", "var(<custom-property-name> , <declaration-value>?)",
                "Inserts the value of a custom property (CSS variable) in place of any part of a value of another property.",
                "https://developer.mozilla.org/docs/Web/CSS/var");
        putFn("min()", "min(<calc-sum>#)",
                "Lets you set the smallest (most negative) value from a list of comma-separated expressions as the value of a CSS property.",
                "https://developer.mozilla.org/docs/Web/CSS/min");
        putFn("max()", "max(<calc-sum>#)",
                "Lets you set the largest (most positive) value from a list of comma-separated expressions as the value of a CSS property.",
                "https://developer.mozilla.org/docs/Web/CSS/max");
        putFn("clamp()", "clamp(<min>, <val>, <max>)",
                "Clamps a middle value between an allowed minimum and maximum. Takes three parameters: a minimum value, a preferred value, and a maximum value.",
                "https://developer.mozilla.org/docs/Web/CSS/clamp");
        putFn("url()", "url(<string> <url-modifier>*)",
                "Used to include a file, such as an image or a font.",
                "https://developer.mozilla.org/docs/Web/CSS/url");
        putFn("linear-gradient()", "linear-gradient([ <angle> | to <side-or-corner> ]? , <color-stop-list>)",
                "Creates an image consisting of a progressive transition between two or more colors along a straight line.",
                "https://developer.mozilla.org/docs/Web/CSS/gradient/linear-gradient");
        putFn("radial-gradient()", "radial-gradient([ <shape> || <size> ]? [ at <position> ]? , <color-stop-list>)",
                "Creates an image consisting of a progressive transition between two or more colors that radiate from an origin.",
                "https://developer.mozilla.org/docs/Web/CSS/gradient/radial-gradient");
        putFn("conic-gradient()", "conic-gradient([ from <angle> ]? [ at <position> ]? , <angular-color-stop-list>)",
                "Creates an image consisting of a gradient with color transitions rotated around a center point.",
                "https://developer.mozilla.org/docs/Web/CSS/gradient/conic-gradient");
        putFn("rgb()", "rgb(<percentage>{3} [ / <alpha-value> ]? ) | rgb(<number>{3} [ / <alpha-value> ]? )",
                "Defines a given color according to its red, green, and blue components, with an optional alpha channel for opacity.",
                "https://developer.mozilla.org/docs/Web/CSS/color_value/rgb");
        putFn("rgba()", "rgba(<percentage>{3} , <alpha-value> ) | rgba(<number>{3} , <alpha-value> )",
                "Defines a color according to its red, green, blue, and alpha components. In modern CSS, rgb() accepts the same syntax.",
                "https://developer.mozilla.org/docs/Web/CSS/color_value/rgb");
        putFn("hsl()", "hsl(<hue> <percentage> <percentage> [ / <alpha-value> ]? )",
                "Defines a color using its hue, saturation, and lightness coordinates, with an optional alpha transparency channel.",
                "https://developer.mozilla.org/docs/Web/CSS/color_value/hsl");
        putFn("hsla()", "hsla(<hue> , <percentage> , <percentage> , <alpha-value> )",
                "Defines a color using its hue, saturation, lightness, and alpha components.",
                "https://developer.mozilla.org/docs/Web/CSS/color_value/hsl");
        putFn("attr()", "attr(<attr-name> <type-or-unit>? [, <fallback> ]?)",
                "Used to retrieve the value of an attribute of the selected element and use it in the style sheet.",
                "https://developer.mozilla.org/docs/Web/CSS/attr");
        putFn("env()", "env(<custom-ident> , <declaration-value>?)",
                "Used to insert the value of a user agent-defined environment variable (e.g. safe-area-inset-top).",
                "https://developer.mozilla.org/docs/Web/CSS/env");
    }

    private static void putProp(String name, String syntax, String desc, String[] values, String mdn) {
        PROPERTIES.put(name.toLowerCase(Locale.US), new PropertyInfo(name, syntax, desc, values, mdn));
    }

    private static void putPC(String label, String syntax, String desc, String mdn) {
        PSEUDO_CLASSES.put(label.toLowerCase(Locale.US), new DocEntry(label, syntax, desc, mdn));
    }

    private static void putPE(String label, String syntax, String desc, String mdn) {
        PSEUDO_ELEMENTS.put(label.toLowerCase(Locale.US), new DocEntry(label, syntax, desc, mdn));
    }

    private static void putAR(String label, String syntax, String desc, String mdn) {
        AT_RULES.put(label.toLowerCase(Locale.US), new DocEntry(label, syntax, desc, mdn));
    }

    private static void putFn(String label, String syntax, String desc, String mdn) {
        FUNCTIONS.put(label.toLowerCase(Locale.US), new DocEntry(label, syntax, desc, mdn));
    }
}
