package com.cocode.vcode.ide.core.autocomplete;

import android.content.Context;

import com.cocode.vcode.ide.core.model.CompletionItem;
import com.cocode.vcode.ide.core.model.FileType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Autocomplete provider for relative file and directory path suggestions in quotes/parentheses.
 */
public class PathAutoCompleteEngine extends AutoCompleteEngine {

    private File currentFile;

    public PathAutoCompleteEngine(Context context) {
        super(context);
    }

    public void setCurrentFile(File file) {
        this.currentFile = file;
    }

    @Override
    public List<CompletionItem> getSuggestions(String fullText, int cursorPos) {
        if (currentFile == null || !currentFile.exists()) return new ArrayList<>();

        // Extract typed path prefix like "src/app/" or "./"
        String prefix = getPathBeforeCursor(fullText, cursorPos);
        if (prefix.isEmpty()) {
            // Check if preceded by a path trigger char
            if (cursorPos > 0) {
                char prev = fullText.charAt(cursorPos - 1);
                if (prev != '(' && prev != '"' && prev != '\'' && prev != '/' && prev != '=') {
                    return new ArrayList<>();
                }
            } else {
                return new ArrayList<>();
            }
        }

        List<CompletionItem> suggestions = new ArrayList<>();
        File baseDir = currentFile.getParentFile();
        
        // Resolve target directory based on prefix
        String searchDirName = "";
        String filterPrefix = prefix;
        
        int lastSlash = prefix.lastIndexOf('/');
        if (lastSlash != -1) {
            searchDirName = prefix.substring(0, lastSlash);
            filterPrefix = prefix.substring(lastSlash + 1);
            
            if (searchDirName.startsWith("/")) {
                // Not supporting absolute paths from root, just relative
                return suggestions;
            }
            
            String[] parts = searchDirName.split("/");
            for (String part : parts) {
                if (part.equals(".")) {
                    // Current dir
                } else if (part.equals("..")) {
                    if (baseDir != null) baseDir = baseDir.getParentFile();
                } else if (!part.isEmpty()) {
                    if (baseDir != null) baseDir = new File(baseDir, part);
                }
            }
        }
        
        if (baseDir != null && baseDir.exists() && baseDir.isDirectory()) {
            File[] files = baseDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    CompletionItem.Type type = f.isDirectory() ? CompletionItem.Type.FOLDER : CompletionItem.Type.FILE;
                    CompletionItem item = new CompletionItem(
                            f.getName() + (f.isDirectory() ? "/" : ""),
                            f.getName() + (f.isDirectory() ? "/" : ""),
                            "Path",
                            type,
                            0
                    );
                    
                    suggestions.add(item);
                }
            }
        }

        return fuzzyFilter(suggestions, filterPrefix);
    }
    
    private String getPathBeforeCursor(String text, int pos) {
        if (text == null || pos <= 0 || pos > text.length()) return "";
        int start = pos;
        while (start > 0) {
            char c = text.charAt(start - 1);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == '/') {
                start--;
            } else {
                break;
            }
        }
        return text.substring(start, pos);
    }
    
    private String getFileExtension(String name) {
        int dot = name.lastIndexOf('.');
        return (dot != -1 && dot < name.length() - 1) ? name.substring(dot + 1) : "";
    }
}
