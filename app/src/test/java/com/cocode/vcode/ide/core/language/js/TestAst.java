package com.cocode.vcode.ide.core.language.js;
import com.cocode.vcode.ide.core.diagnostic.util.TokenStream;
public class TestAst {
    public static void main(String[] args) {
        String code = "function f({ id, name }: User) { console.log(id); }";
        TokenStream mask = JsLexer.tokenize(code);
        JsSyntaxTree tree = JsParser.parseFull(code, mask);
        for(int i = 1; i < tree.nodeCount; i++) {
            System.out.println("Node " + i + ": type=" + tree.nodeType[i] + " start=" + tree.nodeStart[i] + " name=" + tree.nodeName[i]);
        }
    }
}