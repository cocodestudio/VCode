package com.cocode.vcode.ide.core.language.css;

import java.util.HashMap;
import java.util.Map;

/**
 * Lookup table of Emmet CSS abbreviations and their expansions.
 */
public class EmmetCssDefinitions {
    // CSS Named Abbreviations
    public static final Map<String, String> CSS_ABBREVS = new HashMap<>();
    // CSS numeric property map
    public static final Map<String, String> CSS_PROP_MAP = new HashMap<>();

    static {
        // Display
        CSS_ABBREVS.put("d", "display: |;");
        CSS_ABBREVS.put("df", "display: flex;");
        CSS_ABBREVS.put("dif", "display: inline-flex;");
        CSS_ABBREVS.put("db", "display: block;");
        CSS_ABBREVS.put("dib", "display: inline-block;");
        CSS_ABBREVS.put("di", "display: inline;");
        CSS_ABBREVS.put("dn", "display: none;");
        CSS_ABBREVS.put("dg", "display: grid;");
        CSS_ABBREVS.put("dig", "display: inline-grid;");
        CSS_ABBREVS.put("dt", "display: table;");
        CSS_ABBREVS.put("dc", "display: contents;");

        // Flexbox
        CSS_ABBREVS.put("fxd", "flex-direction: |;");
        CSS_ABBREVS.put("fxdr", "flex-direction: row;");
        CSS_ABBREVS.put("fxdc", "flex-direction: column;");
        CSS_ABBREVS.put("fxdrr", "flex-direction: row-reverse;");
        CSS_ABBREVS.put("fxdcr", "flex-direction: column-reverse;");
        CSS_ABBREVS.put("fxw", "flex-wrap: wrap;");
        CSS_ABBREVS.put("fxwn", "flex-wrap: nowrap;");
        CSS_ABBREVS.put("fxg", "flex-grow: |;");
        CSS_ABBREVS.put("fxs", "flex-shrink: |;");
        CSS_ABBREVS.put("fxb", "flex-basis: |;");
        CSS_ABBREVS.put("fx", "flex: |;");

        // Justify & Align
        CSS_ABBREVS.put("jc", "justify-content: |;");
        CSS_ABBREVS.put("jcc", "justify-content: center;");
        CSS_ABBREVS.put("jcsb", "justify-content: space-between;");
        CSS_ABBREVS.put("jcsa", "justify-content: space-around;");
        CSS_ABBREVS.put("jcse", "justify-content: space-evenly;");
        CSS_ABBREVS.put("jcfs", "justify-content: flex-start;");
        CSS_ABBREVS.put("jcfe", "justify-content: flex-end;");
        CSS_ABBREVS.put("ai", "align-items: |;");
        CSS_ABBREVS.put("aic", "align-items: center;");
        CSS_ABBREVS.put("ais", "align-items: stretch;");
        CSS_ABBREVS.put("aifs", "align-items: flex-start;");
        CSS_ABBREVS.put("aife", "align-items: flex-end;");
        CSS_ABBREVS.put("aib", "align-items: baseline;");
        CSS_ABBREVS.put("as", "align-self: |;");
        CSS_ABBREVS.put("asc", "align-self: center;");
        CSS_ABBREVS.put("ac", "align-content: |;");
        CSS_ABBREVS.put("acc", "align-content: center;");
        CSS_ABBREVS.put("ji", "justify-items: |;");
        CSS_ABBREVS.put("js", "justify-self: |;");
        CSS_ABBREVS.put("pi", "place-items: center;");
        CSS_ABBREVS.put("pc", "place-content: center;");

        // Grid
        CSS_ABBREVS.put("gtc", "grid-template-columns: |;");
        CSS_ABBREVS.put("gtr", "grid-template-rows: |;");
        CSS_ABBREVS.put("gg", "grid-gap: |;");
        CSS_ABBREVS.put("gap", "gap: |;");
        CSS_ABBREVS.put("rg", "row-gap: |;");
        CSS_ABBREVS.put("cg", "column-gap: |;");
        CSS_ABBREVS.put("gc", "grid-column: |;");
        CSS_ABBREVS.put("gr", "grid-row: |;");
        CSS_ABBREVS.put("ga", "grid-area: |;");
        CSS_ABBREVS.put("gaf", "grid-auto-flow: |;");
        CSS_ABBREVS.put("gac", "grid-auto-columns: |;");
        CSS_ABBREVS.put("gar", "grid-auto-rows: |;");

        // Position
        CSS_ABBREVS.put("pos", "position: |;");
        CSS_ABBREVS.put("poss", "position: static;");
        CSS_ABBREVS.put("posr", "position: relative;");
        CSS_ABBREVS.put("posa", "position: absolute;");
        CSS_ABBREVS.put("posf", "position: fixed;");
        CSS_ABBREVS.put("posst", "position: sticky;");
        CSS_ABBREVS.put("t", "top: |;");
        CSS_ABBREVS.put("r", "right: |;");
        CSS_ABBREVS.put("b", "bottom: |;");
        CSS_ABBREVS.put("l", "left: |;");
        CSS_ABBREVS.put("z", "z-index: |;");
        CSS_ABBREVS.put("inset", "inset: |;");

        // Margin & Padding
        CSS_ABBREVS.put("m", "margin: |;");
        CSS_ABBREVS.put("mt", "margin-top: |;");
        CSS_ABBREVS.put("mr", "margin-right: |;");
        CSS_ABBREVS.put("mb", "margin-bottom: |;");
        CSS_ABBREVS.put("ml", "margin-left: |;");
        CSS_ABBREVS.put("mx", "margin-inline: |;");
        CSS_ABBREVS.put("my", "margin-block: |;");
        CSS_ABBREVS.put("ma", "margin: auto;");
        CSS_ABBREVS.put("mxa", "margin-inline: auto;");
        CSS_ABBREVS.put("p", "padding: |;");
        CSS_ABBREVS.put("pt", "padding-top: |;");
        CSS_ABBREVS.put("pr", "padding-right: |;");
        CSS_ABBREVS.put("pb", "padding-bottom: |;");
        CSS_ABBREVS.put("pl", "padding-left: |;");
        CSS_ABBREVS.put("px", "padding-inline: |;");
        CSS_ABBREVS.put("py", "padding-block: |;");

        // Width & Height
        CSS_ABBREVS.put("w", "width: |;");
        CSS_ABBREVS.put("h", "height: |;");
        CSS_ABBREVS.put("maw", "max-width: |;");
        CSS_ABBREVS.put("mah", "max-height: |;");
        CSS_ABBREVS.put("miw", "min-width: |;");
        CSS_ABBREVS.put("mih", "min-height: |;");
        CSS_ABBREVS.put("w100", "width: 100%;");
        CSS_ABBREVS.put("h100", "height: 100%;");

        // Typography
        CSS_ABBREVS.put("ff", "font-family: |;");
        CSS_ABBREVS.put("fs", "font-size: |;");
        CSS_ABBREVS.put("fw", "font-weight: |;");
        CSS_ABBREVS.put("fwb", "font-weight: bold;");
        CSS_ABBREVS.put("fwn", "font-weight: normal;");
        CSS_ABBREVS.put("fsi", "font-style: italic;");
        CSS_ABBREVS.put("fsn", "font-style: normal;");
        CSS_ABBREVS.put("ta", "text-align: |;");
        CSS_ABBREVS.put("tac", "text-align: center;");
        CSS_ABBREVS.put("tal", "text-align: left;");
        CSS_ABBREVS.put("tar", "text-align: right;");
        CSS_ABBREVS.put("taj", "text-align: justify;");
        CSS_ABBREVS.put("td", "text-decoration: |;");
        CSS_ABBREVS.put("tdn", "text-decoration: none;");
        CSS_ABBREVS.put("tdu", "text-decoration: underline;");
        CSS_ABBREVS.put("tt", "text-transform: |;");
        CSS_ABBREVS.put("ttu", "text-transform: uppercase;");
        CSS_ABBREVS.put("ttl", "text-transform: lowercase;");
        CSS_ABBREVS.put("ttc", "text-transform: capitalize;");
        CSS_ABBREVS.put("lh", "line-height: |;");
        CSS_ABBREVS.put("ls", "letter-spacing: |;");
        CSS_ABBREVS.put("ws", "white-space: |;");
        CSS_ABBREVS.put("wsnw", "white-space: nowrap;");
        CSS_ABBREVS.put("wof", "word-break: break-all;");
        CSS_ABBREVS.put("tov", "text-overflow: ellipsis;");

        // Background
        CSS_ABBREVS.put("bg", "background: |;");
        CSS_ABBREVS.put("bgc", "background-color: |;");
        CSS_ABBREVS.put("bgi", "background-image: url(|);");
        CSS_ABBREVS.put("bgp", "background-position: |;");
        CSS_ABBREVS.put("bgs", "background-size: |;");
        CSS_ABBREVS.put("bgsc", "background-size: cover;");
        CSS_ABBREVS.put("bgr", "background-repeat: |;");
        CSS_ABBREVS.put("bgrn", "background-repeat: no-repeat;");

        // Border
        CSS_ABBREVS.put("bd", "border: |;");
        CSS_ABBREVS.put("bdn", "border: none;");
        CSS_ABBREVS.put("bds", "border: 1px solid |;");
        CSS_ABBREVS.put("bdt", "border-top: |;");
        CSS_ABBREVS.put("bdr", "border-right: |;");
        CSS_ABBREVS.put("bdb", "border-bottom: |;");
        CSS_ABBREVS.put("bdl", "border-left: |;");
        CSS_ABBREVS.put("bdrs", "border-radius: |;");
        CSS_ABBREVS.put("br", "border-radius: |;");
        CSS_ABBREVS.put("brc", "border-radius: 50%;");
        CSS_ABBREVS.put("bxsh", "box-shadow: |;");
        CSS_ABBREVS.put("bxshn", "box-shadow: none;");

        // Overflow
        CSS_ABBREVS.put("ov", "overflow: |;");
        CSS_ABBREVS.put("ovh", "overflow: hidden;");
        CSS_ABBREVS.put("ova", "overflow: auto;");
        CSS_ABBREVS.put("ovs", "overflow: scroll;");
        CSS_ABBREVS.put("ovv", "overflow: visible;");
        CSS_ABBREVS.put("ovx", "overflow-x: |;");
        CSS_ABBREVS.put("ovy", "overflow-y: |;");

        // Visibility & Opacity
        CSS_ABBREVS.put("v", "visibility: |;");
        CSS_ABBREVS.put("vh", "visibility: hidden;");
        CSS_ABBREVS.put("vv", "visibility: visible;");
        CSS_ABBREVS.put("op", "opacity: |;");

        // Cursor & Pointer Events
        CSS_ABBREVS.put("cur", "cursor: |;");
        CSS_ABBREVS.put("curp", "cursor: pointer;");
        CSS_ABBREVS.put("curd", "cursor: default;");
        CSS_ABBREVS.put("pe", "pointer-events: |;");
        CSS_ABBREVS.put("pen", "pointer-events: none;");
        CSS_ABBREVS.put("us", "user-select: none;");

        // Transition & Animation
        CSS_ABBREVS.put("trs", "transition: |;");
        CSS_ABBREVS.put("trsa", "transition: all 0.3s ease;");
        CSS_ABBREVS.put("anim", "animation: |;");
        CSS_ABBREVS.put("animd", "animation-duration: |;");
        CSS_ABBREVS.put("tf", "transform: |;");

        // Box sizing & misc
        CSS_ABBREVS.put("bxz", "box-sizing: border-box;");
        CSS_ABBREVS.put("ct", "content: \"|\"");
        CSS_ABBREVS.put("ol", "outline: |;");
        CSS_ABBREVS.put("oln", "outline: none;");
        CSS_ABBREVS.put("rsz", "resize: |;");
        CSS_ABBREVS.put("ap", "appearance: none;");
        CSS_ABBREVS.put("fil", "filter: |;");
        CSS_ABBREVS.put("obf", "object-fit: |;");
        CSS_ABBREVS.put("obfc", "object-fit: cover;");
        CSS_ABBREVS.put("obfct", "object-fit: contain;");
        CSS_ABBREVS.put("ar", "aspect-ratio: |;");

        // List
        CSS_ABBREVS.put("lis", "list-style: |;");
        CSS_ABBREVS.put("lisn", "list-style: none;");

        // Color
        CSS_ABBREVS.put("c", "color: |;");

        // Centering patterns (multi-line snippets)
        CSS_ABBREVS.put("cen", "display: flex;\njustify-content: center;\nalign-items: center;");
        CSS_ABBREVS.put("abscen", "position: absolute;\ntop: 50%;\nleft: 50%;\ntransform: translate(-50%, -50%);");
        CSS_ABBREVS.put("trun", "overflow: hidden;\ntext-overflow: ellipsis;\nwhite-space: nowrap;");
    }

    static {
        CSS_PROP_MAP.put("m", "margin");
        CSS_PROP_MAP.put("mt", "margin-top");
        CSS_PROP_MAP.put("mr", "margin-right");
        CSS_PROP_MAP.put("mb", "margin-bottom");
        CSS_PROP_MAP.put("ml", "margin-left");
        CSS_PROP_MAP.put("mx", "margin-inline");
        CSS_PROP_MAP.put("my", "margin-block");
        CSS_PROP_MAP.put("p", "padding");
        CSS_PROP_MAP.put("pt", "padding-top");
        CSS_PROP_MAP.put("pr", "padding-right");
        CSS_PROP_MAP.put("pb", "padding-bottom");
        CSS_PROP_MAP.put("pl", "padding-left");
        CSS_PROP_MAP.put("px", "padding-inline");
        CSS_PROP_MAP.put("py", "padding-block");
        CSS_PROP_MAP.put("w", "width");
        CSS_PROP_MAP.put("h", "height");
        CSS_PROP_MAP.put("maw", "max-width");
        CSS_PROP_MAP.put("mah", "max-height");
        CSS_PROP_MAP.put("miw", "min-width");
        CSS_PROP_MAP.put("mih", "min-height");
        CSS_PROP_MAP.put("fs", "font-size");
        CSS_PROP_MAP.put("lh", "line-height");
        CSS_PROP_MAP.put("ls", "letter-spacing");
        CSS_PROP_MAP.put("br", "border-radius");
        CSS_PROP_MAP.put("bdrs", "border-radius");
        CSS_PROP_MAP.put("t", "top");
        CSS_PROP_MAP.put("r", "right");
        CSS_PROP_MAP.put("b", "bottom");
        CSS_PROP_MAP.put("l", "left");
        CSS_PROP_MAP.put("gap", "gap");
        CSS_PROP_MAP.put("rg", "row-gap");
        CSS_PROP_MAP.put("cg", "column-gap");
        CSS_PROP_MAP.put("op", "opacity");
        CSS_PROP_MAP.put("z", "z-index");
        CSS_PROP_MAP.put("fw", "font-weight");
    }

}
