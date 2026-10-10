package com.cocode.vcode.ide.core.language.json;

import com.cocode.vcode.ide.core.diagnostic.ProblemCode;
import com.cocode.vcode.ide.core.diagnostic.util.LinterUtils;
import com.cocode.vcode.ide.core.model.CompletionItem;
import com.cocode.vcode.ide.core.model.Problem;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Manages JSON schemas, validation, property completions, and hover documentation
 * for package.json, tsconfig.json, and other configuration manifests.
 */
public final class JsonSchemaManager {

    public static final class SchemaProperty {
        public final String name;
        public final String type;
        public final String description;
        public final String defaultValue;
        public final boolean required;
        public final List<String> enumValues;
        public final boolean isPath;

        public SchemaProperty(String name, String type, String description, String defaultValue,
                              boolean required, List<String> enumValues, boolean isPath) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.defaultValue = defaultValue;
            this.required = required;
            this.enumValues = enumValues != null ? Collections.unmodifiableList(enumValues) : null;
            this.isPath = isPath;
        }

        public String toMarkdown() {
            StringBuilder sb = new StringBuilder();
            sb.append("### `").append(name).append("`\n\n");
            if (type != null && !type.isEmpty()) {
                sb.append("_Type:_ `").append(type).append("`\n\n");
            }
            if (defaultValue != null && !defaultValue.isEmpty()) {
                sb.append("_Default:_ `").append(defaultValue).append("`\n\n");
            }
            if (enumValues != null && !enumValues.isEmpty()) {
                sb.append("_Allowed Values:_ ").append(enumValues.toString()).append("\n\n");
            }
            if (description != null && !description.isEmpty()) {
                sb.append(description).append("\n");
            }
            return sb.toString().trim();
        }
    }

    private static final Map<String, SchemaProperty> PACKAGE_PROPERTIES = new HashMap<>();
    private static final Map<String, SchemaProperty> TSCONFIG_ROOT_PROPERTIES = new HashMap<>();
    private static final Map<String, SchemaProperty> TSCONFIG_COMPILER_OPTIONS = new HashMap<>();

    static {
        initPackageSchema();
        initTsConfigSchema();
    }

    private JsonSchemaManager() {}

    private static void initPackageSchema() {
        putPkg(new SchemaProperty("name", "string",
                "The name of the package. Must be lowercase, one word, and may contain hyphens.",
                "\"my-package\"", true, null, false));
        putPkg(new SchemaProperty("version", "string",
                "Version must be parseable by node-semver (e.g. 1.0.0).",
                "\"1.0.0\"", true, null, false));
        putPkg(new SchemaProperty("description", "string",
                "Package description displayed in search results.",
                "\"A brief description\"", false, null, false));
        putPkg(new SchemaProperty("main", "string",
                "The primary entry point to your program.",
                "\"./dist/index.js\"", false, null, true));
        putPkg(new SchemaProperty("module", "string",
                "An ECMAScript module ID used by modern bundlers.",
                "\"./dist/index.esm.js\"", false, null, true));
        putPkg(new SchemaProperty("types", "string",
                "Location of the main TypeScript declaration file.",
                "\"./dist/index.d.ts\"", false, null, true));
        putPkg(new SchemaProperty("typings", "string",
                "Alternative to 'types'. Specifies the entry declaration file.",
                "\"./dist/index.d.ts\"", false, null, true));
        putPkg(new SchemaProperty("scripts", "object",
                "Dictionary of script commands run during package lifecycle.",
                "{\n  \"build\": \"tsc\",\n  \"test\": \"jest\"\n}", false, null, false));
        putPkg(new SchemaProperty("dependencies", "object",
                "Runtime dependencies mapped to semver version ranges.",
                "{}", false, null, false));
        putPkg(new SchemaProperty("devDependencies", "object",
                "Packages only needed for local development and testing.",
                "{}", false, null, false));
        putPkg(new SchemaProperty("peerDependencies", "object",
                "Dependencies required by the host application.",
                "{}", false, null, false));
        putPkg(new SchemaProperty("repository", "object | string",
                "The place where the project source code lives.",
                "\"https://github.com/user/repo\"", false, null, false));
        putPkg(new SchemaProperty("keywords", "array",
                "Array of keywords to help identify your package.",
                "[]", false, null, false));
        putPkg(new SchemaProperty("author", "object | string",
                "Author contact information.",
                "\"Your Name <email@example.com>\"", false, null, false));
        putPkg(new SchemaProperty("license", "string",
                "Software license identifier (SPDX format).",
                "\"MIT\"", false, Arrays.asList("MIT", "Apache-2.0", "ISC", "BSD-2-Clause", "BSD-3-Clause", "GPL-3.0", "AGPL-3.0", "UNLICENSED"), false));
        putPkg(new SchemaProperty("private", "boolean",
                "If set to true, npm will refuse to publish this package.",
                "true", false, null, false));
        putPkg(new SchemaProperty("engines", "object",
                "Version ranges of node and npm that this package requires.",
                "{\n  \"node\": \">=18.0.0\"\n}", false, null, false));
        putPkg(new SchemaProperty("files", "array",
                "List of file patterns to include when published as a dependency.",
                "[\"dist/\", \"README.md\"]", false, null, false));
        putPkg(new SchemaProperty("bin", "object | string",
                "Map of executable command name to local file path.",
                "{}", false, null, false));
        putPkg(new SchemaProperty("type", "string",
                "The module type of JavaScript files (commonjs or module).",
                "\"module\"", false, Arrays.asList("commonjs", "module"), false));
    }

    private static void initTsConfigSchema() {
        putTsRoot(new SchemaProperty("compilerOptions", "object",
                "Instructs the TypeScript compiler how to compile .ts files to JavaScript.",
                "{}", false, null, false));
        putTsRoot(new SchemaProperty("include", "array",
                "Array of filenames or glob patterns to include in the program.",
                "[\"src/**/*\"]", false, null, false));
        putTsRoot(new SchemaProperty("exclude", "array",
                "Array of filenames or glob patterns to skip.",
                "[\"node_modules\", \"dist\"]", false, null, false));
        putTsRoot(new SchemaProperty("files", "array",
                "Explicit list of files to include in the program.",
                "[]", false, null, false));
        putTsRoot(new SchemaProperty("extends", "string",
                "Path to base configuration file to inherit from.",
                "\"./tsconfig.base.json\"", false, null, true));
        putTsRoot(new SchemaProperty("references", "array",
                "Array of project references.",
                "[]", false, null, false));

        putTsOpt(new SchemaProperty("target", "string",
                "Set the JavaScript language version for emitted JavaScript.",
                "\"ES2022\"", false,
                Arrays.asList("ES3", "ES5", "ES6", "ES2015", "ES2016", "ES2017", "ES2018", "ES2019", "ES2020", "ES2021", "ES2022", "ES2023", "ESNext"),
                false));
        putTsOpt(new SchemaProperty("module", "string",
                "Specify what module code is generated.",
                "\"NodeNext\"", false,
                Arrays.asList("CommonJS", "AMD", "System", "UMD", "ES6", "ES2015", "ES2020", "ES2022", "ESNext", "Node16", "NodeNext", "Preserve", "None"),
                false));
        putTsOpt(new SchemaProperty("moduleResolution", "string",
                "Specify how TypeScript looks up a file from a given module specifier.",
                "\"bundler\"", false,
                Arrays.asList("classic", "node", "node10", "node16", "nodenext", "bundler"),
                false));
        putTsOpt(new SchemaProperty("strict", "boolean",
                "Enable all strict type-checking options.",
                "true", false, null, false));
        putTsOpt(new SchemaProperty("jsx", "string",
                "Specify what JSX code is generated.",
                "\"react-jsx\"", false,
                Arrays.asList("preserve", "react", "react-jsx", "react-jsxdev", "react-native"),
                false));
        putTsOpt(new SchemaProperty("outDir", "string",
                "Specify an output folder for all emitted files.",
                "\"./dist\"", false, null, true));
        putTsOpt(new SchemaProperty("rootDir", "string",
                "Specify the root folder within your source files.",
                "\"./src\"", false, null, true));
        putTsOpt(new SchemaProperty("baseUrl", "string",
                "Specify the base directory to resolve non-relative module names.",
                "\".\"", false, null, true));
        putTsOpt(new SchemaProperty("paths", "object",
                "Specify a set of entries that re-map imports to lookup locations.",
                "{}", false, null, false));
        putTsOpt(new SchemaProperty("lib", "array",
                "Specify bundled library declaration files that describe runtime environment.",
                "[\"ES2022\", \"DOM\"]", false, null, false));
        putTsOpt(new SchemaProperty("allowJs", "boolean",
                "Allow JavaScript files to be a part of your program.",
                "true", false, null, false));
        putTsOpt(new SchemaProperty("checkJs", "boolean",
                "Enable error reporting in type-checked JavaScript files.",
                "true", false, null, false));
        putTsOpt(new SchemaProperty("declaration", "boolean",
                "Generate .d.ts files from TypeScript and JavaScript files.",
                "true", false, null, false));
        putTsOpt(new SchemaProperty("sourceMap", "boolean",
                "Create source map files for emitted JavaScript files.",
                "true", false, null, false));
        putTsOpt(new SchemaProperty("noEmit", "boolean",
                "Disable emitting files from a compilation.",
                "true", false, null, false));
        putTsOpt(new SchemaProperty("esModuleInterop", "boolean",
                "Emit additional JavaScript to ease support for importing CommonJS modules.",
                "true", false, null, false));
        putTsOpt(new SchemaProperty("skipLibCheck", "boolean",
                "Skip type checking of declaration files (.d.ts).",
                "true", false, null, false));
        putTsOpt(new SchemaProperty("resolveJsonModule", "boolean",
                "Enable importing .json files.",
                "true", false, null, false));
        putTsOpt(new SchemaProperty("isolatedModules", "boolean",
                "Ensure that each file can be safely transpiled without relying on other imports.",
                "true", false, null, false));
        putTsOpt(new SchemaProperty("forceConsistentCasingInFileNames", "boolean",
                "Ensure that casing is correct in imports.",
                "true", false, null, false));
        putTsOpt(new SchemaProperty("noUnusedLocals", "boolean",
                "Enable error reporting when local variables aren't read.",
                "true", false, null, false));
        putTsOpt(new SchemaProperty("noUnusedParameters", "boolean",
                "Raise an error when a function parameter isn't read.",
                "true", false, null, false));
        putTsOpt(new SchemaProperty("noFallthroughCasesInSwitch", "boolean",
                "Enable error reporting for fallthrough cases in switch statements.",
                "true", false, null, false));
    }

    private static void putPkg(SchemaProperty p) {
        PACKAGE_PROPERTIES.put(p.name, p);
    }

    private static void putTsRoot(SchemaProperty p) {
        TSCONFIG_ROOT_PROPERTIES.put(p.name, p);
    }

    private static void putTsOpt(SchemaProperty p) {
        TSCONFIG_COMPILER_OPTIONS.put(p.name, p);
    }

    public static boolean isPackageJson(File file, String uri) {
        String name = file != null ? file.getName() : (uri != null ? uri : "");
        return name.toLowerCase(Locale.ROOT).endsWith("package.json");
    }

    public static boolean isTsConfig(File file, String uri) {
        String name = file != null ? file.getName() : (uri != null ? uri : "");
        String lower = name.toLowerCase(Locale.ROOT);
        int slash = Math.max(lower.lastIndexOf('/'), lower.lastIndexOf('\\'));
        if (slash >= 0) lower = lower.substring(slash + 1);
        return (lower.startsWith("tsconfig") || lower.startsWith("jsconfig")) && lower.endsWith(".json");
    }

    /**
     * Validates package.json or tsconfig.json manifests against built-in schema rules.
     * Reports missing required properties, type mismatches, and invalid enum values.
     */
    public static List<Problem> validate(File file, String uri, String text, JsonSyntaxTree tree) {
        if (tree == null || text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        boolean isPkg = isPackageJson(file, uri);
        boolean isTs = isTsConfig(file, uri);

        if (!isPkg && !isTs) {
            return Collections.emptyList();
        }

        List<Problem> problems = new ArrayList<>();

        // Locate root object
        int rootObject = -1;
        for (int i = 1; i < tree.nodeCount; i++) {
            if (tree.nodeType[i] == JsonSyntaxTree.N_OBJECT && tree.nodeParent[i] == 0) {
                rootObject = i;
                break;
            }
        }

        if (rootObject <= 0) {
            return problems;
        }

        if (isPkg) {
            validatePackageJson(file, text, tree, rootObject, problems);
        } else {
            validateTsConfig(file, text, tree, rootObject, problems);
        }

        return problems;
    }

    private static void validatePackageJson(File file, String text, JsonSyntaxTree tree,
                                            int rootObject, List<Problem> problems) {
        Set<String> declaredKeys = new HashSet<>();
        int child = tree.nodeChild[rootObject];
        int loopGuard = 0;

        while (child > 0 && child < tree.nodeCount && ++loopGuard <= tree.nodeCount) {
            if (tree.nodeType[child] == JsonSyntaxTree.N_KEY) {
                String rawKey = tree.nodeName[child];
                String key = cleanKey(rawKey);
                declaredKeys.add(key);

                SchemaProperty prop = PACKAGE_PROPERTIES.get(key);
                if (prop != null) {
                    validatePropertyValue(file, text, tree, child, prop, problems);
                }
            }
            child = tree.nodeSibling[child];
        }

        // Validate required properties
        for (SchemaProperty prop : PACKAGE_PROPERTIES.values()) {
            if (prop.required && !declaredKeys.contains(prop.name)) {
                int line = LinterUtils.getLine(text, tree.nodeStart[rootObject]);
                int col = LinterUtils.getColumn(text, tree.nodeStart[rootObject]);
                problems.add(new Problem(file, line, col, 1,
                        "Missing required property '" + prop.name + "' in package.json",
                        Problem.Severity.WARNING, ProblemCode.JSON_SCHEMA_VALIDATION));
            }
        }
    }

    private static void validateTsConfig(File file, String text, JsonSyntaxTree tree,
                                         int rootObject, List<Problem> problems) {
        int child = tree.nodeChild[rootObject];
        int loopGuard = 0;

        while (child > 0 && child < tree.nodeCount && ++loopGuard <= tree.nodeCount) {
            if (tree.nodeType[child] == JsonSyntaxTree.N_KEY) {
                String rawKey = tree.nodeName[child];
                String key = cleanKey(rawKey);

                SchemaProperty rootProp = TSCONFIG_ROOT_PROPERTIES.get(key);
                if (rootProp != null) {
                    validatePropertyValue(file, text, tree, child, rootProp, problems);
                }

                if ("compilerOptions".equals(key)) {
                    int valNode = tree.nodeChild[child];
                    if (valNode > 0 && tree.nodeType[valNode] == JsonSyntaxTree.N_OBJECT) {
                        validateCompilerOptions(file, text, tree, valNode, problems);
                    }
                }
            }
            child = tree.nodeSibling[child];
        }
    }

    private static void validateCompilerOptions(File file, String text, JsonSyntaxTree tree,
                                                int optionsObject, List<Problem> problems) {
        int optChild = tree.nodeChild[optionsObject];
        int optGuard = 0;

        while (optChild > 0 && optChild < tree.nodeCount && ++optGuard <= tree.nodeCount) {
            if (tree.nodeType[optChild] == JsonSyntaxTree.N_KEY) {
                String rawKey = tree.nodeName[optChild];
                String key = cleanKey(rawKey);

                SchemaProperty optProp = TSCONFIG_COMPILER_OPTIONS.get(key);
                if (optProp != null) {
                    validatePropertyValue(file, text, tree, optChild, optProp, problems);
                }
            }
            optChild = tree.nodeSibling[optChild];
        }
    }

    private static void validatePropertyValue(File file, String text, JsonSyntaxTree tree,
                                              int keyNode, SchemaProperty prop, List<Problem> problems) {
        int valNode = tree.nodeChild[keyNode];
        if (valNode <= 0 || valNode >= tree.nodeCount) return;

        byte valType = tree.nodeType[valNode];
        int start = tree.nodeStart[valNode];
        int end = tree.nodeEnd[valNode];
        int len = Math.max(1, end - start);
        int line = LinterUtils.getLine(text, start);
        int col = LinterUtils.getColumn(text, start);

        // 1. Type validation
        boolean typeMatches = true;
        String actualType = describeNodeType(valType);

        if ("string".equals(prop.type)) {
            typeMatches = (valType == JsonSyntaxTree.N_VALUE_STRING);
        } else if ("boolean".equals(prop.type)) {
            typeMatches = (valType == JsonSyntaxTree.N_VALUE_BOOL);
        } else if ("object".equals(prop.type)) {
            typeMatches = (valType == JsonSyntaxTree.N_OBJECT);
        } else if ("array".equals(prop.type)) {
            typeMatches = (valType == JsonSyntaxTree.N_ARRAY);
        } else if ("object | string".equals(prop.type)) {
            typeMatches = (valType == JsonSyntaxTree.N_OBJECT || valType == JsonSyntaxTree.N_VALUE_STRING);
        }

        if (!typeMatches && valType != JsonSyntaxTree.N_ERROR && valType != JsonSyntaxTree.N_VALUE_NULL) {
            problems.add(new Problem(file, line, col, len,
                    "Type mismatch for '" + prop.name + "': expected " + prop.type + ", got " + actualType,
                    Problem.Severity.ERROR, ProblemCode.JSON_SCHEMA_VALIDATION));
            return;
        }

        // 2. Enum validation
        if (prop.enumValues != null && valType == JsonSyntaxTree.N_VALUE_STRING) {
            String rawVal = tree.nodeName[valNode];
            String cleanVal = cleanKey(rawVal);
            boolean enumFound = false;
            for (String allowed : prop.enumValues) {
                if (allowed.equalsIgnoreCase(cleanVal)) {
                    enumFound = true;
                    break;
                }
            }
            if (!enumFound) {
                problems.add(new Problem(file, line, col, len,
                        "Value '" + cleanVal + "' is not accepted for '" + prop.name + "'. Allowed: " + prop.enumValues,
                        Problem.Severity.WARNING, ProblemCode.JSON_SCHEMA_VALIDATION));
            }
        }
    }

    /**
     * Resolves schema-driven completion items for properties and enum values.
     */
    public static List<CompletionItem> getCompletions(File file, String uri, String text, int offset, JsonSyntaxTree tree) {
        boolean isPkg = isPackageJson(file, uri);
        boolean isTs = isTsConfig(file, uri);
        if (!isPkg && !isTs) return Collections.emptyList();

        List<CompletionItem> items = new ArrayList<>();

        // 1. Check if the cursor is in a property value position to offer enum/boolean completions
        String activeKey = findActivePropertyKey(text, offset);
        if (activeKey != null) {
            String clean = cleanKey(activeKey);
            SchemaProperty activeProp = null;
            if (isPkg) {
                activeProp = PACKAGE_PROPERTIES.get(clean);
            } else if (isTs) {
                activeProp = TSCONFIG_COMPILER_OPTIONS.get(clean);
                if (activeProp == null) {
                    activeProp = TSCONFIG_ROOT_PROPERTIES.get(clean);
                }
            }
            if (activeProp != null) {
                if (activeProp.enumValues != null) {
                    for (String ev : activeProp.enumValues) {
                        items.add(new CompletionItem(ev, ev, "enum (" + activeProp.name + ")",
                                CompletionItem.Type.VALUE, 0));
                        String lower = ev.toLowerCase();
                        if (!lower.equals(ev)) {
                            items.add(new CompletionItem(lower, lower, "enum (" + activeProp.name + ")",
                                    CompletionItem.Type.VALUE, 0));
                        }
                    }
                } else if ("boolean".equals(activeProp.type)) {
                    items.add(new CompletionItem("true", "true", "boolean", CompletionItem.Type.VALUE, 0));
                    items.add(new CompletionItem("false", "false", "boolean", CompletionItem.Type.VALUE, 0));
                }
                if (!items.isEmpty()) {
                    return items;
                }
            }
        }

        if (tree == null) return items;

        int enclosingObj = tree.getEnclosingObject(offset);
        if (enclosingObj <= 0) return items;

        // Collect existing keys in the enclosing object to avoid duplicate suggestions
        Set<String> existingKeys = new HashSet<>();
        int child = tree.nodeChild[enclosingObj];
        int loop = 0;
        while (child > 0 && child < tree.nodeCount && ++loop <= tree.nodeCount) {
            if (tree.nodeType[child] == JsonSyntaxTree.N_KEY) {
                existingKeys.add(cleanKey(tree.nodeName[child]));
            }
            child = tree.nodeSibling[child];
        }

        // Determine which schema applies to the enclosing object
        Map<String, SchemaProperty> targetMap = null;
        if (isPkg && tree.nodeParent[enclosingObj] == 0) {
            targetMap = PACKAGE_PROPERTIES;
        } else if (isTs) {
            if (tree.nodeParent[enclosingObj] == 0) {
                targetMap = TSCONFIG_ROOT_PROPERTIES;
            } else {
                int parent = tree.nodeParent[enclosingObj];
                if (parent > 0 && tree.nodeType[parent] == JsonSyntaxTree.N_KEY) {
                    String parentKey = cleanKey(tree.nodeName[parent]);
                    if ("compilerOptions".equals(parentKey)) {
                        targetMap = TSCONFIG_COMPILER_OPTIONS;
                    }
                }
            }
        }

        if (targetMap != null) {
            for (SchemaProperty prop : targetMap.values()) {
                if (!existingKeys.contains(prop.name)) {
                    String insertText = "\"" + prop.name + "\": " + (prop.defaultValue != null ? prop.defaultValue : "\"|\"");
                    items.add(new CompletionItem("\"" + prop.name + "\"", insertText, prop.type,
                            CompletionItem.Type.JSON_KEY, 0));
                }
            }
        }

        return items;
    }

    public static String findActivePropertyKey(String text, int offset) {
        if (text == null || offset < 0 || offset > text.length()) return null;
        int colonIdx = -1;
        for (int i = offset - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '{' || c == '}' || c == ',' || c == '[' || c == ']') {
                break;
            }
            if (c == ':') {
                colonIdx = i;
                break;
            }
        }
        if (colonIdx < 0) return null;
        int p = colonIdx - 1;
        while (p >= 0 && Character.isWhitespace(text.charAt(p))) {
            p--;
        }
        if (p < 0) return null;
        char quote = text.charAt(p);
        if (quote != '"' && quote != '\'') {
            int start = p;
            while (start >= 0 && Character.isJavaIdentifierPart(text.charAt(start))) {
                start--;
            }
            return text.substring(start + 1, p + 1).trim();
        }
        int start = p - 1;
        while (start >= 0 && text.charAt(start) != quote && text.charAt(start) != '\n') {
            start--;
        }
        if (start < 0 || text.charAt(start) != quote) return null;
        return text.substring(start + 1, p);
    }

    /**
     * Resolves hover documentation for a schema property.
     */
    public static String getHoverDocumentation(File file, String uri, String keyName, String parentKey) {
        if (keyName == null || keyName.isEmpty()) return null;
        String clean = cleanKey(keyName);

        boolean isPkg = isPackageJson(file, uri);
        boolean isTs = isTsConfig(file, uri);

        if (isPkg) {
            SchemaProperty p = PACKAGE_PROPERTIES.get(clean);
            if (p != null) return p.toMarkdown();
        } else if (isTs) {
            if ("compilerOptions".equals(parentKey) || TSCONFIG_COMPILER_OPTIONS.containsKey(clean)) {
                SchemaProperty p = TSCONFIG_COMPILER_OPTIONS.get(clean);
                if (p != null) return p.toMarkdown();
            }
            SchemaProperty p = TSCONFIG_ROOT_PROPERTIES.get(clean);
            if (p != null) return p.toMarkdown();
        }

        // Fallback search
        if (PACKAGE_PROPERTIES.containsKey(clean)) {
            return PACKAGE_PROPERTIES.get(clean).toMarkdown();
        }
        if (TSCONFIG_COMPILER_OPTIONS.containsKey(clean)) {
            return TSCONFIG_COMPILER_OPTIONS.get(clean).toMarkdown();
        }
        if (TSCONFIG_ROOT_PROPERTIES.containsKey(clean)) {
            return TSCONFIG_ROOT_PROPERTIES.get(clean).toMarkdown();
        }

        return null;
    }

    public static String cleanKey(String key) {
        if (key == null) return "";
        String s = key.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1).trim();
        }
        if (s.startsWith("'") && s.endsWith("'") && s.length() >= 2) {
            return s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    private static String describeNodeType(byte type) {
        switch (type) {
            case JsonSyntaxTree.N_VALUE_STRING: return "string";
            case JsonSyntaxTree.N_VALUE_NUMBER: return "number";
            case JsonSyntaxTree.N_VALUE_BOOL: return "boolean";
            case JsonSyntaxTree.N_VALUE_NULL: return "null";
            case JsonSyntaxTree.N_OBJECT: return "object";
            case JsonSyntaxTree.N_ARRAY: return "array";
            default: return "unknown";
        }
    }
}
