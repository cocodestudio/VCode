package com.cocode.vcode.ide.core.language.css;

import com.cocode.vcode.ide.core.model.CompletionItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Static definitions of CSS properties, at-rules, pseudo-classes, pseudo-elements, and units.
 */
public class CssDefinitions {
    public static final List<CompletionItem> PSEUDO_ITEMS = new ArrayList<>();
    public static final List<CompletionItem> AT_RULE_ITEMS = new ArrayList<>();

    static {
        String[][] pseudos = {
                {":hover", "hover", "Pseudo-class — mouse hover"},
                {":focus", "focus", "Pseudo-class — keyboard focus"},
                {":focus-within", "focus-within", "Pseudo-class — child has focus"},
                {":focus-visible", "focus-visible", "Pseudo-class — keyboard focus ring"},
                {":active", "active", "Pseudo-class — being clicked"},
                {":visited", "visited", "Pseudo-class — visited link"},
                {":link", "link", "Pseudo-class — unvisited link"},
                {":checked", "checked", "Pseudo-class — checked input"},
                {":disabled", "disabled", "Pseudo-class — disabled element"},
                {":enabled", "enabled", "Pseudo-class — enabled element"},
                {":placeholder-shown", "placeholder-shown", "Pseudo-class — placeholder visible"},
                {":required", "required", "Pseudo-class — required input"},
                {":optional", "optional", "Pseudo-class — optional input"},
                {":valid", "valid", "Pseudo-class — valid input value"},
                {":invalid", "invalid", "Pseudo-class — invalid input value"},
                {":read-only", "read-only", "Pseudo-class — read-only element"},
                {":read-write", "read-write", "Pseudo-class — editable element"},
                {":empty", "empty", "Pseudo-class — no children/text"},
                {":root", "root", "Pseudo-class — document root element"},
                {":first-child", "first-child", "Pseudo-class — first child"},
                {":last-child", "last-child", "Pseudo-class — last child"},
                {":first-of-type", "first-of-type", "Pseudo-class — first of its type"},
                {":last-of-type", "last-of-type", "Pseudo-class — last of its type"},
                {":only-child", "only-child", "Pseudo-class — only child"},
                {":only-of-type", "only-of-type", "Pseudo-class — only of its type"},
                {":nth-child()", "nth-child(|)", "Pseudo-class — nth child"},
                {":nth-last-child()", "nth-last-child(|)", "Pseudo-class — nth last child"},
                {":nth-of-type()", "nth-of-type(|)", "Pseudo-class — nth of type"},
                {":nth-last-of-type()", "nth-last-of-type(|)", "Pseudo-class — nth last of type"},
                {":not()", "not(|)", "Pseudo-class — negation selector"},
                {":is()", "is(|)", "Pseudo-class — matches any selector"},
                {":where()", "where(|)", "Pseudo-class — zero-specificity match"},
                {":has()", "has(|)", "Pseudo-class — relational (parent) selector"},
                {"::before", ":before", "Pseudo-element — generated before content"},
                {"::after", ":after", "Pseudo-element — generated after content"},
                {"::placeholder", ":placeholder", "Pseudo-element — input placeholder"},
                {"::selection", ":selection", "Pseudo-element — user text selection"},
                {"::first-line", ":first-line", "Pseudo-element — first line of block"},
                {"::first-letter", ":first-letter", "Pseudo-element — first letter of block"},
                {"::marker", ":marker", "Pseudo-element — list item marker"},
                {"::backdrop", ":backdrop", "Pseudo-element — fullscreen backdrop"},
        };
        for (String[] p : pseudos) {
            String label = p[0];
            String insertText = p[1];
            String detail = p[2];
            PSEUDO_ITEMS.add(new CompletionItem(label, insertText, detail, CompletionItem.Type.CSS_VALUE, 0));
        }

        String[][] atRules = {
                {"@media", "@media "},
                {"@keyframes", "@keyframes "},
                {"@import", "@import '"},
                {"@font-face", "@font-face "},
                {"@supports", "@supports "},
                {"@charset", "@charset '"},
                {"@layer", "@layer "},
                {"@container", "@container "},
                {"@property", "@property --"},
                {"@counter-style", "@counter-style "},
        };
        for (String[] r : atRules) {
            AT_RULE_ITEMS.add(new CompletionItem(r[0], r[1], "At-rule", CompletionItem.Type.CSS_VALUE, 0));
        }
    }
}
