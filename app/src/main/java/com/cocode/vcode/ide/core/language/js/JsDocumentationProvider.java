package com.cocode.vcode.ide.core.language.js;

import java.util.HashMap;
import java.util.Map;

/**
 * Documentation provider for JavaScript and TypeScript standard library,
 * DOM interfaces, keywords, and global APIs shown in hover popups.
 */
public final class JsDocumentationProvider {

    public static final class JsDocItem {
        public final String signature;
        public final String description;
        public final String[] params;
        public final String returns;
        public final String mdnUrl;

        public JsDocItem(String signature, String description, String[] params, String returns, String mdnUrl) {
            this.signature = signature;
            this.description = description;
            this.params = params != null ? params : new String[0];
            this.returns = returns;
            this.mdnUrl = mdnUrl;
        }

        public String toMarkdown(String language) {
            StringBuilder sb = new StringBuilder();
            sb.append("```").append(language != null ? language : "typescript").append("\n");
            sb.append(signature).append("\n```\n\n");
            sb.append(description).append("\n\n");
            if (params.length > 0) {
                sb.append("**Parameters:**\n");
                for (String p : params) {
                    sb.append("• `").append(p).append("`\n");
                }
                sb.append("\n");
            }
            if (returns != null && !returns.isEmpty()) {
                sb.append("**Returns:** ").append(returns).append("\n\n");
            }
            if (mdnUrl != null && !mdnUrl.isEmpty()) {
                sb.append("[MDN Reference](").append(mdnUrl).append(")");
            }
            return sb.toString().trim();
        }
    }

    private static final Map<String, JsDocItem> DOCS_BY_MEMBER = new HashMap<>();
    private static final Map<String, JsDocItem> GLOBALS = new HashMap<>();
    private static final Map<String, JsDocItem> KEYWORDS = new HashMap<>();

    static {
        initGlobals();
        initConsole();
        initDocument();
        initWindow();
        initStorage();
        initArray();
        initString();
        initObject();
        initMath();
        initJson();
        initPromise();
        initElement();
        initDomTokenList();
        initEvent();
        initDate();
        initMapSet();
        initKeywords();
    }

    private JsDocumentationProvider() {}

    /**
     * Resolves documentation for a member access (e.g. "document.getElementById", "arr.map", "console.log").
     */
    public static String resolveMemberDoc(String receiver, String member, String language) {
        if (member == null || member.isEmpty()) return null;

        // 1. Direct container lookup (e.g. "document.getElementById", "console.log")
        if (receiver != null && !receiver.isEmpty()) {
            String directKey = receiver + "." + member;
            JsDocItem item = DOCS_BY_MEMBER.get(directKey);
            if (item != null) return item.toMarkdown(language);

            // Normalize common receiver aliases (e.g. "doc" -> "document", "win" -> "window")
            if ("doc".equals(receiver)) {
                item = DOCS_BY_MEMBER.get("document." + member);
                if (item != null) return item.toMarkdown(language);
            }
            if ("win".equals(receiver)) {
                item = DOCS_BY_MEMBER.get("window." + member);
                if (item != null) return item.toMarkdown(language);
            }
            if ("el".equals(receiver) || "element".equals(receiver) || "node".equals(receiver)) {
                item = DOCS_BY_MEMBER.get("Element." + member);
                if (item != null) return item.toMarkdown(language);
            }
            if ("classList".equals(receiver)) {
                item = DOCS_BY_MEMBER.get("DOMTokenList." + member);
                if (item != null) return item.toMarkdown(language);
            }
        }

        // 2. Member lookup across prototype containers
        String[] prototypeContainers = new String[]{
                "Array", "String", "Object", "Element", "Document", "DOMTokenList",
                "Storage", "Promise", "Date", "Map", "Set", "Event"
        };
        for (String container : prototypeContainers) {
            JsDocItem item = DOCS_BY_MEMBER.get(container + "." + member);
            if (item != null) return item.toMarkdown(language);
        }

        // 3. Fallback to global signature
        JsDocItem globalItem = GLOBALS.get(member);
        if (globalItem != null) return globalItem.toMarkdown(language);

        return null;
    }

    /**
     * Resolves documentation for a standalone identifier (global function, object, constructor, or keyword).
     */
    public static String resolveIdentifierDoc(String word, String language) {
        if (word == null || word.isEmpty()) return null;

        // 1. Keyword
        JsDocItem kw = KEYWORDS.get(word);
        if (kw != null) return kw.toMarkdown(language);

        // 2. Global API / Constructor / Namespace
        JsDocItem global = GLOBALS.get(word);
        if (global != null) return global.toMarkdown(language);

        // 3. Builtin member without receiver (e.g. "getElementById" or "log")
        return resolveMemberDoc(null, word, language);
    }

    private static void initGlobals() {
        putGlobal("window", "const window: Window & typeof globalThis",
                "Represents a window containing a DOM document; the document property points to the DOM document loaded in that window.",
                new String[0], null, "https://developer.mozilla.org/docs/Web/API/Window");
        putGlobal("document", "const document: Document",
                "Serves as the entry point into the web page's content, which is the DOM tree. It provides global access to create elements and query selectors.",
                new String[0], null, "https://developer.mozilla.org/docs/Web/API/Document");
        putGlobal("console", "const console: Console",
                "Provides access to the browser's debugging console. The methods available include logging, info, warnings, errors, and profiling.",
                new String[0], null, "https://developer.mozilla.org/docs/Web/API/console");
        putGlobal("Math", "const Math: Math",
                "An intrinsic object that provides basic mathematics functionality and constants like PI and E.",
                new String[0], null, "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Math");
        putGlobal("JSON", "const JSON: JSON",
                "An intrinsic object that provides functions to convert JavaScript values to and from the JavaScript Object Notation (JSON) format.",
                new String[0], null, "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/JSON");
        putGlobal("localStorage", "const localStorage: Storage",
                "Allows accessing a Storage object for the Document's origin; the stored data is saved across browser sessions with no expiration date.",
                new String[0], null, "https://developer.mozilla.org/docs/Web/API/Window/localStorage");
        putGlobal("sessionStorage", "const sessionStorage: Storage",
                "Allows accessing a Storage object for the Document's origin; the data stored persists only for the duration of the page session (until tab is closed).",
                new String[0], null, "https://developer.mozilla.org/docs/Web/API/Window/sessionStorage");
        putGlobal("navigator", "const navigator: Navigator",
                "Represents the state and the identity of the user agent. It allows scripts to query it and to register themselves to carry on certain activities.",
                new String[0], null, "https://developer.mozilla.org/docs/Web/API/Navigator");
        putGlobal("location", "const location: Location",
                "Represents the location (URL) of the object it is linked to. Changes done on it are reflected on the object it relates to.",
                new String[0], null, "https://developer.mozilla.org/docs/Web/API/Location");
        putGlobal("history", "const history: History",
                "Allows manipulation of the browser session history, that is the pages visited in the tab or frame that the current page is loaded in.",
                new String[0], null, "https://developer.mozilla.org/docs/Web/API/History");
        putGlobal("fetch", "function fetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response>",
                "Starts the process of fetching a resource from the network, returning a promise which is fulfilled once the response is available.",
                new String[]{"input: The resource that you wish to fetch", "init: An object containing any custom settings that you want to apply to the request"},
                "A Promise that resolves to a Response object.", "https://developer.mozilla.org/docs/Web/API/fetch");
        putGlobal("setTimeout", "function setTimeout(handler: TimerHandler, timeout?: number, ...arguments: any[]): number",
                "Sets a timer which executes a function or specified piece of code once the timer expires.",
                new String[]{"handler: A function to be executed after the timer expires", "timeout: The time, in milliseconds that the timer should wait before the specified function or code is executed"},
                "The returned timeoutID is a positive integer value which identifies the timer created by the call to setTimeout().", "https://developer.mozilla.org/docs/Web/API/setTimeout");
        putGlobal("clearTimeout", "function clearTimeout(id: number | undefined): void",
                "Cancels a timeout previously established by calling setTimeout().",
                new String[]{"id: The identifier of the timeout you want to cancel"}, "void", "https://developer.mozilla.org/docs/Web/API/clearTimeout");
        putGlobal("setInterval", "function setInterval(handler: TimerHandler, timeout?: number, ...arguments: any[]): number",
                "Repeatedly calls a function or executes a code snippet, with a fixed time delay between each call.",
                new String[]{"handler: A function to be executed every timeout milliseconds", "timeout: The time, in milliseconds, the timer should delay in between executions of the specified function or code"},
                "A unique intervalID which can be passed to clearInterval().", "https://developer.mozilla.org/docs/Web/API/setInterval");
        putGlobal("clearInterval", "function clearInterval(id: number | undefined): void",
                "Cancels a timed, repeating action which was previously established by a call to setInterval().",
                new String[]{"id: The identifier of the repeated action you want to cancel"}, "void", "https://developer.mozilla.org/docs/Web/API/clearInterval");
        putGlobal("requestAnimationFrame", "function requestAnimationFrame(callback: FrameRequestCallback): number",
                "Tells the browser that you wish to perform an animation and requests that the browser calls a specified function to update an animation before the next repaint.",
                new String[]{"callback: The function to call when it's time to update your animation for the next repaint"},
                "A long integer value, the request id, that uniquely identifies the entry in the callback list.", "https://developer.mozilla.org/docs/Web/API/window/requestAnimationFrame");
        putGlobal("cancelAnimationFrame", "function cancelAnimationFrame(handle: number): void",
                "Cancels an animation frame request previously scheduled through a call to requestAnimationFrame().",
                new String[]{"handle: The ID value returned by the call to requestAnimationFrame() that requested the callback"}, "void", "https://developer.mozilla.org/docs/Web/API/window/cancelAnimationFrame");
        putGlobal("queueMicrotask", "function queueMicrotask(callback: VoidFunction): void",
                "Queues a microtask to be executed at a safe time prior to control returning to the browser's event loop.",
                new String[]{"callback: A function to be executed when the browser runtime is idle"}, "void", "https://developer.mozilla.org/docs/Web/API/queueMicrotask");
        putGlobal("structuredClone", "function structuredClone<T>(value: T, options?: StructuredSerializeOptions): T",
                "Creates a deep clone of a given value using the structured clone algorithm.",
                new String[]{"value: The object to be cloned", "options: An object with a transfer property containing an array of transferable objects"},
                "A deep copy of the original value.", "https://developer.mozilla.org/docs/Web/API/structuredClone");
        putGlobal("parseInt", "function parseInt(string: string, radix?: number): number",
                "Converts a string to an integer.",
                new String[]{"string: A string to convert into a number", "radix: An integer between 2 and 36 that represents the radix (the base in mathematical numeral systems)"},
                "An integer parsed from the given string, or NaN if the first non-whitespace character cannot be converted to a number.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/parseInt");
        putGlobal("parseFloat", "function parseFloat(string: string): number",
                "Converts a string to a floating-point number.",
                new String[]{"string: A string that represents the value you want to parse"},
                "A floating point number parsed from the given string, or NaN.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/parseFloat");
        putGlobal("alert", "function alert(message?: any): void",
                "Instructs the browser to display a dialog with an optional message, and to wait until the user dismisses the dialog.",
                new String[]{"message: A string or object to be displayed in the alert dialog"}, "void", "https://developer.mozilla.org/docs/Web/API/Window/alert");
        putGlobal("confirm", "function confirm(message?: string): boolean",
                "Instructs the browser to display a dialog with an optional message, and to wait until the user either confirms or cancels the dialog.",
                new String[]{"message: A string to be displayed in the confirmation dialog"}, "true if the user clicked OK; otherwise false.", "https://developer.mozilla.org/docs/Web/API/Window/confirm");
        putGlobal("prompt", "function prompt(message?: string, _default?: string): string | null",
                "Instructs the browser to display a dialog with an optional message prompting the user to input some text, and to wait until the user either submits the text or cancels the dialog.",
                new String[]{"message: A string of text to display to the user", "_default: A string containing the default value displayed in the text input field"},
                "A string containing the text entered by the user, or null.", "https://developer.mozilla.org/docs/Web/API/Window/prompt");

        // Constructors & Types
        putGlobal("Array", "interface ArrayConstructor\nnew (arrayLength?: number): any[]",
                "The JavaScript Array class is a global object that is used in the construction of arrays; which are high-level, list-like objects.",
                new String[0], null, "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Array");
        putGlobal("String", "interface StringConstructor\nnew (value?: any): String",
                "The String object is used to represent and manipulate a sequence of characters.",
                new String[0], null, "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/String");
        putGlobal("Object", "interface ObjectConstructor\nnew (value?: any): Object",
                "The Object class represents one of JavaScript's data types. It is used to store various keyed collections and more complex entities.",
                new String[0], null, "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Object");
        putGlobal("Promise", "interface PromiseConstructor\nnew <T>(executor: (resolve: (value: T) => void, reject: (reason?: any) => void) => void): Promise<T>",
                "The Promise object represents the eventual completion (or failure) of an asynchronous operation and its resulting value.",
                new String[0], null, "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Promise");
        putGlobal("Map", "interface MapConstructor\nnew <K, V>(entries?: readonly (readonly [K, V])[] | null): Map<K, V>",
                "The Map object holds key-value pairs and remembers the original insertion order of the keys. Any value (both objects and primitive values) may be used as either a key or a value.",
                new String[0], null, "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Map");
        putGlobal("Set", "interface SetConstructor\nnew <T>(values?: readonly T[] | null): Set<T>",
                "The Set object lets you store unique values of any type, whether primitive values or object references.",
                new String[0], null, "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Set");
        putGlobal("Date", "interface DateConstructor\nnew (): Date",
                "JavaScript Date objects represent a single moment in time in a platform-independent format. Date objects encapsulate an integral number that represents milliseconds since the midnight at the beginning of January 1, 1970, UTC.",
                new String[0], null, "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Date");
        putGlobal("RegExp", "interface RegExpConstructor\nnew (pattern: RegExp | string, flags?: string): RegExp",
                "The RegExp object is used for matching text with a pattern.",
                new String[0], null, "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/RegExp");
        putGlobal("Error", "interface ErrorConstructor\nnew (message?: string): Error",
                "Error objects are thrown when runtime errors occur. The Error object can also be used as a base object for user-defined exceptions.",
                new String[0], null, "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Error");
        putGlobal("URL", "interface URL\nnew (url: string | URL, base?: string | URL): URL",
                "The URL interface is used to parse, construct, normalize, and encode URLs.",
                new String[0], null, "https://developer.mozilla.org/docs/Web/API/URL");
        putGlobal("FormData", "interface FormData\nnew (form?: HTMLFormElement): FormData",
                "The FormData interface provides a way to construct a set of key/value pairs representing form fields and their values, which can be sent using fetch() or XMLHttpRequest.",
                new String[0], null, "https://developer.mozilla.org/docs/Web/API/FormData");
    }

    private static void initConsole() {
        putMember("console", "log", "(method) Console.log(...data: any[]): void",
                "Outputs a message to the console. The message may be a single string (with optional substitution values), or it may be any one or more JavaScript objects.",
                new String[]{"...data: The JavaScript objects with which to output"}, "void", "https://developer.mozilla.org/docs/Web/API/console/log");
        putMember("console", "warn", "(method) Console.warn(...data: any[]): void",
                "Outputs a warning message to the console with yellow warning styling and an exclamation point icon.",
                new String[]{"...data: The JavaScript objects with which to output"}, "void", "https://developer.mozilla.org/docs/Web/API/console/warn");
        putMember("console", "error", "(method) Console.error(...data: any[]): void",
                "Outputs an error message to the console with red error styling and an error icon.",
                new String[]{"...data: The JavaScript objects with which to output"}, "void", "https://developer.mozilla.org/docs/Web/API/console/error");
        putMember("console", "info", "(method) Console.info(...data: any[]): void",
                "Outputs an informational message to the console.",
                new String[]{"...data: The JavaScript objects with which to output"}, "void", "https://developer.mozilla.org/docs/Web/API/console/info");
        putMember("console", "debug", "(method) Console.debug(...data: any[]): void",
                "Outputs a message to the console with the log level debug.",
                new String[]{"...data: The JavaScript objects with which to output"}, "void", "https://developer.mozilla.org/docs/Web/API/console/debug");
        putMember("console", "table", "(method) Console.table(tabularData?: any, properties?: string[]): void",
                "Displays tabular data as a table in the console.",
                new String[]{"tabularData: The data to display as a table", "properties: An array of property names to include in the table"}, "void", "https://developer.mozilla.org/docs/Web/API/console/table");
        putMember("console", "clear", "(method) Console.clear(): void",
                "Clears the console if the console allows it.",
                new String[0], "void", "https://developer.mozilla.org/docs/Web/API/console/clear");
        putMember("console", "time", "(method) Console.time(label?: string): void",
                "Starts a timer you can use to track how long an operation takes. You give each timer a unique name.",
                new String[]{"label: The name to give the new timer. Defaults to 'default'."}, "void", "https://developer.mozilla.org/docs/Web/API/console/time");
        putMember("console", "timeEnd", "(method) Console.timeEnd(label?: string): void",
                "Stops a timer that was previously started by calling console.time() and prints the elapsed time in milliseconds.",
                new String[]{"label: The name of the timer to stop. Defaults to 'default'."}, "void", "https://developer.mozilla.org/docs/Web/API/console/timeEnd");
    }

    private static void initDocument() {
        putMember("document", "getElementById", "(method) Document.getElementById(elementId: string): HTMLElement | null",
                "Returns a reference to the first object with the specified value of the ID attribute.",
                new String[]{"elementId: String that specifies the ID value"}, "An Element object representing the element whose id property matches the specified string, or null.", "https://developer.mozilla.org/docs/Web/API/Document/getElementById");
        putMember("document", "querySelector", "(method) Document.querySelector<E extends Element = Element>(selectors: string): E | null",
                "Returns the first Element within the document that matches the specified selector, or group of selectors. If no matches are found, null is returned.",
                new String[]{"selectors: A string containing one or more selectors to match"}, "An Element object representing the first element in the document that matches the specified set of CSS selectors, or null.", "https://developer.mozilla.org/docs/Web/API/Document/querySelector");
        putMember("document", "querySelectorAll", "(method) Document.querySelectorAll<E extends Element = Element>(selectors: string): NodeListOf<E>",
                "Returns a static (not live) NodeList representing a list of the document's elements that match the specified group of selectors.",
                new String[]{"selectors: A string containing one or more selectors to match"}, "A non-live NodeList containing one Element object for each element that matches at least one of the specified selectors.", "https://developer.mozilla.org/docs/Web/API/Document/querySelectorAll");
        putMember("document", "createElement", "(method) Document.createElement<K extends keyof HTMLElementTagNameMap>(tagName: K, options?: ElementCreationOptions): HTMLElementTagNameMap[K]",
                "Creates an instance of the element for the specified tag.",
                new String[]{"tagName: The name of an element", "options: An optional ElementCreationOptions object containing a single property named 'is'"}, "The new Element.", "https://developer.mozilla.org/docs/Web/API/Document/createElement");
        putMember("document", "createTextNode", "(method) Document.createTextNode(data: string): Text",
                "Creates a new Text node with the specified string.",
                new String[]{"data: A string containing the data to be put in the text node"}, "A Text node.", "https://developer.mozilla.org/docs/Web/API/Document/createTextNode");
        putMember("document", "addEventListener", "(method) EventTarget.addEventListener(type: string, listener: EventListenerOrEventListenerObject, options?: boolean | AddEventListenerOptions): void",
                "Appends an event listener for events whose type attribute value is type. The callback argument sets the callback that will be invoked when the event is dispatched.",
                new String[]{"type: A case-sensitive string representing the event type to listen for", "listener: The object that receives a notification when an event of the specified type occurs"}, "void", "https://developer.mozilla.org/docs/Web/API/EventTarget/addEventListener");
        putMember("document", "body", "(property) Document.body: HTMLElement",
                "The <body> or <frameset> node of the current document, or null if no such element exists.",
                new String[0], null, "https://developer.mozilla.org/docs/Web/API/Document/body");
        putMember("document", "head", "(property) Document.head: HTMLHeadElement",
                "Returns the <head> element of the current document. If there are more than one <head> elements, the first one is returned.",
                new String[0], null, "https://developer.mozilla.org/docs/Web/API/Document/head");
        putMember("document", "title", "(property) Document.title: string",
                "Gets or sets the title of the current document.",
                new String[0], null, "https://developer.mozilla.org/docs/Web/API/Document/title");
        putMember("document", "cookie", "(property) Document.cookie: string",
                "Gets and sets the cookies associated with the current document.",
                new String[0], null, "https://developer.mozilla.org/docs/Web/API/Document/cookie");
    }

    private static void initWindow() {
        putMember("window", "addEventListener", "(method) EventTarget.addEventListener(type: string, listener: EventListenerOrEventListenerObject, options?: boolean | AddEventListenerOptions): void",
                "Sets up a function that will be called whenever the specified event is delivered to the target.",
                new String[]{"type: A case-sensitive string representing the event type to listen for", "listener: The callback function to be called"}, "void", "https://developer.mozilla.org/docs/Web/API/EventTarget/addEventListener");
        putMember("window", "removeEventListener", "(method) EventTarget.removeEventListener(type: string, listener: EventListenerOrEventListenerObject, options?: boolean | EventListenerOptions): void",
                "Removes the event listener in target's event listener list with the same type, callback, and options.",
                new String[]{"type: A string which specifies the type of event for which to remove an event listener", "listener: The EventListener function to remove from the event target"}, "void", "https://developer.mozilla.org/docs/Web/API/EventTarget/removeEventListener");
        putMember("window", "scrollTo", "(method) Window.scrollTo(options?: ScrollToOptions): void\nWindow.scrollTo(x: number, y: number): void",
                "Scrolls to a particular set of coordinates in the document.",
                new String[]{"x: The pixel along the horizontal axis", "y: The pixel along the vertical axis"}, "void", "https://developer.mozilla.org/docs/Web/API/Window/scrollTo");
        putMember("window", "scrollBy", "(method) Window.scrollBy(options?: ScrollToOptions): void\nWindow.scrollBy(x: number, y: number): void",
                "Scrolls the document in the window by the given amount.",
                new String[]{"x: The pixel along the horizontal axis to scroll by", "y: The pixel along the vertical axis to scroll by"}, "void", "https://developer.mozilla.org/docs/Web/API/Window/scrollBy");
        putMember("window", "getComputedStyle", "(method) Window.getComputedStyle(elt: Element, pseudoElt?: string | null): CSSStyleDeclaration",
                "Returns an object that reports the values of all CSS properties of an element after applying the active stylesheets and resolving any basic computation that may contain.",
                new String[]{"elt: The Element for which to get the computed style", "pseudoElt: A string specifying the pseudo-element to match. Omit or null for normal elements."}, "A CSSStyleDeclaration containing the computed styles.", "https://developer.mozilla.org/docs/Web/API/Window/getComputedStyle");
        putMember("window", "matchMedia", "(method) Window.matchMedia(query: string): MediaQueryList",
                "Returns a new MediaQueryList object that can then be used to determine if the document matches the media query string, as well as to monitor the document to detect when it matches (or stops matching) that media query.",
                new String[]{"query: A string representing the media query for which to return a new MediaQueryList object"}, "A new MediaQueryList object for the media query.", "https://developer.mozilla.org/docs/Web/API/Window/matchMedia");
    }

    private static void initStorage() {
        putMember("Storage", "getItem", "(method) Storage.getItem(key: string): string | null",
                "Returns the current value associated with the given key, or null if the given key does not exist.",
                new String[]{"key: A string containing the name of the key you want to retrieve the value of"}, "A string containing the value of the key, or null.", "https://developer.mozilla.org/docs/Web/API/Storage/getItem");
        putMember("Storage", "setItem", "(method) Storage.setItem(key: string, value: string): void",
                "Sets the value of the pair identified by key to value, creating a new key/value pair if none existed for key previously.",
                new String[]{"key: A string containing the name of the key you want to create/update", "value: A string containing the value you want to give the key"}, "void", "https://developer.mozilla.org/docs/Web/API/Storage/setItem");
        putMember("Storage", "removeItem", "(method) Storage.removeItem(key: string): void",
                "Removes the key/value pair with the given key, if a key/value pair with the given key exists.",
                new String[]{"key: A string containing the name of the key you want to remove"}, "void", "https://developer.mozilla.org/docs/Web/API/Storage/removeItem");
        putMember("Storage", "clear", "(method) Storage.clear(): void",
                "Removes all key/value pairs, if there are any.",
                new String[0], "void", "https://developer.mozilla.org/docs/Web/API/Storage/clear");
        putMember("Storage", "key", "(method) Storage.key(index: number): string | null",
                "Returns the name of the nth key in the storage, or null if n is greater than or equal to the number of key/value pairs in the storage.",
                new String[]{"index: An integer representing the number of the key you want to get the name of"}, "A string containing the name of the key, or null.", "https://developer.mozilla.org/docs/Web/API/Storage/key");

        // Alias for localStorage and sessionStorage
        putMember("localStorage", "getItem", DOCS_BY_MEMBER.get("Storage.getItem"));
        putMember("localStorage", "setItem", DOCS_BY_MEMBER.get("Storage.setItem"));
        putMember("localStorage", "removeItem", DOCS_BY_MEMBER.get("Storage.removeItem"));
        putMember("localStorage", "clear", DOCS_BY_MEMBER.get("Storage.clear"));
        putMember("sessionStorage", "getItem", DOCS_BY_MEMBER.get("Storage.getItem"));
        putMember("sessionStorage", "setItem", DOCS_BY_MEMBER.get("Storage.setItem"));
        putMember("sessionStorage", "removeItem", DOCS_BY_MEMBER.get("Storage.removeItem"));
        putMember("sessionStorage", "clear", DOCS_BY_MEMBER.get("Storage.clear"));
    }

    private static void initArray() {
        putMember("Array", "map", "(method) Array<T>.map<U>(callbackfn: (value: T, index: number, array: T[]) => U, thisArg?: any): U[]",
                "Calls a defined callback function on each element of an array, and returns an array that contains the results.",
                new String[]{"callbackfn: A function that accepts up to three arguments. The map method calls the callbackfn function one time for each element in the array.", "thisArg: An object to which the this keyword can refer in the callbackfn function."},
                "A new array with each element being the result of the callback function.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Array/map");
        putMember("Array", "filter", "(method) Array<T>.filter(predicate: (value: T, index: number, array: T[]) => unknown, thisArg?: any): T[]",
                "Returns the elements of an array that meet the condition specified in a callback function.",
                new String[]{"predicate: A function that accepts up to three arguments. The filter method calls the predicate function one time for each element in the array.", "thisArg: An object to which the this keyword can refer in the predicate function."},
                "A shallow copy of a portion of the given array, filtered down to just the elements from the given array that pass the test.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Array/filter");
        putMember("Array", "reduce", "(method) Array<T>.reduce(callbackfn: (previousValue: T, currentValue: T, currentIndex: number, array: T[]) => T, initialValue?: T): T",
                "Calls the specified callback function for all the elements in an array. The return value of the callback function is the accumulated result, and is provided as an argument in the next call to the callback function.",
                new String[]{"callbackfn: A function that accepts up to four arguments", "initialValue: If specified, used as the initial value to start the accumulation"},
                "The value that results from the reduction.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Array/reduce");
        putMember("Array", "forEach", "(method) Array<T>.forEach(callbackfn: (value: T, index: number, array: T[]) => void, thisArg?: any): void",
                "Performs the specified action for each element in an array.",
                new String[]{"callbackfn: A function that accepts up to three arguments. forEach calls the callbackfn function one time for each element in the array.", "thisArg: An object to which the this keyword can refer in the callbackfn function."},
                "undefined", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Array/forEach");
        putMember("Array", "find", "(method) Array<T>.find(predicate: (value: T, index: number, obj: T[]) => unknown, thisArg?: any): T | undefined",
                "Returns the value of the first element in the array where predicate is true, and undefined otherwise.",
                new String[]{"predicate: find calls predicate once for each element of the array, in ascending order, until it finds one where predicate returns true.", "thisArg: An object to which the this keyword can refer in the predicate function."},
                "The first element in the array that satisfies the provided testing function. Otherwise, undefined is returned.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Array/find");
        putMember("Array", "findIndex", "(method) Array<T>.findIndex(predicate: (value: T, index: number, obj: T[]) => unknown, thisArg?: any): number",
                "Returns the index of the first element in the array where predicate is true, and -1 otherwise.",
                new String[]{"predicate: findIndex calls predicate once for each element of the array, in ascending order, until it finds one where predicate returns true.", "thisArg: An object to which the this keyword can refer in the predicate function."},
                "The index of the first element in the array that passes the test. Otherwise, -1.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Array/findIndex");
        putMember("Array", "includes", "(method) Array<T>.includes(searchElement: T, fromIndex?: number): boolean",
                "Determines whether an array includes a certain element, returning true or false as appropriate.",
                new String[]{"searchElement: The element to search for", "fromIndex: The position in this array at which to begin searching for searchElement"},
                "A boolean value which is true if the value searchElement is found within the array.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Array/includes");
        putMember("Array", "push", "(method) Array<T>.push(...items: T[]): number",
                "Appends new elements to the end of an array, and returns the new length of the array.",
                new String[]{"...items: New elements to add to the array"}, "The new length property of the object upon which the method was called.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Array/push");
        putMember("Array", "pop", "(method) Array<T>.pop(): T | undefined",
                "Removes the last element from an array and returns it. If the array is empty, undefined is returned and the array is not modified.",
                new String[0], "The removed element from the array; undefined if the array is empty.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Array/pop");
        putMember("Array", "shift", "(method) Array<T>.shift(): T | undefined",
                "Removes the first element from an array and returns it. If the array is empty, undefined is returned and the array is not modified.",
                new String[0], "The removed element from the array; undefined if the array is empty.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Array/shift");
        putMember("Array", "unshift", "(method) Array<T>.unshift(...items: T[]): number",
                "Inserts new elements at the start of an array, and returns the new length of the array.",
                new String[]{"...items: Elements to insert at the start of the array"}, "The new length of the array.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Array/unshift");
        putMember("Array", "slice", "(method) Array<T>.slice(start?: number, end?: number): T[]",
                "Returns a copy of a section of an array. For both start and end, a negative index can be used to indicate an offset from the end of the array.",
                new String[]{"start: The beginning index of the specified portion of the array", "end: The end index of the specified portion of the array"},
                "A new array containing the extracted elements.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Array/slice");
        putMember("Array", "splice", "(method) Array<T>.splice(start: number, deleteCount?: number, ...items: T[]): T[]",
                "Removes elements from an array and, if necessary, inserts new elements in their place, returning the deleted elements.",
                new String[]{"start: The zero-based location in the array from which to start removing elements", "deleteCount: The number of elements to remove", "...items: Elements to insert into the array in place of the deleted elements"},
                "An array containing the elements that were deleted.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Array/splice");
        putMember("Array", "join", "(method) Array<T>.join(separator?: string): string",
                "Adds all the elements of an array into a string, separated by the specified separator string.",
                new String[]{"separator: A string used to separate one element of the array from the next in the resulting string. If omitted, the array elements are separated with a comma."},
                "A string with all array elements joined.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Array/join");
        putMember("Array", "concat", "(method) Array<T>.concat(...items: ConcatArray<T>[]): T[]",
                "Combines two or more arrays. This method returns a new array without modifying any existing arrays.",
                new String[]{"...items: Additional arrays and/or items to add to the end of the array"},
                "A new Array instance.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Array/concat");
        putMember("Array", "indexOf", "(method) Array<T>.indexOf(searchElement: T, fromIndex?: number): number",
                "Returns the index of the first occurrence of a value in an array, or -1 if it is not present.",
                new String[]{"searchElement: The value to locate in the array", "fromIndex: The array index at which to begin the search"},
                "The first index of the element in the array; -1 if not found.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Array/indexOf");
        putMember("Array", "isArray", "(method) ArrayConstructor.isArray(arg: any): arg is any[]",
                "Determines whether the passed value is an Array.",
                new String[]{"arg: The value to be checked"}, "true if the value is an Array; otherwise, false.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Array/isArray");
    }

    private static void initString() {
        putMember("String", "slice", "(method) String.slice(start?: number, end?: number): string",
                "Returns a section of a string. The slice() method extracts a part of a string and returns it as a new string, without modifying the original string.",
                new String[]{"start: The index to the beginning of the specified portion of stringObj", "end: The index to the end of the specified portion of stringObj"},
                "A new string containing the extracted section of the string.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/String/slice");
        putMember("String", "split", "(method) String.split(separator: string | RegExp, limit?: number): string[]",
                "Splits a String object into an array of strings by separating the string into substrings, using a specified separator string to determine where to make each split.",
                new String[]{"separator: Specifies the character or regular expression to use for splitting the string", "limit: An integer that limits the number of splits to be found"},
                "An Array of strings, split at each point where the separator occurs in the given string.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/String/split");
        putMember("String", "replace", "(method) String.replace(searchValue: string | RegExp, replaceValue: string): string",
                "Replaces text in a string, using a regular expression or search string.",
                new String[]{"searchValue: A string or regular expression to search for", "replaceValue: A string containing the text to replace for every successful match"},
                "A new string, with one, some, or all matches of the pattern replaced by the specified replacement.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/String/replace");
        putMember("String", "replaceAll", "(method) String.replaceAll(searchValue: string | RegExp, replaceValue: string): string",
                "Replace all instances of a substring in a string, using a regular expression or search string.",
                new String[]{"searchValue: A string or regular expression to search for", "replaceValue: A string containing the text to replace for every successful match"},
                "A new string, with all matches of a pattern replaced by a replacement.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/String/replaceAll");
        putMember("String", "trim", "(method) String.trim(): string",
                "Removes the leading and trailing white space and line terminator characters from a string.",
                new String[0], "A new string representing the calling string stripped of whitespace from both ends.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/String/trim");
        putMember("String", "toLowerCase", "(method) String.toLowerCase(): string",
                "Converts all the alphabetic characters in a string to lowercase.",
                new String[0], "A new string representing the calling string converted to lower case.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/String/toLowerCase");
        putMember("String", "toUpperCase", "(method) String.toUpperCase(): string",
                "Converts all the alphabetic characters in a string to uppercase.",
                new String[0], "A new string representing the calling string converted to upper case.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/String/toUpperCase");
        putMember("String", "includes", "(method) String.includes(searchString: string, position?: number): boolean",
                "Performs a case-sensitive search to determine whether one string may be found within another string, returning true or false as appropriate.",
                new String[]{"searchString: The characters to be searched for within this string", "position: The position within the string at which to begin searching for searchString. Defaults to 0."},
                "true if the search string is found anywhere within the given string; otherwise, false.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/String/includes");
        putMember("String", "startsWith", "(method) String.startsWith(searchString: string, position?: number): boolean",
                "Determines whether the beginning of string instances match a search string.",
                new String[]{"searchString: The characters to be searched for at the start of this string", "position: The position in this string at which to begin searching for searchString. Defaults to 0."},
                "true if the given characters are found at the beginning of the string; otherwise, false.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/String/startsWith");
        putMember("String", "endsWith", "(method) String.endsWith(searchString: string, endPosition?: number): boolean",
                "Determines whether a string ends with the characters of a specified string, returning true or false as appropriate.",
                new String[]{"searchString: The characters to be searched for at the end of this string", "endPosition: The end position at which the string is considered to end. Defaults to string.length."},
                "true if the given characters are found at the end of the string; otherwise, false.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/String/endsWith");
    }

    private static void initObject() {
        putMember("Object", "keys", "(method) ObjectConstructor.keys(o: object): string[]",
                "Returns the names of the enumerable string properties and methods of an object.",
                new String[]{"o: Object that contains the properties and methods. This can be an object that you created or an existing Document Object Model (DOM) object."},
                "An array of strings representing the given object's own enumerable string-keyed property keys.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Object/keys");
        putMember("Object", "values", "(method) ObjectConstructor.values<T>(o: { [s: string]: T; } | ArrayLike<T>): T[]",
                "Returns an array of values of the enumerable properties of an object.",
                new String[]{"o: Object that contains the properties and methods. This can be an object that you created or an existing Document Object Model (DOM) object."},
                "An array containing the given object's own enumerable string-keyed property values.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Object/values");
        putMember("Object", "entries", "(method) ObjectConstructor.entries<T>(o: { [s: string]: T; } | ArrayLike<T>): [string, T][]",
                "Returns an array of key/values of the enumerable properties of an object.",
                new String[]{"o: Object that contains the properties and methods."},
                "An array of the given object's own enumerable string-keyed property key-value pairs.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Object/entries");
        putMember("Object", "assign", "(method) ObjectConstructor.assign<T extends {}, U>(target: T, source: U): T & U",
                "Copies the values of all enumerable own properties from one or more source objects to a target object. Returns the target object.",
                new String[]{"target: The target object to copy to", "source: The source object from which to copy properties"},
                "The target object.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Object/assign");
        putMember("Object", "freeze", "(method) ObjectConstructor.freeze<T>(a: T): Readonly<T>",
                "Prevents the modification of existing property attributes and values, and prevents the addition of new properties.",
                new String[]{"a: The object to freeze"}, "The object that was passed to the function.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Object/freeze");
    }

    private static void initMath() {
        putMember("Math", "max", "(method) Math.max(...values: number[]): number",
                "Returns the larger of a set of supplied numeric expressions.",
                new String[]{"...values: Numeric expressions to be evaluated"}, "The largest of the given numbers. If any argument is or converts to NaN, returns NaN.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Math/max");
        putMember("Math", "min", "(method) Math.min(...values: number[]): number",
                "Returns the smaller of a set of supplied numeric expressions.",
                new String[]{"...values: Numeric expressions to be evaluated"}, "The smallest of the given numbers. If any argument is or converts to NaN, returns NaN.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Math/min");
        putMember("Math", "floor", "(method) Math.floor(x: number): number",
                "Returns the greatest integer less than or equal to its numeric argument.",
                new String[]{"x: A numeric expression"}, "The smallest integer greater than or equal to the given number.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Math/floor");
        putMember("Math", "ceil", "(method) Math.ceil(x: number): number",
                "Returns the smallest integer greater than or equal to its numeric argument.",
                new String[]{"x: A numeric expression"}, "The smallest integer greater than or equal to the given number.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Math/ceil");
        putMember("Math", "round", "(method) Math.round(x: number): number",
                "Returns a supplied numeric expression rounded to the nearest integer.",
                new String[]{"x: The value to be rounded to the nearest integer"}, "The value of the given number rounded to the nearest integer.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Math/round");
        putMember("Math", "abs", "(method) Math.abs(x: number): number",
                "Returns the absolute value of a number (the value without regard to whether it is positive or negative).",
                new String[]{"x: A numeric expression for which the absolute value is needed"}, "The absolute value of the given number.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Math/abs");
        putMember("Math", "random", "(method) Math.random(): number",
                "Returns a pseudorandom number between 0 and 1 (inclusive of 0, but not 1).",
                new String[0], "A floating-point, pseudo-random number between 0 (inclusive) and 1 (exclusive).", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Math/random");
    }

    private static void initJson() {
        putMember("JSON", "parse", "(method) JSON.parse(text: string, reviver?: (this: any, key: string, value: any) => any): any",
                "Converts a JavaScript Object Notation (JSON) string into an object.",
                new String[]{"text: A valid JSON string", "reviver: A function that transforms the results. This function is called for each member of the object."},
                "The Object, Array, string, number, boolean, or null value corresponding to the given JSON text.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/JSON/parse");
        putMember("JSON", "stringify", "(method) JSON.stringify(value: any, replacer?: (this: any, key: string, value: any) => any, space?: string | number): string",
                "Converts a JavaScript value to a JavaScript Object Notation (JSON) string.",
                new String[]{"value: The value to convert to a JSON string", "replacer: A function that alters the behavior of the stringification process, or an array of strings or numbers naming properties of value that should be included in the output", "space: A String or Number object that's used to insert white space into the output JSON string for readability purposes"},
                "A JSON string representing the given value.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/JSON/stringify");
    }

    private static void initPromise() {
        putMember("Promise", "then", "(method) Promise<T>.then<TResult1 = T, TResult2 = never>(onfulfilled?: ((value: T) => TResult1 | PromiseLike<TResult1>) | null, onrejected?: ((reason: any) => TResult2 | PromiseLike<TResult2>) | null): Promise<TResult1 | TResult2>",
                "Attaches callbacks for the resolution and/or rejection of the Promise.",
                new String[]{"onfulfilled: The callback to execute when the Promise is resolved", "onrejected: The callback to execute when the Promise is rejected"},
                "A new Promise immediately.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Promise/then");
        putMember("Promise", "catch", "(method) Promise<T>.catch<TResult = never>(onrejected?: ((reason: any) => TResult | PromiseLike<TResult>) | null): Promise<T | TResult>",
                "Attaches a callback for only the rejection of the Promise.",
                new String[]{"onrejected: The callback to execute when the Promise is rejected"},
                "A new Promise immediately.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Promise/catch");
        putMember("Promise", "finally", "(method) Promise<T>.finally(onfinally?: (() => void) | null): Promise<T>",
                "Attaches a callback that is invoked when the Promise is settled (fulfilled or rejected). The resolved value cannot be modified from the callback.",
                new String[]{"onfinally: The callback to execute when the Promise is settled (fulfilled or rejected)"},
                "A new Promise immediately.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Promise/finally");
        putMember("Promise", "all", "(method) PromiseConstructor.all<T>(values: Iterable<T | PromiseLike<T>>): Promise<T[]>",
                "Creates a Promise that is resolved with an array of results when all of the provided Promises resolve, or rejected when any Promise is rejected.",
                new String[]{"values: An array of Promises"},
                "A Promise that resolves to an array of the results of the input promises.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Promise/all");
        putMember("Promise", "resolve", "(method) PromiseConstructor.resolve<T>(value: T | PromiseLike<T>): Promise<T>",
                "Creates a new resolved promise for the provided value.",
                new String[]{"value: The value the promise was resolved with"},
                "A Promise that is resolved with the given value.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Promise/resolve");
        putMember("Promise", "reject", "(method) PromiseConstructor.reject<T = never>(reason?: any): Promise<T>",
                "Creates a new rejected promise for the provided reason.",
                new String[]{"reason: The reason the promise was rejected"},
                "A Promise that is rejected with the given reason.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Promise/reject");
    }

    private static void initElement() {
        putMember("Element", "getAttribute", "(method) Element.getAttribute(qualifiedName: string): string | null",
                "Returns the value of a specified attribute on the element. If the given attribute does not exist, the value returned will either be null or \"\" (the empty string).",
                new String[]{"qualifiedName: The name of the attribute whose value you want to get"}, "The attribute's value, or null if the attribute does not exist.", "https://developer.mozilla.org/docs/Web/API/Element/getAttribute");
        putMember("Element", "setAttribute", "(method) Element.setAttribute(qualifiedName: string, value: string): void",
                "Sets the value of an attribute on the specified element. If the attribute already exists, the value is updated; otherwise a new attribute is added with the specified name and value.",
                new String[]{"qualifiedName: A string specifying the name of the attribute whose value is to be set", "value: A string containing the value to assign to the attribute"}, "void", "https://developer.mozilla.org/docs/Web/API/Element/setAttribute");
        putMember("Element", "removeAttribute", "(method) Element.removeAttribute(qualifiedName: string): void",
                "Removes the element's attribute with the specified name.",
                new String[]{"qualifiedName: A string specifying the name of the attribute to remove from the element"}, "void", "https://developer.mozilla.org/docs/Web/API/Element/removeAttribute");
        putMember("Element", "hasAttribute", "(method) Element.hasAttribute(qualifiedName: string): boolean",
                "Returns a boolean value indicating whether the specified element has the specified attribute or not.",
                new String[]{"qualifiedName: Is a string representing the name of the attribute"}, "true if the element has the specified attribute, otherwise false.", "https://developer.mozilla.org/docs/Web/API/Element/hasAttribute");
        putMember("Element", "classList", "(property) Element.classList: DOMTokenList",
                "Returns a live DOMTokenList collection of the class attributes of the element. This can then be used to manipulate the class list.",
                new String[0], null, "https://developer.mozilla.org/docs/Web/API/Element/classList");
        putMember("Element", "innerHTML", "(property) Element.innerHTML: string",
                "Gets or sets the HTML or XML markup contained within the element.",
                new String[0], null, "https://developer.mozilla.org/docs/Web/API/Element/innerHTML");
        putMember("Element", "textContent", "(property) Node.textContent: string | null",
                "Gets or sets the text content of the node and its descendants.",
                new String[0], null, "https://developer.mozilla.org/docs/Web/API/Node/textContent");
        putMember("Element", "appendChild", "(method) Node.appendChild<T extends Node>(node: T): T",
                "Adds a node to the end of the list of children of a specified parent node.",
                new String[]{"node: The node to append to the given parent node"}, "The added child node.", "https://developer.mozilla.org/docs/Web/API/Node/appendChild");
        putMember("Element", "removeChild", "(method) Node.removeChild<T extends Node>(child: T): T",
                "Removes a child node from the DOM and returns the removed node.",
                new String[]{"child: A Node that is the child node to be removed from the DOM"}, "The removed child node.", "https://developer.mozilla.org/docs/Web/API/Node/removeChild");
        putMember("Element", "closest", "(method) Element.closest<E extends Element = Element>(selectors: string): E | null",
                "Traverses the element and its parents (heading toward the document root) until it finds a node that matches the specified CSS selector.",
                new String[]{"selectors: A string of valid CSS selector to match the Element and its ancestors against"}, "The closest ancestor Element or itself; null if no match found.", "https://developer.mozilla.org/docs/Web/API/Element/closest");
    }

    private static void initDomTokenList() {
        putMember("DOMTokenList", "add", "(method) DOMTokenList.add(...tokens: string[]): void",
                "Adds the specified tokens to the list, omitting any that are already present.",
                new String[]{"...tokens: A string representing a token (or list of tokens) to add to the DOMTokenList"}, "void", "https://developer.mozilla.org/docs/Web/API/DOMTokenList/add");
        putMember("DOMTokenList", "remove", "(method) DOMTokenList.remove(...tokens: string[]): void",
                "Removes the specified tokens from the list.",
                new String[]{"...tokens: A string representing a token (or list of tokens) to remove from the DOMTokenList"}, "void", "https://developer.mozilla.org/docs/Web/API/DOMTokenList/remove");
        putMember("DOMTokenList", "toggle", "(method) DOMTokenList.toggle(token: string, force?: boolean): boolean",
                "Removes token from list if it's present, or adds it if it's not present.",
                new String[]{"token: A string representing the token you want to toggle", "force: If included, turns the toggle into a one way-only operation. If set to false, then token will only be removed, but not added. If set to true, then token will only be added, but not removed."},
                "true if token is now present in the list, and false otherwise.", "https://developer.mozilla.org/docs/Web/API/DOMTokenList/toggle");
        putMember("DOMTokenList", "contains", "(method) DOMTokenList.contains(token: string): boolean",
                "Returns true if the list contains the given token, otherwise false.",
                new String[]{"token: A string representing the token you want to check for the existence of in the list"}, "A boolean value, which is true if the calling list contains token, otherwise false.", "https://developer.mozilla.org/docs/Web/API/DOMTokenList/contains");
    }

    private static void initEvent() {
        putMember("Event", "preventDefault", "(method) Event.preventDefault(): void",
                "Tells the user agent that if the event does not get explicitly handled, its default action should not be taken as it normally would be.",
                new String[0], "void", "https://developer.mozilla.org/docs/Web/API/Event/preventDefault");
        putMember("Event", "stopPropagation", "(method) Event.stopPropagation(): void",
                "Prevents further propagation of the current event in the capturing and bubbling phases.",
                new String[0], "void", "https://developer.mozilla.org/docs/Web/API/Event/stopPropagation");
        putMember("Event", "target", "(property) Event.target: EventTarget | null",
                "A reference to the object onto which the event was dispatched.",
                new String[0], null, "https://developer.mozilla.org/docs/Web/API/Event/target");
    }

    private static void initDate() {
        putMember("Date", "now", "(method) DateConstructor.now(): number",
                "Returns the number of milliseconds elapsed since midnight, January 1, 1970 Universal Coordinated Time (UTC).",
                new String[0], "A number representing the milliseconds elapsed since the UNIX epoch.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Date/now");
        putMember("Date", "getTime", "(method) Date.getTime(): number",
                "Returns the stored time value in milliseconds since midnight, January 1, 1970 UTC.",
                new String[0], "A number representing the milliseconds for this date since the UNIX epoch.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Date/getTime");
        putMember("Date", "toISOString", "(method) Date.toISOString(): string",
                "Returns a date as a string value in ISO format (YYYY-MM-DDTHH:mm:ss.sssZ).",
                new String[0], "A string representing the given date in the ISO 8601 format.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Date/toISOString");
    }

    private static void initMapSet() {
        putMember("Map", "get", "(method) Map<K, V>.get(key: K): V | undefined",
                "Returns a specified element from a Map object.",
                new String[]{"key: The key of the element to return from the Map object"}, "The element associated with the specified key, or undefined.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Map/get");
        putMember("Map", "set", "(method) Map<K, V>.set(key: K, value: V): this",
                "Adds or updates an element with a specified key and a value to a Map object.",
                new String[]{"key: The key of the element to add to the Map object", "value: The value of the element to add to the Map object"}, "The Map object.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Map/set");
        putMember("Map", "has", "(method) Map<K, V>.has(key: K): boolean",
                "Returns a boolean indicating whether an element with the specified key exists or not.",
                new String[]{"key: The key of the element to test for presence in the Map object"}, "true if an element with the specified key exists in the Map object; otherwise false.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Map/has");
        putMember("Set", "add", "(method) Set<T>.add(value: T): this",
                "Inserts a new element with a specified value in to a Set object, if there isn't an element with the same value already in the Set.",
                new String[]{"value: The value of the element to add to the Set object"}, "The Set object.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Set/add");
        putMember("Set", "has", "(method) Set<T>.has(value: T): boolean",
                "Returns a boolean indicating whether an element with the specified value exists in a Set object or not.",
                new String[]{"value: The value to test for presence in the Set object"}, "true if an element with the specified value exists in the Set object; otherwise false.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Set/has");
    }

    private static void initKeywords() {
        putKw("function", "function name([param[, item]]) { [statements] }",
                "Declares a function with the specified parameters and body statements.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Statements/function");
        putKw("class", "class name [extends otherName] { [classBody] }",
                "Declares a class, creating a blueprint for creating objects with shared prototype and methods.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Statements/class");
        putKw("const", "const name1 = value1 [, name2 = value2 [, ... [, nameN = valueN]]];",
                "Declares block-scoped, read-only named constants. The value of a constant can't be changed through reassignment.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Statements/const");
        putKw("let", "let name1 [= value1] [, name2 [= value2]] [, ..., nameN [= valueN]];",
                "Declares a block-scoped local variable, optionally initializing it to a value.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Statements/let");
        putKw("var", "var varname1 [= value1] [, varname2 [= value2 ... [, varnameN [= valueN]]];",
                "Declares a function-scoped or globally-scoped variable, optionally initializing it to a value.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Statements/var");
        putKw("return", "return [expression];",
                "Ends function execution and specifies a value to be returned to the function caller.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Statements/return");
        putKw("if", "if (condition) statement1 [else statement2]",
                "Executes a statement if a specified condition is truthy. If the condition is falsy, another statement in the optional else clause will be executed.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Statements/if...else");
        putKw("else", "if (condition) statement1 else statement2",
                "Executes the following statement when the corresponding if condition evaluates to falsy.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Statements/if...else");
        putKw("for", "for ([initialization]; [condition]; [afterthought]) statement",
                "Creates a loop that consists of three optional expressions, enclosed in parentheses and separated by semicolons, followed by a statement.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Statements/for");
        putKw("while", "while (condition) statement",
                "Creates a loop that executes a specified statement as long as the test condition evaluates to true.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Statements/while");
        putKw("async", "async function name([param[, item]]) { [statements] }",
                "Defines an asynchronous function which returns an AsyncFunction object. Async functions operate asynchronously via the event loop, using an implicit Promise.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Statements/async_function");
        putKw("await", "let response = await promiseOrAsyncFunction();",
                "Causes async function execution to pause until a Promise is settled (that is, fulfilled or rejected), and to resume execution of the async function after fulfillment.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Operators/await");
        putKw("try", "try { tryStatements } catch (exceptionVar) { catchStatements } [finally { finallyStatements }]",
                "Marks a block of statements to try, and specifies a response, should an exception be thrown.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Statements/try...catch");
        putKw("catch", "catch (exceptionVar) { catchStatements }",
                "Contains statements that specify what to do if an exception is thrown in the try block.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Statements/try...catch");
        putKw("finally", "finally { finallyStatements }",
                "Statements that execute after the try block and catch clause(s) execute. It executes regardless of whether an exception was thrown or caught.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Statements/try...catch");
        putKw("throw", "throw expression;",
                "Throws a user-defined exception. Execution of the current function will stop, and control will be passed to the first catch block in the call stack.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Statements/throw");
        putKw("new", "new constructor[([arguments])]",
                "Creates an instance of a user-defined object type or of one of the built-in object types that has a constructor function.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Operators/new");
        putKw("this", "this",
                "A property of an execution context (global, function or eval) that, in non–strict mode, is always a reference to an object and in strict mode can be any value.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Operators/this");
        putKw("import", "import defaultExport from \"module-name\";",
                "Used to import read-only live bindings which are exported by another module.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Statements/import");
        putKw("export", "export { name1, name2, ..., nameN };",
                "Used when creating JavaScript modules to export functions, objects, or primitive values from the module so they can be used by other programs with the import statement.", "https://developer.mozilla.org/docs/Web/JavaScript/Reference/Statements/export");

        // TypeScript Keywords
        putKw("interface", "interface InterfaceName { property: Type; }",
                "TypeScript language construct used to define contracts within your code and contracts with code outside of your project.", "https://www.typescriptlang.org/docs/handbook/interfaces.html");
        putKw("type", "type TypeAliasName = TypeDefinition;",
                "TypeScript type alias creates a new name for a type. Type aliases are sometimes similar to interfaces, but can name primitives, unions, tuples, and any other types.", "https://www.typescriptlang.org/docs/handbook/advanced-types.html#type-aliases");
        putKw("enum", "enum Direction { Up = 1, Down, Left, Right }",
                "Allows a developer to define a set of named constants. Using enums can make it easier to document intent, or create a set of distinct cases.", "https://www.typescriptlang.org/docs/handbook/enums.html");
    }

    private static void putMember(String container, String name, String signature, String desc, String[] params, String returns, String mdn) {
        DOCS_BY_MEMBER.put(container + "." + name, new JsDocItem(signature, desc, params, returns, mdn));
    }

    private static void putMember(String container, String name, JsDocItem item) {
        if (item != null) {
            DOCS_BY_MEMBER.put(container + "." + name, item);
        }
    }

    private static void putGlobal(String name, String signature, String desc, String[] params, String returns, String mdn) {
        GLOBALS.put(name, new JsDocItem(signature, desc, params, returns, mdn));
    }

    private static void putKw(String name, String syntax, String desc, String mdn) {
        KEYWORDS.put(name, new JsDocItem("keyword " + name + "\n" + syntax, desc, new String[0], null, mdn));
    }
}
