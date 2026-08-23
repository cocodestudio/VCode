package com.cocode.vcode.ide.core.lsp.servers;

import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspPosition;
import com.cocode.vcode.ide.core.lsp.LspSignatureHelp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsSignatureParser {

    public static LspSignatureHelp parse(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return null;
        int offset = doc.toOffset(pos);
        if (offset < 0 || offset > doc.text.length()) return null;

        String text = doc.text;
        int i = offset - 1;
        int parenDepth = 0;
        int argIndex = 0;

        boolean inString = false;
        char stringChar = 0;

        while (i >= 0) {
            char c = text.charAt(i);
            
            if (inString) {
                if (c == stringChar) {
                    int bs = 0, k = i - 1;
                    while (k >= 0 && text.charAt(k) == '\\') { bs++; k--; }
                    if (bs % 2 == 0) inString = false;
                }
            } else {
                if (c == '"' || c == '\'' || c == '`') {
                    inString = true; stringChar = c;
                } else if (c == ')') {
                    parenDepth++;
                } else if (c == '(') {
                    if (parenDepth == 0) break;
                    parenDepth--;
                } else if (c == ',' && parenDepth == 0) {
                    argIndex++;
                }
            }
            i--;
        }

        if (i < 0) return null;

        i--; // Skip '('
        while (i >= 0 && Character.isWhitespace(text.charAt(i))) i--;

        int wordEnd = i + 1;
        while (i >= 0 && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_' || text.charAt(i) == '$')) {
            i--;
        }
        int wordStart = i + 1;
        if (wordStart >= wordEnd) return null;

        String funcName = text.substring(wordStart, wordEnd);
        
        // Prevent built-in keywords from being treated as local functions
        if (funcName.equals("if") || funcName.equals("for") || funcName.equals("while") 
                || funcName.equals("switch") || funcName.equals("catch") || funcName.equals("return")) {
            return null;
        }
        
        boolean isNew = false;
        int j = wordStart - 1;
        while (j >= 0 && Character.isWhitespace(text.charAt(j))) j--;
        if (j >= 2 && text.charAt(j) == 'w' && text.charAt(j - 1) == 'e' && text.charAt(j - 2) == 'n' && (j == 2 || !Character.isLetterOrDigit(text.charAt(j - 3)))) {
            isNew = true;
        }

        String signature = null;
        String sourceLabel = isNew ? "Class constructor" : "Local function";

        String quotedFunc = java.util.regex.Pattern.quote(funcName);
        
        if (isNew) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "class\\s+" + quotedFunc + "(?:\\s+extends\\s+\\w+)?\\s*\\{(?:(?!\\bclass\\b)[\\s\\S])*?constructor\\s*\\(([^)]*)\\)", java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher m = p.matcher(text);
            if (m.find()) {
                signature = m.group(1);
            }
        } else {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    quotedFunc + "\\s*=\\s*(?:async\\s*)?\\(([^)]*)\\)\\s*=>" + 
                    "|function\\s+" + quotedFunc + "\\s*\\(([^)]*)\\)" + 
                    "|(?:^|\\s)(?:static\\s+)?(?:async\\s+)?" + quotedFunc + "\\s*\\(([^)]*)\\)\\s*\\{", 
                    java.util.regex.Pattern.MULTILINE
            );
            java.util.regex.Matcher m = p.matcher(text);
            if (m.find()) {
                signature = m.group(1);
                if (signature == null) signature = m.group(2);
                if (signature == null) signature = m.group(3);
            }
        }

        if (signature == null) {
            com.cocode.vcode.ide.core.lsp.ProjectIndex index = com.cocode.vcode.ide.core.lsp.ProjectIndex.getInstance();
            java.util.List<com.cocode.vcode.ide.core.lsp.SymbolEntry> symbols = index.findSymbolsByPrefix(funcName);
            for (com.cocode.vcode.ide.core.lsp.SymbolEntry sym : symbols) {
                if (sym.name.equals(funcName)) {
                    if (isNew && sym.kind == com.cocode.vcode.ide.core.lsp.SymbolEntry.KIND_CLASS) {
                        signature = sym.detail;
                        sourceLabel = "Cross-file class";
                        break;
                    } else if (!isNew && sym.kind == com.cocode.vcode.ide.core.lsp.SymbolEntry.KIND_FUNCTION) {
                        signature = sym.detail;
                        sourceLabel = "Cross-file function";
                        break;
                    }
                }
            }
        }

        if (signature == null) {
            return null;
        }

        LspSignatureHelp.LspSignatureInformation sig = new LspSignatureHelp.LspSignatureInformation(
                funcName + "(" + signature.trim().replaceAll("\\s+", " ") + ")",
                sourceLabel,
                new java.util.ArrayList<>()
        );

        if (!signature.trim().isEmpty()) {
            String[] args = signature.split(",");
            for (String arg : args) {
                sig.parameters.add(new LspSignatureHelp.LspParameterInformation(arg.trim(), null));
            }
        }

        return new LspSignatureHelp(java.util.Collections.singletonList(sig), 0, argIndex);
    }
}
