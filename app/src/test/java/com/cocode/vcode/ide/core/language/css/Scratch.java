package com.cocode.vcode.ide.core.language.css;

public class Scratch {
    public static void main(String[] args) {
        String source = ".class[data=\"{\"] { content: \";\"; }";
        CssTokenStream stream = CssLexer.tokenize(source);
        StringBuilder sb = new StringBuilder();
        int lastType = -1;
        int lastStart = -1;
        for (int i = 0; i < stream.length; i++) {
            if (stream.types[i] != CssTokenStream.TK_NONE) {
                if (stream.types[i] != lastType || stream.tokenStart[i] != lastStart) {
                    if (sb.length() > 0) sb.append("|");
                    sb.append(typeToString(stream.types[i])).append(":");
                    lastType = stream.types[i];
                    lastStart = stream.tokenStart[i];
                }
                sb.append(source.charAt(i));
            }
        }
        System.out.println(sb.toString());
    }
    
    private static String typeToString(byte type) {
        switch (type) {
            case CssTokenStream.TK_SELECTOR: return "SEL";
            case CssTokenStream.TK_PROPERTY: return "PROP";
            case CssTokenStream.TK_VALUE: return "VAL";
            case CssTokenStream.TK_PUNCT: return "PUNCT";
            case CssTokenStream.TK_COMMENT: return "COMM";
            default: return "UNK";
        }
    }
}
