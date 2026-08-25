package com.cocode.vcode.ide.core.language.js;

import com.cocode.vcode.ide.core.diagnostic.util.KnownElements;
import com.cocode.vcode.ide.core.diagnostic.util.LinterUtils;
import com.cocode.vcode.ide.core.diagnostic.util.TokenMask;
import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspLocation;
import com.cocode.vcode.ide.core.lsp.ModuleResolver;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.core.lsp.SymbolEntry;
import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsSemanticLinter {

    // Regex for import statements: import { foo, bar as b } from './utils' or import * as NS from './utils' or import Default from './utils'
    private static final Pattern PAT_IMPORT = Pattern.compile("(?m)^\\s*import\\s+(?:\\*\\s+as\\s+([a-zA-Z_$][\\w$]*)|\\{([^}]+)\\}|([a-zA-Z_$][\\w$]*))\\s+from\\s+['\"]([^'\"]+)['\"]");
    // Simple function call extraction: identifier(args...)
    private static final Pattern PAT_FUNCTION_CALL = Pattern.compile("\\b([a-zA-Z_$][\\w$]*)\\s*\\(");
    // Find bare identifiers
    private static final Pattern PAT_IDENTIFIER = Pattern.compile("\\b([a-zA-Z_$][\\w$]*)\\b");
    // Find JS keywords
    private static final Set<String> JS_KEYWORDS = new HashSet<>(java.util.Arrays.asList(
            "await", "break", "case", "catch", "class", "const", "continue", "debugger",
            "default", "delete", "do", "else", "enum", "export", "extends", "false",
            "finally", "for", "function", "if", "import", "in", "instanceof", "new",
            "null", "return", "super", "switch", "this", "throw", "true", "try",
            "typeof", "var", "void", "while", "with", "yield", "let", "static", "async"
    ));
    
    // Pattern to grab declared functions, classes, and variables (simplified from SymbolExtractor)
    private static final Pattern PAT_DECL = Pattern.compile("\\b(?:function|class|let|const|var)\\s+([a-zA-Z_$][\\w$]*)");
    // Pattern to grab function parameters
    private static final Pattern PAT_FUNC_PARAMS = Pattern.compile("function\\s*\\w*\\s*\\(([^)]*)\\)|([a-zA-Z_$][\\w$]*)\\s*=>|\\(([^)]*)\\)\\s*=>");


    public static void analyze(File file, String text, TokenMask mask, ProjectIndex index, List<Problem> problems) {
        if (index == null || text == null || text.trim().isEmpty()) return;

        Set<String> inScope = new HashSet<>(KnownElements.JS_GLOBALS);
        inScope.addAll(JS_KEYWORDS);
        
        // 1. Parse locally declared symbols
        Matcher declMatcher = PAT_DECL.matcher(text);
        while (declMatcher.find()) {
            if (!mask.isMasked(declMatcher.start(1))) {
                inScope.add(declMatcher.group(1));
            }
        }
        
        // Add function parameters to scope
        Matcher paramMatcher = PAT_FUNC_PARAMS.matcher(text);
        while (paramMatcher.find()) {
            if (!mask.isMasked(paramMatcher.start())) {
                String paramsStr = paramMatcher.group(1);
                if (paramsStr == null) paramsStr = paramMatcher.group(2);
                if (paramsStr == null) paramsStr = paramMatcher.group(3);
                if (paramsStr != null) {
                    for (String p : paramsStr.split(",")) {
                        String clean = p.replaceAll("[={].*|\\.\\.\\.", "").trim(); // strip defaults/rest/destructuring somewhat
                        if (!clean.isEmpty() && PAT_IDENTIFIER.matcher(clean).matches()) {
                            inScope.add(clean);
                        }
                    }
                }
            }
        }

        // 2. Resolve imports
        Matcher importMatcher = PAT_IMPORT.matcher(text);
        while (importMatcher.find()) {
            if (mask.isMasked(importMatcher.start())) continue;
            
            String namespaceAlias = importMatcher.group(1);
            String namedImports = importMatcher.group(2);
            String defaultImport = importMatcher.group(3);
            String importPath = importMatcher.group(4);
            
            if (namespaceAlias != null) inScope.add(namespaceAlias);
            if (defaultImport != null) inScope.add(defaultImport);
            if (namedImports != null) {
                for (String named : namedImports.split(",")) {
                    String[] parts = named.split("\\s+as\\s+");
                    String alias = parts.length > 1 ? parts[1].trim() : parts[0].trim();
                    if (!alias.isEmpty()) inScope.add(alias);
                }
            }
        }
        
        // 3 & 4. Arity checking
        checkArity(file, text, mask, index, inScope, problems);
        
        // 5. Undefined symbols checking
        checkUndefined(file, text, mask, inScope, problems);
    }
    
    private static void checkArity(File file, String text, TokenMask mask, ProjectIndex index, Set<String> inScope, List<Problem> problems) {
        String uri = file.getAbsolutePath();
        Matcher callMatcher = PAT_FUNCTION_CALL.matcher(text);
        while (callMatcher.find()) {
            if (mask.isMasked(callMatcher.start())) continue;
            
            // Skip object member calls e.g. obj.method()
            if (callMatcher.start() > 0 && text.charAt(callMatcher.start() - 1) == '.') continue;
            
            String identifier = callMatcher.group(1);
            
            // Try to find the symbol definition in ProjectIndex (we search globally by name, but could be narrowed if we linked imports)
            // For simplicity, we just look up definitions globally since this is JS
            List<LspLocation> defs = index.findDefinitions(identifier);
            SymbolEntry targetEntry = null;
            
            for (LspLocation loc : defs) {
                List<SymbolEntry> syms = index.getFileSymbols(loc.uri);
                for (SymbolEntry s : syms) {
                    if (s.name.equals(identifier) && (s.kind == SymbolEntry.KIND_FUNCTION || s.kind == SymbolEntry.KIND_CLASS)) {
                        targetEntry = s;
                        break;
                    }
                }
                if (targetEntry != null) break;
            }
            
            if (targetEntry == null || targetEntry.detail == null) continue;
            
            // Parse arguments
            String detail = targetEntry.detail; // e.g. "a, b = 1, ...rest"
            if (detail.contains("...")) continue; // Skip rest params
            
            String[] declaredParams = detail.trim().isEmpty() ? new String[0] : detail.split(",");
            int totalParams = declaredParams.length;
            int requiredParams = 0;
            for (String p : declaredParams) {
                if (!p.contains("=")) requiredParams++;
            }
            
            // Count actual arguments
            int argsStart = callMatcher.end();
            int argsEnd = argsStart;
            int parenDepth = 1;
            boolean inQuote = false;
            char quoteChar = 0;
            
            for (int i = argsStart; i < text.length(); i++) {
                char c = text.charAt(i);
                
                if (!inQuote && (c == '"' || c == '\'' || c == '`')) {
                    inQuote = true;
                    quoteChar = c;
                } else if (inQuote && c == quoteChar && text.charAt(i-1) != '\\') {
                    inQuote = false;
                } else if (!inQuote) {
                    if (c == '(') parenDepth++;
                    else if (c == ')') {
                        parenDepth--;
                        if (parenDepth == 0) {
                            argsEnd = i;
                            break;
                        }
                    }
                }
            }
            
            if (parenDepth > 0) continue; // Unclosed parenthesis
            
            String argsText = text.substring(argsStart, argsEnd).trim();
            int actualArgs = argsText.isEmpty() ? 0 : countArgs(argsText);
            
            if (actualArgs < requiredParams) {
                int line = LinterUtils.getLine(text, callMatcher.start());
                int col = LinterUtils.getColumn(text, callMatcher.start());
                problems.add(new Problem(file, line, col, identifier.length(),
                        "Too few arguments: '" + identifier + "' expects at least " + requiredParams + " argument(s), but got " + actualArgs,
                        Problem.Severity.ERROR));
            } else if (actualArgs > totalParams) {
                int line = LinterUtils.getLine(text, callMatcher.start());
                int col = LinterUtils.getColumn(text, callMatcher.start());
                problems.add(new Problem(file, line, col, identifier.length(),
                        "Too many arguments: '" + identifier + "' expects at most " + totalParams + " argument(s), but got " + actualArgs,
                        Problem.Severity.ERROR));
            }
        }
    }
    
    private static int countArgs(String argsText) {
        int count = 1;
        int depth = 0;
        boolean inQuote = false;
        char quoteChar = 0;
        
        for (int i = 0; i < argsText.length(); i++) {
            char c = argsText.charAt(i);
            if (!inQuote && (c == '"' || c == '\'' || c == '`')) {
                inQuote = true;
                quoteChar = c;
            } else if (inQuote && c == quoteChar && i > 0 && argsText.charAt(i-1) != '\\') {
                inQuote = false;
            } else if (!inQuote) {
                if (c == '(' || c == '{' || c == '[') depth++;
                else if (c == ')' || c == '}' || c == ']') depth--;
                else if (c == ',' && depth == 0) count++;
            }
        }
        return count;
    }
    
    private static void checkUndefined(File file, String text, TokenMask mask, Set<String> inScope, List<Problem> problems) {
        Matcher idMatcher = PAT_IDENTIFIER.matcher(text);
        while (idMatcher.find()) {
            if (mask.isMasked(idMatcher.start())) continue;
            
            String id = idMatcher.group(1);
            if (id.length() <= 1) continue; // Skip single char
            
            // Check preceding char to see if it's a property access
            int preIndex = idMatcher.start() - 1;
            while (preIndex >= 0 && Character.isWhitespace(text.charAt(preIndex))) preIndex--;
            if (preIndex >= 0 && text.charAt(preIndex) == '.') continue;
            
            // Check preceding tokens to see if it's a declaration
            if (isDeclarationSite(text, idMatcher.start())) continue;
            
            // Allow object keys in object literals: { key: value }
            int postIndex = idMatcher.end();
            while (postIndex < text.length() && Character.isWhitespace(text.charAt(postIndex))) postIndex++;
            if (postIndex < text.length() && text.charAt(postIndex) == ':') {
                 // Check if it's a ternary before assuming it's a key
                 // Hard to determine cleanly without AST, we'll skip the key check for now to be safe, 
                 // actually object keys are often safe to ignore. Let's ignore if followed by ':'
                 continue;
            }
            
            if (!inScope.contains(id)) {
                int line = LinterUtils.getLine(text, idMatcher.start());
                int col = LinterUtils.getColumn(text, idMatcher.start());
                problems.add(new Problem(file, line, col, id.length(),
                        "'" + id + "' is not defined",
                        Problem.Severity.ERROR));
            }
        }
    }
    
    private static boolean isDeclarationSite(String text, int start) {
        int i = start - 1;
        while (i >= 0 && Character.isWhitespace(text.charAt(i))) i--;
        
        if (i >= 7) {
            String sub = text.substring(Math.max(0, i - 8), i + 1);
            if (sub.endsWith("function") || sub.endsWith("class") || sub.endsWith("let") || sub.endsWith("const") || sub.endsWith("var")) {
                // Must be a whole word
                int wordStart = i - (sub.endsWith("function") ? 8 : sub.endsWith("class") ? 5 : sub.endsWith("const") ? 5 : sub.endsWith("var") ? 3 : 3);
                if (wordStart < 0 || !Character.isLetterOrDigit(text.charAt(wordStart))) {
                    return true;
                }
            }
        }
        return false;
    }
}
