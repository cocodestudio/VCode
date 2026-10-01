package com.cocode.vcode.ide.core.language.html;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Documentation provider for HTML elements, attributes, ARIA roles, and event handlers.
 * Provides reference descriptions and MDN links for hover popups.
 */
public final class HtmlDocumentationProvider {

    public static final class ElementInfo {
        public final String tag;
        public final String description;
        public final String[] commonAttributes;
        public final String mdnUrl;

        public ElementInfo(String tag, String description, String[] commonAttributes, String mdnUrl) {
            this.tag = tag;
            this.description = description;
            this.commonAttributes = commonAttributes != null ? commonAttributes : new String[0];
            this.mdnUrl = mdnUrl;
        }
    }

    public static final class AttributeInfo {
        public final String name;
        public final String description;
        public final String[] values;
        public final String mdnUrl;

        public AttributeInfo(String name, String description, String[] values, String mdnUrl) {
            this.name = name;
            this.description = description;
            this.values = values != null ? values : new String[0];
            this.mdnUrl = mdnUrl;
        }
    }

    private static final Map<String, ElementInfo> ELEMENTS = new HashMap<>();
    private static final Map<String, AttributeInfo> ELEMENT_ATTRIBUTES = new HashMap<>();
    private static final Map<String, AttributeInfo> GLOBAL_ATTRIBUTES = new HashMap<>();
    private static final Map<String, AttributeInfo> ARIA_ATTRIBUTES = new HashMap<>();
    private static final Map<String, AttributeInfo> EVENT_ATTRIBUTES = new HashMap<>();
    private static final Map<String, String> VALUE_DOCS = new HashMap<>();

    static {
        initElements();
        initElementAttributes();
        initGlobalAttributes();
        initAriaAttributes();
        initEventAttributes();
        initValueDocs();
    }

    private HtmlDocumentationProvider() {}

    /**
     * Retrieves markdown documentation for an HTML element by tag name.
     */
    public static String getElementMarkdown(String tagName) {
        if (tagName == null || tagName.isEmpty()) return null;
        String key = tagName.toLowerCase(Locale.US);
        ElementInfo info = ELEMENTS.get(key);
        if (info == null) {
            return "### `<" + key + ">`\n\nHTML Element";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("### `<").append(info.tag).append(">`\n\n");
        sb.append(info.description).append("\n\n");
        if (info.commonAttributes.length > 0) {
            sb.append("**Attributes:** `").append(String.join("`, `", info.commonAttributes)).append("`\n\n");
        }
        if (info.mdnUrl != null && !info.mdnUrl.isEmpty()) {
            sb.append("[MDN Reference](").append(info.mdnUrl).append(")");
        }
        return sb.toString().trim();
    }

    /**
     * Retrieves markdown documentation for an attribute in context of its parent element.
     */
    public static String getAttributeMarkdown(String parentTag, String attrName) {
        if (attrName == null || attrName.isEmpty()) return null;
        String lowerAttr = attrName.toLowerCase(Locale.US);
        String lowerTag = parentTag != null ? parentTag.toLowerCase(Locale.US) : null;

        // 1. Element-specific attribute (e.g. "a.href", "input.type")
        if (lowerTag != null) {
            String elementAttrKey = lowerTag + "." + lowerAttr;
            AttributeInfo info = ELEMENT_ATTRIBUTES.get(elementAttrKey);
            if (info != null) {
                return formatAttributeDoc(lowerTag, info);
            }
        }

        // 2. Global attribute (e.g. "class", "id", "style", "title")
        AttributeInfo globalInfo = GLOBAL_ATTRIBUTES.get(lowerAttr);
        if (globalInfo != null) {
            return formatAttributeDoc(null, globalInfo);
        }

        // 3. ARIA attribute or role (e.g. "aria-label", "role")
        AttributeInfo ariaInfo = ARIA_ATTRIBUTES.get(lowerAttr);
        if (ariaInfo != null) {
            return formatAttributeDoc(null, ariaInfo);
        }

        // 4. Event handler attribute (e.g. "onclick", "onchange")
        AttributeInfo eventInfo = EVENT_ATTRIBUTES.get(lowerAttr);
        if (eventInfo != null) {
            return formatAttributeDoc(null, eventInfo);
        }

        // 5. Data attribute prefix
        if (lowerAttr.startsWith("data-")) {
            return "### `" + attrName + "`\n\n"
                    + "Custom data attribute. Forms a class of attributes called custom data attributes, "
                    + "allowing proprietary information to be exchanged between the HTML and its DOM representation by scripts.\n\n"
                    + "[MDN Reference](https://developer.mozilla.org/docs/Web/HTML/Global_attributes/data-*)";
        }

        // 6. Generic or fallback
        return "### `" + attrName + "`\n\nHTML Attribute";
    }

    /**
     * Retrieves markdown documentation for a specific attribute value.
     */
    public static String getAttributeValueMarkdown(String parentTag, String attrName, String value) {
        if (attrName == null || value == null) return null;
        String cleanVal = value.replace("\"", "").replace("'", "").trim().toLowerCase(Locale.US);
        String lowerAttr = attrName.toLowerCase(Locale.US);
        String lowerTag = parentTag != null ? parentTag.toLowerCase(Locale.US) : "";

        String specificKey = lowerTag + "." + lowerAttr + "." + cleanVal;
        if (VALUE_DOCS.containsKey(specificKey)) {
            return "### `" + cleanVal + "`\n\n" + VALUE_DOCS.get(specificKey);
        }

        String genericKey = lowerAttr + "." + cleanVal;
        if (VALUE_DOCS.containsKey(genericKey)) {
            return "### `" + cleanVal + "`\n\n" + VALUE_DOCS.get(genericKey);
        }

        return null;
    }

    private static String formatAttributeDoc(String tag, AttributeInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append("### `").append(info.name).append("`\n\n");
        if (tag != null && !tag.isEmpty()) {
            sb.append("**Element:** `<").append(tag).append(">`\n\n");
        }
        sb.append(info.description).append("\n\n");
        if (info.values.length > 0) {
            sb.append("**Values:** `").append(String.join("`, `", info.values)).append("`\n\n");
        }
        if (info.mdnUrl != null && !info.mdnUrl.isEmpty()) {
            sb.append("[MDN Reference](").append(info.mdnUrl).append(")");
        }
        return sb.toString().trim();
    }

    private static void initElements() {
        // Document metadata & roots
        putEl("html", "The root (top-level element) of an HTML document. All other elements must be descendants of this element.",
                new String[]{"lang", "dir"}, "https://developer.mozilla.org/docs/Web/HTML/Element/html");
        putEl("head", "Contains machine-readable information (metadata) about the document, like its title, scripts, and style sheets.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/head");
        putEl("title", "Defines the document's title that is shown in a browser's title bar or a page's tab. Contains only text.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/title");
        putEl("base", "Specifies the base URL to use for all relative URLs in a document. There can be only one <base> element in a document.",
                new String[]{"href", "target"}, "https://developer.mozilla.org/docs/Web/HTML/Element/base");
        putEl("link", "Specifies relationships between the current document and an external resource. Most commonly used to link to stylesheets and icons.",
                new String[]{"rel", "href", "as", "type", "media", "crossorigin", "integrity", "sizes"}, "https://developer.mozilla.org/docs/Web/HTML/Element/link");
        putEl("meta", "Represents metadata that cannot be represented by other HTML meta-related elements, like <base>, <link>, <script>, <style> or <title>.",
                new String[]{"name", "content", "charset", "http-equiv", "viewport"}, "https://developer.mozilla.org/docs/Web/HTML/Element/meta");
        putEl("style", "Contains style information for a document, or part of a document. It contains CSS, which is applied to the contents of the document.",
                new String[]{"media", "nonce"}, "https://developer.mozilla.org/docs/Web/HTML/Element/style");
        putEl("body", "Represents the content of an HTML document. There can be only one <body> element in a document.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/body");

        // Content sectioning
        putEl("article", "Represents a self-contained composition in a document, page, application, or site, intended to be independently distributable or reusable.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/article");
        putEl("section", "Represents a generic standalone section of a document, which doesn't have a more specific semantic element to represent it.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/section");
        putEl("nav", "Represents a section of a page whose purpose is to provide navigation links, either within the current document or to other documents.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/nav");
        putEl("aside", "Represents a portion of a document whose content is only indirectly related to the document's main content, like a sidebar.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/aside");
        putEl("h1", "Represents the highest level heading on the page.", new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/Heading_Elements");
        putEl("h2", "Represents a level 2 heading on the page.", new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/Heading_Elements");
        putEl("h3", "Represents a level 3 heading on the page.", new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/Heading_Elements");
        putEl("h4", "Represents a level 4 heading on the page.", new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/Heading_Elements");
        putEl("h5", "Represents a level 5 heading on the page.", new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/Heading_Elements");
        putEl("h6", "Represents the lowest level heading on the page.", new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/Heading_Elements");
        putEl("hgroup", "Represents a heading and related content. It groups a single <h1>–<h6> element with one or more <p> elements.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/hgroup");
        putEl("header", "Represents introductory content, typically a group of introductory or navigational aids.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/header");
        putEl("footer", "Represents a footer for its nearest ancestor sectioning content or sectioning root element.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/footer");
        putEl("address", "Indicates that the enclosed HTML provides contact information for a person or people, or for an organization.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/address");
        putEl("main", "Represents the dominant content of the <body> of a document. Must not be a descendant of <article>, <aside>, <footer>, <header>, or <nav>.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/main");
        putEl("search", "Represents a part of a document or application with form controls or other content specifically for performing a search.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/search");

        // Text content
        putEl("p", "Represents a paragraph. Paragraphs are usually represented in visual media as blocks of text separated from adjacent blocks by blank lines.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/p");
        putEl("hr", "Represents a thematic break between paragraph-level elements (for example, a change of scene in a story, or a shift of topic).",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/hr");
        putEl("pre", "Represents preformatted text which is to be presented exactly as written in the HTML file, typically rendered in a monospace font.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/pre");
        putEl("blockquote", "Indicates that the enclosed text is an extended quotation. Usually visually indented.",
                new String[]{"cite"}, "https://developer.mozilla.org/docs/Web/HTML/Element/blockquote");
        putEl("ol", "Represents an ordered list of items — typically rendered as a numbered list.",
                new String[]{"reversed", "start", "type"}, "https://developer.mozilla.org/docs/Web/HTML/Element/ol");
        putEl("ul", "Represents an unordered list of items, typically rendered as a bulleted list.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/ul");
        putEl("li", "Represents an item in a list. It must be contained in a parent element: an ordered list (<ol>), an unordered list (<ul>), or a menu (<menu>).",
                new String[]{"value"}, "https://developer.mozilla.org/docs/Web/HTML/Element/li");
        putEl("dl", "Represents a description list. Encloses a list of groups of terms (<dt>) and descriptions (<dd>).",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/dl");
        putEl("dt", "Specifies a term in a description or definition list, and as such must be used inside a <dl> element.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/dt");
        putEl("dd", "Provides the description, definition, or value for the preceding term (<dt>) in a description list (<dl>).",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/dd");
        putEl("figure", "Represents self-contained content, potentially with an optional caption, which is specified using the <figcaption> element.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/figure");
        putEl("figcaption", "Represents a caption or legend describing the rest of the contents of its parent <figure> element.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/figcaption");
        putEl("div", "The generic container for flow content. It has no effect on the content or layout until styled using CSS.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/div");
        putEl("menu", "A semantic alternative to <ul>, but treated identically by browsers. Represents an unordered list of items (a menu).",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/menu");

        // Inline text semantics
        putEl("a", "Together with its href attribute, creates a hyperlink to web pages, files, email addresses, locations in the same page, or anything else a URL can address.",
                new String[]{"href", "target", "rel", "download", "type", "referrerpolicy"}, "https://developer.mozilla.org/docs/Web/HTML/Element/a");
        putEl("em", "Marks text that has stress emphasis. Typically presented in italic type.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/em");
        putEl("strong", "Indicates that its contents have strong importance, seriousness, or urgency. Typically displayed in bold.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/strong");
        putEl("small", "Represents side-comments and small print, like copyright and legal text, independent of its styled presentation.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/small");
        putEl("s", "Renders text with a strikethrough, or a line through it. Represents things that are no longer relevant or no longer accurate.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/s");
        putEl("cite", "Used to describe a reference to a cited creative work, and must include the title of that work.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/cite");
        putEl("q", "Indicates that the enclosed text is a short inline quotation. Most modern browsers surround the text with quotation marks.",
                new String[]{"cite"}, "https://developer.mozilla.org/docs/Web/HTML/Element/q");
        putEl("dfn", "Used to indicate the term being defined within the context of a definition phrase or sentence.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/dfn");
        putEl("abbr", "Represents an abbreviation or acronym; the optional title attribute can provide an expansion or description for the abbreviation.",
                new String[]{"title"}, "https://developer.mozilla.org/docs/Web/HTML/Element/abbr");
        putEl("ruby", "Represents small annotations that are rendered above, below, or next to base text, usually used for showing the pronunciation of East Asian characters.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/ruby");
        putEl("rt", "Specifies the ruby text component of a ruby annotation, which is used to provide pronunciation, translation, or transliteration information.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/rt");
        putEl("rp", "Used to provide fall-back parentheses for browsers that do not support display of ruby annotations using the <ruby> element.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/rp");
        putEl("data", "Links a given piece of content with a machine-readable translation. If the content is time- or date-related, the <time> element must be used.",
                new String[]{"value"}, "https://developer.mozilla.org/docs/Web/HTML/Element/data");
        putEl("time", "Represents a specific period in time. May include the datetime attribute to translate dates into machine-readable format.",
                new String[]{"datetime"}, "https://developer.mozilla.org/docs/Web/HTML/Element/time");
        putEl("code", "Displays its contents styled in a fashion intended to indicate that the text is a short fragment of computer code.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/code");
        putEl("var", "Represents the name of a variable in a mathematical expression or a programming context.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/var");
        putEl("samp", "Used to enclose inline text which represents sample (or quoted) output from a computer program.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/samp");
        putEl("kbd", "Represents a span of inline text denoting textual user input from a keyboard, voice input, or any other text entry device.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/kbd");
        putEl("sub", "Specifies inline text which should be displayed as subscript for solely typographical reasons.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/sub");
        putEl("sup", "Specifies inline text which is to be displayed as superscript for solely typographical reasons.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/sup");
        putEl("i", "Represents a range of text that is set off from the normal text for some reason, such as idiomatic text, technical terms, taxonomical designations, etc.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/i");
        putEl("b", "Used to draw the reader's attention to the element's contents, which are not otherwise granted special importance (typically styled in bold).",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/b");
        putEl("u", "Represents a span of inline text which should be rendered in a way that indicates that it has a non-textual annotation (e.g. spelling errors).",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/u");
        putEl("mark", "Represents text which is marked or highlighted for reference or notation purposes, due to the marked passage's relevance in the enclosing context.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/mark");
        putEl("bdi", "Tells the browser's bidirectional algorithm to treat the text it contains in isolation from its surrounding text.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/bdi");
        putEl("bdo", "Overrides the current directionality of text, so that the text within is rendered in a different direction.",
                new String[]{"dir"}, "https://developer.mozilla.org/docs/Web/HTML/Element/bdo");
        putEl("span", "A generic inline container for phrasing content, which does not inherently represent anything. Styled using CSS or targeted with JavaScript.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/span");
        putEl("br", "Produces a line break in text (carriage-return). Useful for writing a poem or an address.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/br");
        putEl("wbr", "Represents a word break opportunity—a position within text where the browser may optionally break a line, though its line-breaking rules would not otherwise create a break at that location.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/wbr");

        // Image and multimedia
        putEl("img", "Embeds an image into the document. Requires src and alt attributes.",
                new String[]{"src", "alt", "width", "height", "loading", "decoding", "srcset", "sizes", "crossorigin", "fetchpriority"}, "https://developer.mozilla.org/docs/Web/HTML/Element/img");
        putEl("picture", "Contains zero or more <source> elements and one <img> element to offer alternative versions of an image for different display/device scenarios.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/picture");
        putEl("source", "Specifies multiple media resources for the <picture>, <audio>, and <video> elements.",
                new String[]{"src", "srcset", "sizes", "media", "type", "width", "height"}, "https://developer.mozilla.org/docs/Web/HTML/Element/source");
        putEl("video", "Embeds a media player which supports video playback into the document.",
                new String[]{"src", "controls", "autoplay", "loop", "muted", "poster", "preload", "width", "height", "playsinline", "crossorigin"}, "https://developer.mozilla.org/docs/Web/HTML/Element/video");
        putEl("audio", "Used to embed sound content in documents. It may contain one or more audio sources, represented using the src attribute or the <source> element.",
                new String[]{"src", "controls", "autoplay", "loop", "muted", "preload", "crossorigin"}, "https://developer.mozilla.org/docs/Web/HTML/Element/audio");
        putEl("track", "Used as a child of the media elements, <audio> and <video>. Lets you specify timed text tracks (or time-based data), for example to overlay subtitles or captions.",
                new String[]{"src", "kind", "srclang", "label", "default"}, "https://developer.mozilla.org/docs/Web/HTML/Element/track");

        // Embedded content
        putEl("iframe", "Represents a nested browsing context, embedding another HTML page into the current one.",
                new String[]{"src", "srcdoc", "name", "sandbox", "allow", "allowfullscreen", "loading", "referrerpolicy", "width", "height"}, "https://developer.mozilla.org/docs/Web/HTML/Element/iframe");
        putEl("embed", "Embeds external content at the specified point in the document. This content is provided by an external application or other source of interactive content.",
                new String[]{"src", "type", "width", "height"}, "https://developer.mozilla.org/docs/Web/HTML/Element/embed");
        putEl("object", "Represents an external resource, which can be treated as an image, a nested browsing context, or a resource to be handled by a plugin.",
                new String[]{"data", "type", "name", "width", "height"}, "https://developer.mozilla.org/docs/Web/HTML/Element/object");
        putEl("canvas", "Use the HTML <canvas> element with either the canvas scripting API or the WebGL API to draw graphics and animations.",
                new String[]{"width", "height"}, "https://developer.mozilla.org/docs/Web/HTML/Element/canvas");

        // SVG integration
        putEl("svg", "Defines an SVG container for 2D scalable vector graphics.",
                new String[]{"viewBox", "width", "height", "xmlns", "fill", "stroke"}, "https://developer.mozilla.org/docs/Web/SVG/Element/svg");
        putEl("path", "The <path> SVG element is the generic element to define a shape. All the basic shapes can be created with a path element.",
                new String[]{"d", "fill", "stroke", "stroke-width"}, "https://developer.mozilla.org/docs/Web/SVG/Element/path");
        putEl("circle", "An SVG basic shape used to draw circles based on a center point and a radius.",
                new String[]{"cx", "cy", "r", "fill", "stroke"}, "https://developer.mozilla.org/docs/Web/SVG/Element/circle");
        putEl("rect", "An SVG basic shape used to create rectangles based on the position of a corner and their width and height.",
                new String[]{"x", "y", "width", "height", "rx", "ry", "fill", "stroke"}, "https://developer.mozilla.org/docs/Web/SVG/Element/rect");
        putEl("g", "An SVG container element used to group other SVG elements.",
                new String[]{"transform", "fill", "stroke", "opacity"}, "https://developer.mozilla.org/docs/Web/SVG/Element/g");

        // Tabular data
        putEl("table", "Represents tabular data — that is, information presented in a two-dimensional table comprised of rows and columns of cells containing data.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/table");
        putEl("caption", "Specifies the title of a table. Always the first descendant of a <table>.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/caption");
        putEl("colgroup", "Defines a group of columns within a table.",
                new String[]{"span"}, "https://developer.mozilla.org/docs/Web/HTML/Element/colgroup");
        putEl("col", "Defines one or more columns in a column group represented by its implicit or explicit parent <colgroup> element.",
                new String[]{"span"}, "https://developer.mozilla.org/docs/Web/HTML/Element/col");
        putEl("tbody", "Encapsulates a set of table rows (<tr> elements), indicating that they comprise the body of the table (<table>).",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/tbody");
        putEl("thead", "Encapsulates a set of rows (<tr> elements) defining the head of the columns of the table.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/thead");
        putEl("tfoot", "Encapsulates a set of rows (<tr> elements) defining the footer of a table.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/tfoot");
        putEl("tr", "Defines a row of cells in a table. The row's cells can then be established using a mix of <td> (data cell) and <th> (header cell) elements.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/tr");
        putEl("td", "Defines a cell of a table that contains data. It participates in the table model.",
                new String[]{"colspan", "rowspan", "headers"}, "https://developer.mozilla.org/docs/Web/HTML/Element/td");
        putEl("th", "Defines a cell as header of a group of table cells. The exact nature of this group is defined by the scope and headers attributes.",
                new String[]{"scope", "colspan", "rowspan", "headers"}, "https://developer.mozilla.org/docs/Web/HTML/Element/th");

        // Forms
        putEl("form", "Represents a document section containing interactive controls for submitting information.",
                new String[]{"action", "method", "enctype", "target", "autocomplete", "novalidate", "name"}, "https://developer.mozilla.org/docs/Web/HTML/Element/form");
        putEl("label", "Represents a caption for an item in a user interface. Associating a <label> with a form control improves accessibility.",
                new String[]{"for"}, "https://developer.mozilla.org/docs/Web/HTML/Element/label");
        putEl("input", "Used to create interactive controls for web-based forms in order to accept data from the user.",
                new String[]{"type", "name", "value", "placeholder", "required", "disabled", "readonly", "checked", "min", "max", "step", "pattern", "maxlength", "minlength", "autofocus", "autocomplete", "form", "multiple", "accept"}, "https://developer.mozilla.org/docs/Web/HTML/Element/input");
        putEl("button", "An interactive element activated by a user with a mouse, keyboard, finger, voice command, or other assistive technology. Once activated, it then performs an action, such as submitting a form or opening a dialog.",
                new String[]{"type", "disabled", "name", "value", "form", "formaction", "formmethod", "popovertarget"}, "https://developer.mozilla.org/docs/Web/HTML/Element/button");
        putEl("select", "Represents a control that provides a menu of options.",
                new String[]{"name", "id", "required", "disabled", "multiple", "size", "autofocus", "form", "autocomplete"}, "https://developer.mozilla.org/docs/Web/HTML/Element/select");
        putEl("datalist", "Contains a set of <option> elements that represent the permissible or recommended options available to choose from within other controls.",
                new String[]{"id"}, "https://developer.mozilla.org/docs/Web/HTML/Element/datalist");
        putEl("optgroup", "Creates a grouping of options within a <select> element.",
                new String[]{"label", "disabled"}, "https://developer.mozilla.org/docs/Web/HTML/Element/optgroup");
        putEl("option", "Used to define an item contained in a <select>, an <optgroup>, or a <datalist> element.",
                new String[]{"value", "selected", "disabled", "label"}, "https://developer.mozilla.org/docs/Web/HTML/Element/option");
        putEl("textarea", "Represents a multi-line plain-text editing control, useful when you want to allow users to enter a sizeable amount of free-form text, for example a comment or feedback form.",
                new String[]{"name", "rows", "cols", "placeholder", "required", "disabled", "readonly", "maxlength", "minlength", "wrap", "autofocus", "autocomplete", "spellcheck"}, "https://developer.mozilla.org/docs/Web/HTML/Element/textarea");
        putEl("output", "Container element into which a site or app can inject the results of a calculation or the outcome of a user action.",
                new String[]{"for", "name", "form"}, "https://developer.mozilla.org/docs/Web/HTML/Element/output");
        putEl("progress", "Displays an indicator showing the completion progress of a task, typically displayed as a progress bar.",
                new String[]{"value", "max"}, "https://developer.mozilla.org/docs/Web/HTML/Element/progress");
        putEl("meter", "Represents either a scalar value within a known range or a fractional value (e.g. disk usage, relevance of a query result).",
                new String[]{"value", "min", "max", "low", "high", "optimum"}, "https://developer.mozilla.org/docs/Web/HTML/Element/meter");
        putEl("fieldset", "Used to group several controls as well as labels (<label>) within a web form.",
                new String[]{"disabled", "form", "name"}, "https://developer.mozilla.org/docs/Web/HTML/Element/fieldset");
        putEl("legend", "Represents a caption for the content of its parent <fieldset>.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/legend");

        // Interactive elements
        putEl("details", "Creates a disclosure widget in which information is visible only when the widget is toggled into an 'open' state. A summary or label must be provided using the <summary> element.",
                new String[]{"open"}, "https://developer.mozilla.org/docs/Web/HTML/Element/details");
        putEl("summary", "Specifies a summary, caption, or legend for a <details> disclosure box.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/summary");
        putEl("dialog", "Represents a dialog box or other interactive component, such as a dismissible alert, inspector, or subwindow.",
                new String[]{"open"}, "https://developer.mozilla.org/docs/Web/HTML/Element/dialog");

        // Scripting
        putEl("script", "Used to embed executable code or data; this is typically used to embed or refer to JavaScript code.",
                new String[]{"src", "type", "async", "defer", "crossorigin", "integrity", "nomodule", "nonce", "referrerpolicy"}, "https://developer.mozilla.org/docs/Web/HTML/Element/script");
        putEl("noscript", "Defines a section of HTML to be inserted if a script type on the page is unsupported or if scripting is currently turned off in the browser.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/noscript");
        putEl("template", "A mechanism for holding HTML that is not to be rendered immediately when a page is loaded but may be instantiated subsequently during runtime using JavaScript.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/template");
        putEl("slot", "A placeholder inside a web component that you can fill with your own markup, which lets you create separate DOM trees and present them together.",
                new String[]{"name"}, "https://developer.mozilla.org/docs/Web/HTML/Element/slot");
    }

    private static void initElementAttributes() {
        // <a> attributes
        putAttr("a.href", "The URL that the hyperlink points to. Links are not restricted to HTTP-based URLs — they can use any URL scheme supported by browsers (e.g. mailto:, tel:, #anchor).",
                new String[]{"https://", "http://", "mailto:", "tel:", "#"}, "https://developer.mozilla.org/docs/Web/HTML/Element/a#href");
        putAttr("a.target", "Where to display the linked URL, as the name for a browsing context (a tab, window, or <iframe>).",
                new String[]{"_blank", "_self", "_parent", "_top"}, "https://developer.mozilla.org/docs/Web/HTML/Element/a#target");
        putAttr("a.rel", "The relationship of the target object to the link object. The value is a space-separated list of link types.",
                new String[]{"noopener", "noreferrer", "nofollow", "alternate", "author", "help", "license"}, "https://developer.mozilla.org/docs/Web/HTML/Element/a#rel");
        putAttr("a.download", "Prompts the user to save the linked URL instead of navigating to it. Can be used with or without a value, which will become the default filename.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/a#download");

        // <input> attributes
        putAttr("input.type", "The type of form control to display. Default is 'text'.",
                new String[]{"text", "password", "checkbox", "radio", "submit", "button", "file", "number", "email", "url", "tel", "search", "date", "time", "datetime-local", "color", "range", "hidden", "image", "reset"}, "https://developer.mozilla.org/docs/Web/HTML/Element/input#type");
        putAttr("input.value", "The initial value of the control. This attribute is optional except when the type attribute is 'checkbox' or 'radio'.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/input#value");
        putAttr("input.name", "The name of the form control, as submitted with the form data.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/input#name");
        putAttr("input.placeholder", "A short hint that describes the expected value of an input field (e.g. a sample value or a short description of the expected format).",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/input#placeholder");
        putAttr("input.checked", "Indicates whether the element should be checked when the page loads (for 'checkbox' and 'radio' types).",
                new String[]{"checked"}, "https://developer.mozilla.org/docs/Web/HTML/Element/input#checked");
        putAttr("input.required", "Specifies that an input field must be filled out before submitting the form.",
                new String[]{"required"}, "https://developer.mozilla.org/docs/Web/HTML/Element/input#required");
        putAttr("input.disabled", "Indicates that the user cannot interact with the input.",
                new String[]{"disabled"}, "https://developer.mozilla.org/docs/Web/HTML/Element/input#disabled");
        putAttr("input.readonly", "Indicates that the user cannot modify the value of the input, though it can still receive focus and be submitted.",
                new String[]{"readonly"}, "https://developer.mozilla.org/docs/Web/HTML/Element/input#readonly");
        putAttr("input.pattern", "A regular expression that the input's value must match in order for the value to pass constraint validation.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/input#pattern");
        putAttr("input.min", "The minimum value permitted for this input in numeric and date/time types.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/input#min");
        putAttr("input.max", "The maximum value permitted for this input in numeric and date/time types.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/input#max");
        putAttr("input.step", "Works with the min and max attributes to limit the increments at which a numeric or date-time value can be set.",
                new String[]{"1", "any"}, "https://developer.mozilla.org/docs/Web/HTML/Element/input#step");
        putAttr("input.maxlength", "The maximum number of characters (as UTF-16 code units) the user can enter into the entry field.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/input#maxlength");
        putAttr("input.minlength", "The minimum number of characters (as UTF-16 code units) the user can enter into the entry field.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/input#minlength");
        putAttr("input.accept", "Defines the file types the file input should accept (e.g. image/*, .pdf, audio/*).",
                new String[]{"image/*", "video/*", "audio/*", ".pdf", ".png", ".jpg"}, "https://developer.mozilla.org/docs/Web/HTML/Element/input#accept");
        putAttr("input.multiple", "Indicates that the user can enter more than one value (valid for file and email types).",
                new String[]{"multiple"}, "https://developer.mozilla.org/docs/Web/HTML/Element/input#multiple");
        putAttr("input.autocomplete", "Specifies what permission the user agent has to provide automated assistance in filling out the field value.",
                new String[]{"on", "off", "name", "email", "username", "current-password", "new-password", "tel", "address-line1"}, "https://developer.mozilla.org/docs/Web/HTML/Element/input#autocomplete");

        // <img> attributes
        putAttr("img.src", "The image URL. Mandatory for the <img> element.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/img#src");
        putAttr("img.alt", "Defines an alternative text description of the image for accessibility and when the image fails to load.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/img#alt");
        putAttr("img.loading", "Indicates how the browser should load the image: 'eager' loads immediately, 'lazy' defers loading until within a distance of viewport.",
                new String[]{"lazy", "eager"}, "https://developer.mozilla.org/docs/Web/HTML/Element/img#loading");
        putAttr("img.decoding", "Provides an image decoding hint to the browser ('async', 'sync', 'auto').",
                new String[]{"async", "sync", "auto"}, "https://developer.mozilla.org/docs/Web/HTML/Element/img#decoding");
        putAttr("img.srcset", "One or more strings separated by commas, indicating a set of possible image sources for the user agent to use.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/img#srcset");
        putAttr("img.sizes", "One or more strings separated by commas, indicating a set of source sizes for responsive images.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/img#sizes");

        // <script> attributes
        putAttr("script.src", "This attribute specifies the URI of an external script; this can be used as an alternative to embedding a script directly within the element.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/script#src");
        putAttr("script.type", "Represents the type of script represented. Standard values include 'module', 'text/javascript', or 'importmap'.",
                new String[]{"module", "text/javascript", "importmap"}, "https://developer.mozilla.org/docs/Web/HTML/Element/script#type");
        putAttr("script.async", "For external scripts, indicates that the browser should fetch the script in parallel and execute it as soon as it is available.",
                new String[]{"async"}, "https://developer.mozilla.org/docs/Web/HTML/Element/script#async");
        putAttr("script.defer", "Indicates that the script is meant to be executed after the document has been parsed, but before firing DOMContentLoaded.",
                new String[]{"defer"}, "https://developer.mozilla.org/docs/Web/HTML/Element/script#defer");

        // <link> attributes
        putAttr("link.rel", "Names a relationship of the linked document to the current document.",
                new String[]{"stylesheet", "icon", "preload", "prefetch", "preconnect", "dns-prefetch", "modulepreload", "manifest", "canonical", "alternate", "author"}, "https://developer.mozilla.org/docs/Web/HTML/Element/link#rel");
        putAttr("link.href", "Specifies the URL of the linked resource.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/link#href");
        putAttr("link.as", "Used when rel='preload' or rel='prefetch' to state the type of content being loaded for resource prioritization and CSP.",
                new String[]{"style", "script", "font", "image", "audio", "video", "document", "fetch"}, "https://developer.mozilla.org/docs/Web/HTML/Element/link#as");

        // <form> attributes
        putAttr("form.action", "The URL that processes the form submission.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/form#action");
        putAttr("form.method", "The HTTP method to submit the form with: 'get' appends data to URL, 'post' sends data in request body, 'dialog' closes dialog.",
                new String[]{"get", "post", "dialog"}, "https://developer.mozilla.org/docs/Web/HTML/Element/form#method");
        putAttr("form.enctype", "Specifies how the form data should be encoded when submitting to the server (when method='post').",
                new String[]{"application/x-www-form-urlencoded", "multipart/form-data", "text/plain"}, "https://developer.mozilla.org/docs/Web/HTML/Element/form#enctype");

        // <button> attributes
        putAttr("button.type", "The default behavior of the button ('submit', 'reset', or 'button').",
                new String[]{"submit", "button", "reset"}, "https://developer.mozilla.org/docs/Web/HTML/Element/button#type");
        putAttr("button.disabled", "Prevents the user from interacting with the button.",
                new String[]{"disabled"}, "https://developer.mozilla.org/docs/Web/HTML/Element/button#disabled");
        putAttr("button.popovertarget", "Turns a <button> into a popover control button; takes the ID of the popover element to toggle.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Element/button#popovertarget");
    }

    private static void initGlobalAttributes() {
        putGlobal("class", "A space-separated list of the case-sensitive classes of the element. Classes allow CSS and JavaScript to select and access specific elements via class selectors or methods like document.getElementsByClassName.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Global_attributes/class");
        putGlobal("id", "Defines an identifier (ID) which must be unique in the whole document. Its purpose is to identify the element when linking, scripting, or styling.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Global_attributes/id");
        putGlobal("style", "Contains CSS styling declarations to be applied directly to the element. It is generally recommended to define styles in separate stylesheets.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Global_attributes/style");
        putGlobal("title", "Contains text representing advisory information related to the element it belongs to. Most browsers display this information as a tooltip.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Global_attributes/title");
        putGlobal("lang", "Helps define the language of an element: the language that non-editable elements are written in, or the language that editable elements should be written in.",
                new String[]{"en", "es", "fr", "de", "zh", "ja", "ru", "hi", "ar"}, "https://developer.mozilla.org/docs/Web/HTML/Global_attributes/lang");
        putGlobal("dir", "An enumerated attribute that indicates the directionality of the element's text ('ltr', 'rtl', or 'auto').",
                new String[]{"ltr", "rtl", "auto"}, "https://developer.mozilla.org/docs/Web/HTML/Global_attributes/dir");
        putGlobal("hidden", "An enumerated attribute indicating that the browser should not render the contents of the element. Values: 'hidden', 'until-found'.",
                new String[]{"hidden", "until-found"}, "https://developer.mozilla.org/docs/Web/HTML/Global_attributes/hidden");
        putGlobal("tabindex", "An integer attribute indicating if the element can take input focus (is focusable), if it should participate to sequential keyboard navigation.",
                new String[]{"0", "-1", "1"}, "https://developer.mozilla.org/docs/Web/HTML/Global_attributes/tabindex");
        putGlobal("contenteditable", "An enumerated attribute indicating if the element should be editable by the user. If so, the browser modifies its widget to allow editing.",
                new String[]{"true", "false", "plaintext-only"}, "https://developer.mozilla.org/docs/Web/HTML/Global_attributes/contenteditable");
        putGlobal("spellcheck", "An enumerated attribute defines whether the element may be checked for spelling errors ('true' or 'false').",
                new String[]{"true", "false"}, "https://developer.mozilla.org/docs/Web/HTML/Global_attributes/spellcheck");
        putGlobal("autofocus", "Indicates that an element should be focused on page load, or when the <dialog> that it is in is displayed.",
                new String[]{"autofocus"}, "https://developer.mozilla.org/docs/Web/HTML/Global_attributes/autofocus");
        putGlobal("inert", "A boolean attribute that indicates that the browser will ignore the element. While inert, the element won't represent any events, will be removed from accessibility tree, and cannot receive focus.",
                new String[]{"inert"}, "https://developer.mozilla.org/docs/Web/HTML/Global_attributes/inert");
        putGlobal("popover", "Used to transform an element into a popover element; takes 'auto' or 'manual' as values.",
                new String[]{"auto", "manual"}, "https://developer.mozilla.org/docs/Web/HTML/Global_attributes/popover");
        putGlobal("draggable", "An enumerated attribute indicating whether the element can be dragged, using the Drag and Drop API ('true' or 'false').",
                new String[]{"true", "false"}, "https://developer.mozilla.org/docs/Web/HTML/Global_attributes/draggable");
        putGlobal("enterkeyhint", "Defines what action label (or icon) to present for the enter key on virtual keyboards.",
                new String[]{"enter", "done", "go", "next", "previous", "search", "send"}, "https://developer.mozilla.org/docs/Web/HTML/Global_attributes/enterkeyhint");
        putGlobal("inputmode", "Provides a hint to browsers about what type of virtual keyboard to display when the user edits this element or its contents.",
                new String[]{"none", "text", "decimal", "numeric", "tel", "search", "email", "url"}, "https://developer.mozilla.org/docs/Web/HTML/Global_attributes/inputmode");
        putGlobal("nonce", "A cryptographic nonce ('number used once') which can be used by Content Security Policy to determine whether or not a given fetch will be allowed to proceed.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Global_attributes/nonce");
        putGlobal("role", "Defines the role of an element in the accessibility tree, allowing screen readers and assistive technology to understand its purpose.",
                new String[]{"button", "dialog", "navigation", "main", "banner", "contentinfo", "alert", "region", "tab", "tabpanel", "presentation", "none"}, "https://developer.mozilla.org/docs/Web/Accessibility/ARIA/Roles");
        putGlobal("slot", "Assigns a slot in a shadow DOM shadow tree to an element: the element with the corresponding slot attribute is assigned to the slot created by the <slot> element whose name attribute matches.",
                new String[0], "https://developer.mozilla.org/docs/Web/HTML/Global_attributes/slot");
    }

    private static void initAriaAttributes() {
        putAria("aria-label", "Defines a string value that labels the current element. Used when a text label is not visible on the screen.",
                new String[0], "https://developer.mozilla.org/docs/Web/Accessibility/ARIA/Attributes/aria-label");
        putAria("aria-labelledby", "Identifies the element (or elements) that labels the current element by referencing ID of the labelling element.",
                new String[0], "https://developer.mozilla.org/docs/Web/Accessibility/ARIA/Attributes/aria-labelledby");
        putAria("aria-describedby", "Identifies the element (or elements) that describes the object, providing a longer descriptive text.",
                new String[0], "https://developer.mozilla.org/docs/Web/Accessibility/ARIA/Attributes/aria-describedby");
        putAria("aria-hidden", "Indicates whether the element is exposed to an accessibility API ('true' or 'false').",
                new String[]{"true", "false"}, "https://developer.mozilla.org/docs/Web/Accessibility/ARIA/Attributes/aria-hidden");
        putAria("aria-expanded", "Indicates whether a grouping element that is expandable is currently expanded or collapsed.",
                new String[]{"true", "false"}, "https://developer.mozilla.org/docs/Web/Accessibility/ARIA/Attributes/aria-expanded");
        putAria("aria-disabled", "Indicates that the element is perceivable but disabled, so it is not editable or otherwise operable.",
                new String[]{"true", "false"}, "https://developer.mozilla.org/docs/Web/Accessibility/ARIA/Attributes/aria-disabled");
        putAria("aria-live", "Indicates that an element will be updated, and describes the types of updates the user agents, assistive technologies, and user can expect from the live region.",
                new String[]{"off", "polite", "assertive"}, "https://developer.mozilla.org/docs/Web/Accessibility/ARIA/Attributes/aria-live");
        putAria("aria-checked", "Indicates the current 'checked' state of checkboxes, radio buttons, and other widgets.",
                new String[]{"true", "false", "mixed"}, "https://developer.mozilla.org/docs/Web/Accessibility/ARIA/Attributes/aria-checked");
        putAria("aria-selected", "Indicates the current 'selected' state of various widgets like tabs and options.",
                new String[]{"true", "false"}, "https://developer.mozilla.org/docs/Web/Accessibility/ARIA/Attributes/aria-selected");
        putAria("aria-controls", "Identifies the element (or elements) whose contents or presence are controlled by the current element.",
                new String[0], "https://developer.mozilla.org/docs/Web/Accessibility/ARIA/Attributes/aria-controls");
        putAria("aria-current", "Indicates the element that represents the current item within a container or set of related elements.",
                new String[]{"page", "step", "location", "date", "time", "true", "false"}, "https://developer.mozilla.org/docs/Web/Accessibility/ARIA/Attributes/aria-current");
        putAria("aria-haspopup", "Indicates the availability and type of interactive popup element, such as menu or dialog, that can be triggered by an element.",
                new String[]{"false", "true", "menu", "listbox", "tree", "grid", "dialog"}, "https://developer.mozilla.org/docs/Web/Accessibility/ARIA/Attributes/aria-haspopup");
        putAria("aria-invalid", "Indicates the entered value does not conform to the format expected by the application.",
                new String[]{"grammar", "false", "spelling", "true"}, "https://developer.mozilla.org/docs/Web/Accessibility/ARIA/Attributes/aria-invalid");
    }

    private static void initEventAttributes() {
        putEvt("onclick", "Fires when the pointing device button is pressed and released on an element, or triggered by keyboard activation.");
        putEvt("ondblclick", "Fires when a pointing device button is clicked twice on a single element.");
        putEvt("onchange", "Fires when the value of an <input>, <select>, or <textarea> element has been changed and committed by the user.");
        putEvt("oninput", "Fires synchronously when the value of an <input>, <select>, or <textarea> element is changed.");
        putEvt("onsubmit", "Fires when a <form> is submitted.");
        putEvt("onreset", "Fires when a <form> is reset.");
        putEvt("onkeydown", "Fires when a key is pressed down on the keyboard.");
        putEvt("onkeyup", "Fires when a key is released on the keyboard.");
        putEvt("onkeypress", "Fires when a key that produces a character value is pressed (deprecated in modern standards).");
        putEvt("onfocus", "Fires when an element has received focus.");
        putEvt("onblur", "Fires when an element has lost focus.");
        putEvt("onmouseenter", "Fires when a pointing device is moved over the element that has the listener attached.");
        putEvt("onmouseleave", "Fires when the pointing device is moved out of the element that has the listener attached.");
        putEvt("onmouseover", "Fires when a pointing device is moved onto the element to which the listener is attached or onto one of its children.");
        putEvt("onmouseout", "Fires when a pointing device is moved off the element to which the listener is attached or off one of its children.");
        putEvt("onmousedown", "Fires when a pointing device button is pressed on an element.");
        putEvt("onmouseup", "Fires when a pointing device button is released over an element.");
        putEvt("onload", "Fires when the whole page has loaded, including all dependent resources such as stylesheets, scripts, and images.");
        putEvt("onerror", "Fires when an error occurs while loading an external file, script, or resource.");
        putEvt("onscroll", "Fires when the document view or an element has been scrolled.");
        putEvt("onresize", "Fires when the document view (window) has been resized.");
        putEvt("oncontextmenu", "Fires when the user attempts to open a context menu (usually via right-click or long-press).");
    }

    private static void initValueDocs() {
        VALUE_DOCS.put("input.type.text", "Single-line plain text input. This is the default if the type attribute is omitted.");
        VALUE_DOCS.put("input.type.password", "Single-line text field whose value is obscured (e.g. replaced by asterisks or dots) to prevent shoulder surfing.");
        VALUE_DOCS.put("input.type.checkbox", "A check box allowing single values to be selected/deselected.");
        VALUE_DOCS.put("input.type.radio", "A radio button, allowing a single value to be selected out of a group of buttons with the same name.");
        VALUE_DOCS.put("input.type.submit", "A button that submits the form when pressed.");
        VALUE_DOCS.put("input.type.button", "A push button with no default behavior displaying the value of the value attribute.");
        VALUE_DOCS.put("input.type.number", "A control for entering a number. Displays a spinner interface and numeric keypad on supporting devices.");
        VALUE_DOCS.put("input.type.email", "A field for editing an email address. Validates email format automatically during form submission.");
        VALUE_DOCS.put("input.type.url", "A field for entering a URL. Looks like a text input, but validates absolute URLs and displays URL-optimized soft keyboards.");
        VALUE_DOCS.put("input.type.file", "A control that lets the user select and upload one or more files from their storage.");
        VALUE_DOCS.put("input.type.hidden", "A control that is not displayed to the user, but whose value is submitted to the server.");

        VALUE_DOCS.put("target._blank", "Opens the linked document in a new tab or window.");
        VALUE_DOCS.put("target._self", "Opens the linked document in the same frame/browsing context as it was clicked (this is default).");
        VALUE_DOCS.put("target._parent", "Opens the linked document in the parent browsing context. If no parent, behaves like '_self'.");
        VALUE_DOCS.put("target._top", "Opens the linked document in the topmost browsing context (full body of the window).");

        VALUE_DOCS.put("method.get", "Appends form data to the URL as query parameters: URL?name=value&name2=value2. Ideal for idempotent queries.");
        VALUE_DOCS.put("method.post", "Sends form data in the HTTP request body. Ideal for creating or modifying data on the server.");

        VALUE_DOCS.put("rel.stylesheet", "Imports an external stylesheet for use in styling the current document.");
        VALUE_DOCS.put("rel.icon", "Specifies the icon (favicon) to represent the document in browser tabs and bookmarks.");
        VALUE_DOCS.put("rel.preload", "Tells the browser to download and cache a resource as soon as possible before it is discovered in DOM parser.");
        VALUE_DOCS.put("rel.noopener", "Prevents the newly opened window from accessing the window.opener property of the original window.");
        VALUE_DOCS.put("rel.noreferrer", "Prevents the browser from sending the Referer HTTP header to the destination URL.");
    }

    private static void putEl(String tag, String desc, String[] attrs, String mdn) {
        ELEMENTS.put(tag, new ElementInfo(tag, desc, attrs, mdn));
    }

    private static void putAttr(String key, String desc, String[] values, String mdn) {
        int dot = key.indexOf('.');
        String attrName = dot >= 0 ? key.substring(dot + 1) : key;
        ELEMENT_ATTRIBUTES.put(key, new AttributeInfo(attrName, desc, values, mdn));
    }

    private static void putGlobal(String name, String desc, String[] values, String mdn) {
        GLOBAL_ATTRIBUTES.put(name, new AttributeInfo(name, desc, values, mdn));
    }

    private static void putAria(String name, String desc, String[] values, String mdn) {
        ARIA_ATTRIBUTES.put(name, new AttributeInfo(name, desc, values, mdn));
    }

    private static void putEvt(String name, String desc) {
        EVENT_ATTRIBUTES.put(name, new AttributeInfo(name, desc, new String[0], "https://developer.mozilla.org/docs/Web/API/Element/" + name + "_event"));
    }
}
