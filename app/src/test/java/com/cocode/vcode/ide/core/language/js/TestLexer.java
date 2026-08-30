package com.cocode.vcode.ide.core.language.js;
public class TestLexer {
    public static void main(String[] args) {
        String code = "let a = `hello ${`world ${1}`}`;";
        System.out.println("Tokenizing...");
        JsLexer.tokenize(code);
        System.out.println("Done!");
    }
}