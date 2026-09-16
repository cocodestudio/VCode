package com.cocode.vcode.ide.core.completion.staticdata;

import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
import com.cocode.vcode.ide.core.language.css.CssSyntaxTree;
import com.cocode.vcode.ide.core.language.css.CssTokenStream;
import com.cocode.vcode.ide.core.language.html.HtmlSyntaxTree;
import com.cocode.vcode.ide.core.language.html.HtmlTokenStream;
import com.cocode.vcode.ide.core.language.js.JsSyntaxTree;
import com.cocode.vcode.ide.core.language.json.JsonSyntaxTree;
import com.cocode.vcode.ide.core.language.json.JsonTokenStream;
import com.cocode.vcode.ide.core.model.CompletionItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified static-completion provider.
 *
 * <p>Routes a completion request to the right per-language
 * dispatcher (CSS, HTML, JS, or JSON) and returns the static
 * candidates that match the resolved AST/text position. The
 * caller merges these with AST-derived candidates from the
 * language's existing completion engine.
 *
 * <p>Dispatch rules:
 * <ul>
 *   <li><b>JS</b> at {@code Position.MEMBER_ACCESS} → empty.</li>
 *   <li><b>JS</b> at {@code Position.STATEMENT_START} →
 *       keywords + filtered structural keywords.</li>
 *   <li><b>JS</b> at {@code Position.IDENTIFIER} → empty
 *       (identifiers come from scope-based completion; static
 *       BUILTIN entries would duplicate AST candidates).</li>
 *   <li><b>CSS</b> at property-name / property-value position →
 *       property and value completions.</li>
 *   <li><b>HTML</b> at tag-name / attribute-name position →
 *       HTML element and attribute completions.</li>
 *   <li><b>HTML</b> at closing-tag position → empty (this is
 *       AST-only and lives outside this dispatcher).</li>
 *   <li><b>JSON</b> at key / value / file-empty position →
 *       structural JSON completions.</li>
 * </ul>
 *
 * <p>The {@code languageHint} is one of the constants
 * {@link #LANG_JS}, {@link #LANG_CSS}, {@link #LANG_HTML},
 * {@link #LANG_JSON}. The caller passes the file's language
 * classification.
 */
public final class StaticCompletionProvider {

    public static final String LANG_JS = "javascript";
    public static final String LANG_TS = "typescript";
    public static final String LANG_CSS = "css";
    public static final String LANG_HTML = "html";
    public static final String LANG_JSON = "json";
    private StaticCompletionProvider() {
    }

    /**
     * Compute the static completion candidates for the cursor's
     * current position. The caller passes the relevant parsed
     * tree(s) for the file's language.
     */
    public static List<CompletionItem> provide(
            String languageHint,
            String source, int cursor,
            // JS
            TokenStream jsStream, JsSyntaxTree jsTree,
            // CSS
            CssTokenStream cssStream, CssSyntaxTree cssTree,
            // HTML
            HtmlTokenStream htmlStream, HtmlSyntaxTree htmlTree,
            // JSON
            JsonTokenStream jsonStream, JsonSyntaxTree jsonTree,
            // For M.18 filename boost
            String currentFileName) {
        if (languageHint == null) return new ArrayList<>();
        switch (languageHint) {
            case LANG_JS:
            case LANG_TS:
                return provideJs(source, cursor, jsStream, jsTree);
            case LANG_CSS:
                return provideCss(source, cursor);
            case LANG_HTML:
                return provideHtml(source, cursor);
            case LANG_JSON:
                return provideJson(source, cursor, currentFileName);
            default:
                return new ArrayList<>();
        }
    }

    // --- Per-language routing ------------------------------------------

    private static List<CompletionItem> provideJs(
            String source, int cursor, TokenStream stream, JsSyntaxTree tree) {
        JsStaticCompletionDispatcher.Position pos =
                JsStaticCompletionDispatcher.detectPosition(source, stream, tree, cursor);
        switch (pos) {
            case STATEMENT_START: {
                List<CompletionItem> all = JsStaticCompletionDispatcher.buildCompletions();
                return JsStaticCompletionDispatcher.filterStructural(all, source, cursor);
            }
            case IDENTIFIER:
            case MEMBER_ACCESS:
            case OTHER:
            default:
                return new ArrayList<>();
        }
    }

    private static List<CompletionItem> provideCss(String source, int cursor) {
        CssStaticCompletionDispatcher.Position pos =
                CssStaticCompletionDispatcher.detectPosition(source, null, null, cursor);
        return CssStaticCompletionDispatcher.buildCompletions(pos, source, cursor);
    }

    private static List<CompletionItem> provideHtml(String source, int cursor) {
        HtmlStaticCompletionDispatcher.Position pos =
                HtmlStaticCompletionDispatcher.detectPosition(source, cursor);
        // M.10: closing-tag is AST-only — dispatcher returns empty.
        return HtmlStaticCompletionDispatcher.buildCompletions(pos, source, cursor);
    }

    private static List<CompletionItem> provideJson(String source, int cursor, String currentFileName) {
        JsonStaticCompletionDispatcher.Position pos =
                JsonStaticCompletionDispatcher.detectPosition(source, cursor);
        return JsonStaticCompletionDispatcher.buildCompletions(pos, currentFileName);
    }
}
