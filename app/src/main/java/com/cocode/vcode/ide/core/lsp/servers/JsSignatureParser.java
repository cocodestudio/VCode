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

        while (i >= 0) {
            char c = text.charAt(i);
            if (c == ')') {
                parenDepth++;
            } else if (c == '(') {
                if (parenDepth == 0) {
                    break;
                }
                parenDepth--;
            } else if (c == ',' && parenDepth == 0) {
                argIndex++;
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

        String quotedFunc = Pattern.quote(funcName);
        Pattern p = Pattern.compile(
                quotedFunc + "\\s*=\\s*(?:async\\s*)?\\(([^)]*)\\)\\s*=>" + // const func = (a, b) =>
                "|function\\s+" + quotedFunc + "\\s*\\(([^)]*)\\)" + // function func(a, b)
                "|" + quotedFunc + "\\s*\\(([^)]*)\\)\\s*\\{" // method(a, b) {
        );
        Matcher m = p.matcher(text);
        String signature = null;
        if (m.find()) {
            signature = m.group(1);
            if (signature == null) signature = m.group(2);
            if (signature == null) signature = m.group(3);
        }

        if (signature == null) {
            return null; 
        }

        LspSignatureHelp.LspSignatureInformation sig = new LspSignatureHelp.LspSignatureInformation(
                funcName + "(" + signature + ")",
                "Local function",
                new ArrayList<>()
        );

        String[] args = signature.split(",");
        for (String arg : args) {
            sig.parameters.add(new LspSignatureHelp.LspParameterInformation(arg.trim(), null));
        }

        return new LspSignatureHelp(Collections.singletonList(sig), 0, argIndex);
    }
}
