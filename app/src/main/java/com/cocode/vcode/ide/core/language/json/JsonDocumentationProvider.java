package com.cocode.vcode.ide.core.language.json;

import java.util.HashMap;
import java.util.Map;

/**
 * Documentation provider for standard JSON configuration files (package.json, tsconfig.json).
 * Provides schema descriptions and field examples for hover popups.
 */
public final class JsonDocumentationProvider {

    public static final class SchemaProperty {
        public final String name;
        public final String type;
        public final String description;
        public final String example;

        public SchemaProperty(String name, String type, String description, String example) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.example = example;
        }

        public String toMarkdown() {
            StringBuilder sb = new StringBuilder();
            sb.append("### `").append(name).append("`\n\n");
            if (type != null && !type.isEmpty()) {
                sb.append("**Type:** `").append(type).append("`\n\n");
            }
            sb.append(description).append("\n\n");
            if (example != null && !example.isEmpty()) {
                sb.append("**Example:**\n```json\n").append(example).append("\n```");
            }
            return sb.toString().trim();
        }
    }

    private static final Map<String, SchemaProperty> PACKAGE_JSON = new HashMap<>();
    private static final Map<String, SchemaProperty> TSCONFIG_JSON = new HashMap<>();

    static {
        initPackageJson();
        initTsConfigJson();
    }

    private JsonDocumentationProvider() {}

    /**
     * Resolves schema documentation for a property in a JSON file based on the file name.
     */
    public static String getPropertyDoc(String filename, String key) {
        if (key == null || key.isEmpty()) return null;
        String cleanKey = key.replace("\"", "").trim();

        if (filename != null) {
            String lowerFile = filename.toLowerCase();
            if (lowerFile.endsWith("package.json")) {
                SchemaProperty prop = PACKAGE_JSON.get(cleanKey);
                if (prop != null) return prop.toMarkdown();
            } else if (lowerFile.endsWith("tsconfig.json") || lowerFile.endsWith("jsconfig.json")) {
                SchemaProperty prop = TSCONFIG_JSON.get(cleanKey);
                if (prop != null) return prop.toMarkdown();
            }
        }

        // Generic fallback check in package.json then tsconfig
        if (PACKAGE_JSON.containsKey(cleanKey)) {
            return PACKAGE_JSON.get(cleanKey).toMarkdown();
        }
        if (TSCONFIG_JSON.containsKey(cleanKey)) {
            return TSCONFIG_JSON.get(cleanKey).toMarkdown();
        }

        return null;
    }

    private static void initPackageJson() {
        putPkg("name", "string", "The name of the package. Must be lowercase and one word, and may contain hyphens and underscores.", "\"my-awesome-package\"");
        putPkg("version", "string", "Version must be parseable by node-semver, which is bundled with npm as a dependency (e.g. 1.0.0).", "\"1.0.0\"");
        putPkg("description", "string", "Put a description in it. It's a string. This helps people discover your package, as it's listed in npm search.", "\"A useful utility library\"");
        putPkg("main", "string", "The primary entry point to your program. If your package is named 'foo', and a user does require('foo'), then your main module's exports object will be returned.", "\"dist/index.js\"");
        putPkg("module", "string", "An ECMAScript module ID used by build tools like Webpack and Rollup to bundle tree-shaken ES modules.", "\"dist/index.esm.js\"");
        putPkg("types", "string", "Specifies the location of the main TypeScript declaration file for your package.", "\"dist/index.d.ts\"");
        putPkg("typings", "string", "Alternative to 'types'. Specifies the entry declaration file for TypeScript.", "\"dist/index.d.ts\"");
        putPkg("scripts", "object", "The 'scripts' property is a dictionary containing script commands that are run at various times in the lifecycle of your package.", "{\n  \"start\": \"node index.js\",\n  \"test\": \"jest\"\n}");
        putPkg("dependencies", "object", "Dependencies are specified in a simple object that maps a package name to a version range.", "{\n  \"express\": \"^4.18.2\"\n}");
        putPkg("devDependencies", "object", "Packages that are only needed for local development and testing, and will not be installed in production.", "{\n  \"typescript\": \"^5.0.0\",\n  \"jest\": \"^29.0.0\"\n}");
        putPkg("peerDependencies", "object", "Dependencies that your package requires the host application to install, rather than bundling them itself.", "{\n  \"react\": \">=18.0.0\"\n}");
        putPkg("repository", "object | string", "Specify the place where your code lives. This is helpful for people who want to contribute.", "{\n  \"type\": \"git\",\n  \"url\": \"https://github.com/user/repo.git\"\n}");
        putPkg("keywords", "string[]", "Put keywords in it. It's an array of strings. This helps people discover your package as it's listed in npm search.", "[\"web\", \"ide\", \"editor\"]");
        putPkg("author", "object | string", "The person who authored the package.", "\"Your Name <you@example.com>\"");
        putPkg("license", "string", "You should specify a license for your package so that people know how they are permitted to use it, and any restrictions you're placing on it.", "\"MIT\"");
        putPkg("private", "boolean", "If set to true in your package.json, npm will refuse to publish it. This prevents accidental publishing of private repositories.", "true");
        putPkg("exports", "object | string", "The 'exports' field allows defining the entry points of a package when imported by name or loaded via require().", "{\n  \".\": \"./index.js\",\n  \"./sub\": \"./sub.js\"\n}");
        putPkg("files", "string[]", "The 'files' field is an array of file patterns that describes the entries to be included when your package is installed as a dependency.", "[\"dist/\", \"README.md\"]");
        putPkg("bin", "object | string", "A map of command name to local file name. On install, npm will symlink that file into prefix/bin for global installs.", "{\n  \"my-cli\": \"./bin/cli.js\"\n}");
        putPkg("engines", "object", "You can specify the version of node that your stuff works on.", "{\n  \"node\": \">=18.0.0\"\n}");
    }

    private static void initTsConfigJson() {
        putTs("compilerOptions", "object", "Instructs the TypeScript compiler how to compile .ts files to JavaScript.", "{\n  \"target\": \"es2020\",\n  \"module\": \"esnext\"\n}");
        putTs("target", "string", "Set the JavaScript language version for emitted JavaScript and include compatible library declarations.", "\"ES2022\"");
        putTs("module", "string", "Specify what module code is generated.", "\"NodeNext\"");
        putTs("lib", "string[]", "Specify a set of bundled library declaration files that describe the target runtime environment.", "[\"ES2022\", \"DOM\", \"DOM.Iterable\"]");
        putTs("strict", "boolean", "Enable all strict type-checking options. Enables noImplicitAny, strictNullChecks, and other strict flags.", "true");
        putTs("outDir", "string", "Specify an output folder for all emitted files.", "\"./dist\"");
        putTs("rootDir", "string", "Specify the root folder within your source files.", "\"./src\"");
        putTs("baseUrl", "string", "Specify the base directory to resolve non-relative module names.", "\".\"");
        putTs("paths", "object", "Specify a set of entries that re-map imports to additional lookup locations.", "{\n  \"@/*\": [\"src/*\"]\n}");
        putTs("allowJs", "boolean", "Allow JavaScript files to be a part of your program. Use the 'checkJs' option to get errors from these files.", "true");
        putTs("checkJs", "boolean", "Enable error reporting in type-checked JavaScript files.", "true");
        putTs("declaration", "boolean", "Generate .d.ts files from TypeScript and JavaScript files in your project.", "true");
        putTs("sourceMap", "boolean", "Create source map files for emitted JavaScript files.", "true");
        putTs("noEmit", "boolean", "Disable emitting files from a compilation.", "true");
        putTs("esModuleInterop", "boolean", "Emit additional JavaScript to ease support for importing CommonJS modules. This enables allowSyntheticDefaultImports for type compatibility.", "true");
        putTs("skipLibCheck", "boolean", "Skip type checking of declaration files (.d.ts) to speed up compilation.", "true");
        putTs("include", "string[]", "Specifies an array of filenames or patterns to include in the program.", "[\"src/**/*\"]");
        putTs("exclude", "string[]", "Specifies an array of filenames or patterns that should be skipped when resolving 'include'.", "[\"node_modules\", \"dist\"]");
    }

    private static void putPkg(String name, String type, String desc, String example) {
        PACKAGE_JSON.put(name, new SchemaProperty(name, type, desc, example));
    }

    private static void putTs(String name, String type, String desc, String example) {
        TSCONFIG_JSON.put(name, new SchemaProperty(name, type, desc, example));
    }
}
