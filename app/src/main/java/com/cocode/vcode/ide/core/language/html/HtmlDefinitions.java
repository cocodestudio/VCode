package com.cocode.vcode.ide.core.language.html;

import com.cocode.vcode.ide.core.model.CompletionItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HtmlDefinitions {
    public static final List<CompletionItem> GLOBAL_ATTRS = new ArrayList<>();
    public static final List<CompletionItem> EVENT_ATTRS = new ArrayList<>();
    public static final List<CompletionItem> DOCTYPE_ITEMS = new ArrayList<>();
    public static final List<CompletionItem> ENTITY_ITEMS = new ArrayList<>();
    public static final Map<String, String[]> ATTR_VALUES = new HashMap<>();

    static {
        String[][] globals = {
                {"class", "class=\"|\"", "CSS class names"},
                {"id", "id=\"|\"", "Unique element ID"},
                {"style", "style=\"|\"", "Inline CSS styles"},
                {"title", "title=\"|\"", "Tooltip text"},
                {"lang", "lang=\"|\"", "Language code"},
                {"dir", "dir=\"|\"", "Text direction"},
                {"tabindex", "tabindex=\"|\"", "Tab order"},
                {"hidden", "hidden", "Hide element"},
                {"aria-label", "aria-label=\"|\"", "Accessible label"},
                {"aria-hidden", "aria-hidden=\"true\"", "Hide from assistive tech"},
                {"aria-describedby", "aria-describedby=\"|\"", "Accessible description"},
                {"aria-labelledby", "aria-labelledby=\"|\"", "Accessible label (element)"},
                {"aria-live", "aria-live=\"|\"", "Live region"},
                {"aria-expanded", "aria-expanded=\"|\"", "Expanded state"},
                {"aria-controls", "aria-controls=\"|\"", "Controls element"},
                {"aria-current", "aria-current=\"|\"", "Current item indicator"},
                {"aria-disabled", "aria-disabled=\"|\"", "Disabled state"},
                {"aria-required", "aria-required=\"|\"", "Required field"},
                {"aria-invalid", "aria-invalid=\"|\"", "Validation state"},
                {"aria-haspopup", "aria-haspopup=\"|\"", "Has popup"},
                {"aria-selected", "aria-selected=\"|\"", "Selected state"},
                {"aria-checked", "aria-checked=\"|\"", "Checked state"},
                {"aria-valuemin", "aria-valuemin=\"|\"", "Minimum value"},
                {"aria-valuemax", "aria-valuemax=\"|\"", "Maximum value"},
                {"aria-valuenow", "aria-valuenow=\"|\"", "Current value"},
                {"role", "role=\"|\"", "ARIA role"},
                {"data-", "data-|=\"\"", "Custom data attribute"},
                {"draggable", "draggable=\"|\"", "Enable dragging"},
                {"contenteditable", "contenteditable=\"|\"", "Editable content"},
                {"spellcheck", "spellcheck=\"|\"", "Spell check"},
                {"translate", "translate=\"|\"", "Translation hint"},
                {"accesskey", "accesskey=\"|\"", "Keyboard shortcut"},
                {"autocapitalize", "autocapitalize=\"|\"", "Auto-capitalise"},
                {"enterkeyhint", "enterkeyhint=\"|\"", "Enter key label"},
                {"inputmode", "inputmode=\"|\"", "Virtual keyboard type"},
                {"is", "is=\"|\"", "Custom element name"},
                {"part", "part=\"|\"", "CSS shadow part"},
                {"slot", "slot=\"|\"", "Named slot target"},
                {"popover", "popover", "Popover element"},
                {"autofocus", "autofocus", "Auto focus on load"},
                {"inert", "inert", "Non-interactive subtree"},
                {"nonce", "nonce=\"|\"", "CSP nonce"},
        };
        for (String[] g : globals) {
            GLOBAL_ATTRS.add(new CompletionItem(g[0], g[1], g[2], CompletionItem.Type.ATTRIBUTE, 0));
        }

        String[][] events = {
                {"onclick", "onclick=\"|\"", "Mouse click"},
                {"ondblclick", "ondblclick=\"|\"", "Double click"},
                {"onmousedown", "onmousedown=\"|\"", "Mouse button pressed"},
                {"onmouseup", "onmouseup=\"|\"", "Mouse button released"},
                {"onmouseover", "onmouseover=\"|\"", "Mouse enters element"},
                {"onmouseout", "onmouseout=\"|\"", "Mouse leaves element"},
                {"onmousemove", "onmousemove=\"|\"", "Mouse moves over element"},
                {"onmouseenter", "onmouseenter=\"|\"", "Mouse enters (no bubble)"},
                {"onmouseleave", "onmouseleave=\"|\"", "Mouse leaves (no bubble)"},
                {"onkeydown", "onkeydown=\"|\"", "Key pressed"},
                {"onkeyup", "onkeyup=\"|\"", "Key released"},
                {"onkeypress", "onkeypress=\"|\"", "Key press (deprecated)"},
                {"onfocus", "onfocus=\"|\"", "Element gains focus"},
                {"onblur", "onblur=\"|\"", "Element loses focus"},
                {"onfocusin", "onfocusin=\"|\"", "Focus (bubbles)"},
                {"onfocusout", "onfocusout=\"|\"", "Blur (bubbles)"},
                {"onchange", "onchange=\"|\"", "Value changed"},
                {"oninput", "oninput=\"|\"", "Input value changes"},
                {"onsubmit", "onsubmit=\"|\"", "Form submitted"},
                {"onreset", "onreset=\"|\"", "Form reset"},
                {"oninvalid", "oninvalid=\"|\"", "Input validation fails"},
                {"onselect", "onselect=\"|\"", "Text selected"},
                {"onload", "onload=\"|\"", "Resource loaded"},
                {"onerror", "onerror=\"|\"", "Error occurred"},
                {"onresize", "onresize=\"|\"", "Element resized"},
                {"onscroll", "onscroll=\"|\"", "Element scrolled"},
                {"onwheel", "onwheel=\"|\"", "Wheel rotated"},
                {"oncontextmenu", "oncontextmenu=\"|\"", "Context menu opened"},
                {"ondrag", "ondrag=\"|\"", "Dragging"},
                {"ondragstart", "ondragstart=\"|\"", "Drag started"},
                {"ondragend", "ondragend=\"|\"", "Drag ended"},
                {"ondragover", "ondragover=\"|\"", "Dragged over target"},
                {"ondragenter", "ondragenter=\"|\"", "Enters drop target"},
                {"ondragleave", "ondragleave=\"|\"", "Leaves drop target"},
                {"ondrop", "ondrop=\"|\"", "Dropped on target"},
                {"ontouchstart", "ontouchstart=\"|\"", "Touch started"},
                {"ontouchmove", "ontouchmove=\"|\"", "Touch moved"},
                {"ontouchend", "ontouchend=\"|\"", "Touch ended"},
                {"ontouchcancel", "ontouchcancel=\"|\"", "Touch cancelled"},
                {"onpointerdown", "onpointerdown=\"|\"", "Pointer pressed"},
                {"onpointerup", "onpointerup=\"|\"", "Pointer released"},
                {"onpointermove", "onpointermove=\"|\"", "Pointer moved"},
                {"onpointerenter", "onpointerenter=\"|\"", "Pointer enters"},
                {"onpointerleave", "onpointerleave=\"|\"", "Pointer leaves"},
                {"onpointerover", "onpointerover=\"|\"", "Pointer over"},
                {"onpointerout", "onpointerout=\"|\"", "Pointer out"},
                {"onpointercancel", "onpointercancel=\"|\"", "Pointer cancelled"},
                {"onanimationstart", "onanimationstart=\"|\"", "CSS animation starts"},
                {"onanimationend", "onanimationend=\"|\"", "CSS animation ends"},
                {"onanimationiteration", "onanimationiteration=\"|\"", "CSS animation repeats"},
                {"ontransitionend", "ontransitionend=\"|\"", "CSS transition ends"},
                {"oncopy", "oncopy=\"|\"", "Content copied"},
                {"oncut", "oncut=\"|\"", "Content cut"},
                {"onpaste", "onpaste=\"|\"", "Content pasted"},
                {"onplay", "onplay=\"|\"", "Media playback starts"},
                {"onpause", "onpause=\"|\"", "Media playback paused"},
                {"onended", "onended=\"|\"", "Media playback ended"},
                {"ontimeupdate", "ontimeupdate=\"|\"", "Media time changed"},
                {"onvolumechange", "onvolumechange=\"|\"", "Media volume changed"},
                {"oncanplay", "oncanplay=\"|\"", "Media can start"},
                {"ontoggle", "ontoggle=\"|\"", "Details toggled"},
        };
        for (String[] ev : events) {
            EVENT_ATTRS.add(new CompletionItem(ev[0], ev[1], ev[2], CompletionItem.Type.ATTRIBUTE, 0));
        }
        GLOBAL_ATTRS.addAll(EVENT_ATTRS);

        DOCTYPE_ITEMS.add(new CompletionItem("<!DOCTYPE html>", "<!DOCTYPE html>\n", "HTML5 DOCTYPE", CompletionItem.Type.SNIPPET, 0));
        DOCTYPE_ITEMS.add(new CompletionItem("<!-- -->", "<!-- | -->", "Comment", CompletionItem.Type.SNIPPET, 0));

        String[][] entities = {
                {"&amp;", "&amp;", "& ampersand"},
                {"&lt;", "&lt;", "< less-than"},
                {"&gt;", "&gt;", "> greater-than"},
                {"&quot;", "&quot;", "\" quotation mark"},
                {"&apos;", "&apos;", "' apostrophe"},
                {"&nbsp;", "&nbsp;", "Non-breaking space"},
                {"&copy;", "&copy;", "\u00A9 copyright"},
                {"&reg;", "&reg;", "\u00AE registered"},
                {"&trade;", "&trade;", "\u2122 trademark"},
                {"&mdash;", "&mdash;", "\u2014 em dash"},
                {"&ndash;", "&ndash;", "\u2013 en dash"},
                {"&laquo;", "&laquo;", "\u00AB left guillemet"},
                {"&raquo;", "&raquo;", "\u00BB right guillemet"},
                {"&bull;", "&bull;", "\u2022 bullet"},
                {"&hellip;", "&hellip;", "\u2026 horizontal ellipsis"},
                {"&larr;", "&larr;", "\u2190 left arrow"},
                {"&rarr;", "&rarr;", "\u2192 right arrow"},
                {"&uarr;", "&uarr;", "\u2191 up arrow"},
                {"&darr;", "&darr;", "\u2193 down arrow"},
                {"&times;", "&times;", "\u00D7 multiplication"},
                {"&divide;", "&divide;", "\u00F7 division"},
                {"&plusmn;", "&plusmn;", "\u00B1 plus-minus"},
                {"&deg;", "&deg;", "\u00B0 degree"},
                {"&infin;", "&infin;", "\u221E infinity"},
                {"&lsquo;", "&lsquo;", "\u2018 left single quote"},
                {"&rsquo;", "&rsquo;", "\u2019 right single quote"},
                {"&ldquo;", "&ldquo;", "\u201C left double quote"},
                {"&rdquo;", "&rdquo;", "\u201D right double quote"},
                {"&euro;", "&euro;", "\u20AC euro sign"},
                {"&pound;", "&pound;", "\u00A3 pound sign"},
                {"&yen;", "&yen;", "\u00A5 yen sign"},
                {"&cent;", "&cent;", "\u00A2 cent sign"},
                {"&check;", "&check;", "\u2713 check mark"},
                {"&hearts;", "&hearts;", "\u2665 heart"},
                {"&star;", "&star;", "\u2606 star"},
        };
        for (String[] e : entities) {
            ENTITY_ITEMS.add(new CompletionItem(e[0], e[1], e[2], CompletionItem.Type.VALUE, 0));
        }

        ATTR_VALUES.put("type", new String[]{
                "text", "password", "email", "number", "tel", "url", "search", "date", "time", "datetime-local",
                "month", "week", "color", "range", "checkbox", "radio", "file", "hidden", "submit", "reset", "button", "image"
        });
        ATTR_VALUES.put("method", new String[]{"get", "post", "dialog"});
        ATTR_VALUES.put("enctype", new String[]{"application/x-www-form-urlencoded", "multipart/form-data", "text/plain"});
        ATTR_VALUES.put("target", new String[]{"_blank", "_self", "_parent", "_top"});
        ATTR_VALUES.put("rel", new String[]{"noopener", "noreferrer", "nofollow", "stylesheet", "icon", "preload", "prefetch", "canonical", "alternate", "author", "license", "manifest", "dns-prefetch", "preconnect", "modulepreload", "prev", "next", "help", "search"});
        ATTR_VALUES.put("loading", new String[]{"lazy", "eager"});
        ATTR_VALUES.put("decoding", new String[]{"async", "sync", "auto"});
        ATTR_VALUES.put("fetchpriority", new String[]{"high", "low", "auto"});
        ATTR_VALUES.put("crossorigin", new String[]{"anonymous", "use-credentials"});
        ATTR_VALUES.put("referrerpolicy", new String[]{"no-referrer", "no-referrer-when-downgrade", "origin", "origin-when-cross-origin", "same-origin", "strict-origin", "strict-origin-when-cross-origin", "unsafe-url"});
        ATTR_VALUES.put("autocomplete", new String[]{"on", "off", "name", "email", "username", "current-password", "new-password", "one-time-code", "postal-code", "country", "tel", "address-line1", "address-line2", "city", "state", "zip"});
        ATTR_VALUES.put("dir", new String[]{"ltr", "rtl", "auto"});
        ATTR_VALUES.put("draggable", new String[]{"true", "false"});
        ATTR_VALUES.put("contenteditable", new String[]{"true", "false", "plaintext-only"});
        ATTR_VALUES.put("spellcheck", new String[]{"true", "false"});
        ATTR_VALUES.put("translate", new String[]{"yes", "no"});
        ATTR_VALUES.put("scope", new String[]{"col", "row", "colgroup", "rowgroup"});
        ATTR_VALUES.put("wrap", new String[]{"soft", "hard"});
        ATTR_VALUES.put("preload", new String[]{"none", "metadata", "auto"});
        ATTR_VALUES.put("kind", new String[]{"subtitles", "captions", "descriptions", "chapters", "metadata"});
        ATTR_VALUES.put("inputmode", new String[]{"none", "text", "decimal", "numeric", "tel", "search", "email", "url"});
        ATTR_VALUES.put("enterkeyhint", new String[]{"enter", "done", "go", "next", "previous", "search", "send"});
        ATTR_VALUES.put("autocapitalize", new String[]{"off", "none", "on", "sentences", "words", "characters"});
        ATTR_VALUES.put("sandbox", new String[]{"allow-forms", "allow-modals", "allow-popups", "allow-same-origin", "allow-scripts", "allow-top-navigation"});
        ATTR_VALUES.put("aria-live", new String[]{"off", "polite", "assertive"});
        ATTR_VALUES.put("aria-expanded", new String[]{"true", "false"});
        ATTR_VALUES.put("aria-haspopup", new String[]{"true", "false", "menu", "listbox", "tree", "grid", "dialog"});
        ATTR_VALUES.put("aria-current", new String[]{"page", "step", "location", "date", "time", "true", "false"});
        ATTR_VALUES.put("aria-invalid", new String[]{"false", "true", "grammar", "spelling"});
        ATTR_VALUES.put("popover", new String[]{"auto", "manual"});
        ATTR_VALUES.put("shape", new String[]{"rect", "circle", "poly", "default"});
        ATTR_VALUES.put("http-equiv", new String[]{"content-type", "default-style", "refresh", "x-ua-compatible", "content-security-policy"});
        ATTR_VALUES.put("name", new String[]{"viewport", "description", "keywords", "author", "robots", "theme-color", "color-scheme", "generator", "application-name"});
        ATTR_VALUES.put("property", new String[]{"og:title", "og:description", "og:image", "og:url", "og:type", "og:site_name", "og:locale"});
        ATTR_VALUES.put("role", new String[]{"alert", "alertdialog", "application", "article", "banner", "button", "cell", "checkbox", "columnheader", "combobox", "complementary", "contentinfo", "definition", "dialog", "directory", "document", "feed", "figure", "form", "grid", "gridcell", "group", "heading", "img", "link", "list", "listbox", "listitem", "log", "main", "marquee", "math", "menu", "menubar", "menuitem", "menuitemcheckbox", "menuitemradio", "navigation", "none", "note", "option", "presentation", "progressbar", "radio", "radiogroup", "region", "row", "rowgroup", "rowheader", "scrollbar", "search", "searchbox", "separator", "slider", "spinbutton", "status", "switch", "tab", "table", "tablist", "tabpanel", "term", "textbox", "timer", "toolbar", "tooltip", "tree", "treegrid", "treeitem"});
    }
}
