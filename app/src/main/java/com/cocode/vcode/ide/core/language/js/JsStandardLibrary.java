package com.cocode.vcode.ide.core.language.js;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class JsStandardLibrary {

    public static final String[][] DOT_METHODS = {
            {"console", "log,warn,error,info,table,time,timeEnd,timeLog,assert,clear,count,countReset,group,groupEnd,groupCollapsed,dir,dirxml,trace,debug,profile,profileEnd"},
            {"Math", "floor,ceil,round,abs,max,min,random,sqrt,pow,PI,E,LN2,LN10,LOG2E,SQRT2,log,log2,log10,sign,trunc,sin,cos,tan,asin,acos,atan,atan2,sinh,cosh,tanh,cbrt,hypot,clz32,imul,fround"},
            {"JSON", "parse,stringify"},
            {"Array", "from,isArray,of"},
            {"Object", "keys,values,entries,assign,create,freeze,seal,isFrozen,isSealed,defineProperty,defineProperties,getOwnPropertyNames,getOwnPropertyDescriptors,getPrototypeOf,setPrototypeOf,hasOwn,fromEntries,is,groupBy"},
            {"Promise", "all,race,resolve,reject,allSettled,any,withResolvers"},
            {"Number", "isFinite,isInteger,isNaN,isSafeInteger,parseInt,parseFloat,MAX_VALUE,MIN_VALUE,MAX_SAFE_INTEGER,MIN_SAFE_INTEGER,EPSILON,POSITIVE_INFINITY,NEGATIVE_INFINITY,NaN"},
            {"String", "fromCharCode,fromCodePoint,raw"},
            {"Date", "now,parse,UTC"},
            {"Map", "groupBy"},
            {"Set", ""},
            {"WeakMap", ""},
            {"WeakSet", ""},
            {"RegExp", ""},
            {"Symbol", "iterator,asyncIterator,hasInstance,toPrimitive,toStringTag,for,keyFor"},
            {"Reflect", "apply,construct,defineProperty,deleteProperty,get,getOwnPropertyDescriptor,getPrototypeOf,has,isExtensible,ownKeys,preventExtensions,set,setPrototypeOf"},
            {"Proxy", "revocable"},
            {"document", "getElementById,querySelector,querySelectorAll,createElement,createTextNode,createDocumentFragment,createComment,addEventListener,removeEventListener,body,head,title,cookie,write,writeln,readyState,documentElement,activeElement,forms,images,links,scripts,styleSheets,hidden,visibilityState,fullscreenElement,pointerLockElement,designMode,execCommand,getSelection,hasFocus,open,close,importNode,adoptNode,createEvent,createRange,createTreeWalker,elementsFromPoint,elementFromPoint,exitFullscreen,exitPointerLock,scrollingElement"},
            {"window", "location,history,navigator,alert,confirm,prompt,open,close,scrollTo,scrollBy,setTimeout,setInterval,clearTimeout,clearInterval,requestAnimationFrame,cancelAnimationFrame,requestIdleCallback,cancelIdleCallback,fetch,addEventListener,removeEventListener,getComputedStyle,matchMedia,innerWidth,innerHeight,outerWidth,outerHeight,devicePixelRatio,performance,screen,crypto,indexedDB,caches,customElements,visualViewport,structuredClone,atob,btoa,queueMicrotask,reportError,postMessage,focus,blur,print,stop,getSelection"},
            {"navigator", "userAgent,language,languages,onLine,geolocation,clipboard,mediaDevices,permissions,serviceWorker,hardwareConcurrency,cookieEnabled,platform,maxTouchPoints,connection,storage,locks,credentials,sendBeacon,vibrate,share,canShare,getBattery,getGamepads,requestMIDIAccess,wakeLock"},
            {"location", "href,pathname,search,hash,hostname,port,protocol,host,origin,reload,replace,assign,toString"},
            {"history", "back,forward,go,pushState,replaceState,length,state,scrollRestoration"},
            {"localStorage", "getItem,setItem,removeItem,clear,length,key"},
            {"sessionStorage", "getItem,setItem,removeItem,clear,length,key"},
            {"performance", "now,mark,measure,clearMarks,clearMeasures,getEntries,getEntriesByName,getEntriesByType,timeOrigin,navigation,timing"},
            {"crypto", "subtle,getRandomValues,randomUUID"},
            {"screen", "width,height,availWidth,availHeight,colorDepth,pixelDepth,orientation"},
            {"URL", "createObjectURL,revokeObjectURL,canParse"},
            {"URLSearchParams", "append,delete,get,getAll,has,set,sort,toString,entries,keys,values,forEach,size"},
            {"FormData", "append,delete,get,getAll,has,set,entries,keys,values,forEach"},
            {"Headers", "append,delete,get,has,set,entries,keys,values,forEach"},
            {"Request", "clone,arrayBuffer,blob,formData,json,text,url,method,headers,body,mode,credentials,cache,redirect,referrer,integrity,signal"},
            {"Response", "clone,arrayBuffer,blob,formData,json,text,ok,status,statusText,headers,url,type,redirected,error,redirect"},
            {"AbortController", "abort,signal"},
            {"IntersectionObserver", "observe,unobserve,disconnect,takeRecords,root,rootMargin,thresholds"},
            {"ResizeObserver", "observe,unobserve,disconnect"},
            {"MutationObserver", "observe,disconnect,takeRecords"},
            {"EventTarget", "addEventListener,removeEventListener,dispatchEvent"},
            {"CustomEvent", "detail"},
            {"WebSocket", "send,close,onopen,onclose,onmessage,onerror,readyState,url,protocol,binaryType,bufferedAmount,CONNECTING,OPEN,CLOSING,CLOSED"},
            {"Worker", "postMessage,terminate,onmessage,onerror"},
            {"BroadcastChannel", "postMessage,close,onmessage,name"},
            {"Intl", "DateTimeFormat,NumberFormat,Collator,PluralRules,RelativeTimeFormat,ListFormat,Segmenter,DisplayNames,Locale"},
            {"TextEncoder", "encode,encodeInto,encoding"},
            {"TextDecoder", "decode,encoding,fatal,ignoreBOM"},
            {"DOMParser", "parseFromString"},
            {"XMLSerializer", "serializeToString"},
    };

    public static final String[] EVENT_NAMES = {
            "click", "dblclick", "mousedown", "mouseup", "mousemove", "mouseover", "mouseout",
            "mouseenter", "mouseleave", "contextmenu", "wheel",
            "keydown", "keyup", "keypress",
            "focus", "blur", "focusin", "focusout",
            "input", "change", "submit", "reset", "invalid",
            "touchstart", "touchmove", "touchend", "touchcancel",
            "pointerdown", "pointerup", "pointermove", "pointerenter", "pointerleave",
            "pointerover", "pointerout", "pointercancel", "gotpointercapture", "lostpointercapture",
            "scroll", "scrollend", "resize",
            "load", "error", "abort", "unload", "beforeunload",
            "DOMContentLoaded", "readystatechange",
            "animationstart", "animationend", "animationiteration", "animationcancel",
            "transitionstart", "transitionend", "transitionrun", "transitioncancel",
            "drag", "dragstart", "dragend", "dragover", "dragenter", "dragleave", "drop",
            "copy", "cut", "paste",
            "play", "pause", "ended", "timeupdate", "volumechange", "seeking", "seeked",
            "canplay", "canplaythrough", "loadeddata", "loadedmetadata", "progress", "waiting", "stalled",
            "fullscreenchange", "fullscreenerror",
            "visibilitychange", "online", "offline", "storage",
            "hashchange", "popstate", "pagehide", "pageshow",
            "message", "messageerror",
            "open", "close",
            "toggle", "beforetoggle",
            "select", "selectstart", "selectionchange",
            "slotchange",
            "formdata",
    };

    public static final Map<String, String[]> PROTOTYPE_METHODS = new HashMap<>();
    public static final Map<String, String> CHAIN_RETURN_TYPES = new HashMap<>();
    public static final Set<String> PROMISE_FUNCTIONS = new HashSet<>();

    static {
        PROTOTYPE_METHODS.put("array", new String[]{
                "push", "pop", "shift", "unshift", "splice", "slice", "concat", "join", "reverse", "sort",
                "indexOf", "lastIndexOf", "includes", "find", "findIndex", "findLast", "findLastIndex",
                "filter", "map", "reduce", "reduceRight", "forEach", "some", "every", "flat", "flatMap",
                "fill", "copyWithin", "entries", "keys", "values", "at", "toReversed", "toSorted", "toSpliced",
                "with", "toString", "toLocaleString", "length", "group", "groupToMap"
        });
        PROTOTYPE_METHODS.put("string", new String[]{
                "charAt", "charCodeAt", "codePointAt", "at", "concat", "includes", "startsWith", "endsWith",
                "indexOf", "lastIndexOf", "search", "match", "matchAll", "replace", "replaceAll", "split",
                "slice", "substring", "padStart", "padEnd", "trimStart", "trimEnd", "trim",
                "toUpperCase", "toLowerCase", "toLocaleLowerCase", "toLocaleUpperCase", "normalize",
                "repeat", "valueOf", "toString", "length", "isWellFormed", "toWellFormed", "localeCompare"
        });
        PROTOTYPE_METHODS.put("number", new String[]{"toFixed", "toPrecision", "toExponential", "toString", "valueOf", "toLocaleString"});
        PROTOTYPE_METHODS.put("promise", new String[]{"then", "catch", "finally"});
        PROTOTYPE_METHODS.put("map", new String[]{"set", "get", "has", "delete", "clear", "forEach", "keys", "values", "entries", "size"});
        PROTOTYPE_METHODS.put("set", new String[]{"add", "has", "delete", "clear", "forEach", "values", "keys", "entries", "size", "union", "intersection", "difference", "symmetricDifference", "isSubsetOf", "isSupersetOf"});
        PROTOTYPE_METHODS.put("date", new String[]{"getTime", "getFullYear", "getMonth", "getDate", "getDay", "getHours", "getMinutes", "getSeconds", "getMilliseconds", "setTime", "setFullYear", "setMonth", "setDate", "setHours", "setMinutes", "setSeconds", "toISOString", "toLocaleDateString", "toLocaleTimeString", "toLocaleString", "toJSON", "toString", "valueOf", "getTimezoneOffset"});
        PROTOTYPE_METHODS.put("regexp", new String[]{"test", "exec", "toString", "source", "flags", "global", "ignoreCase", "multiline", "sticky", "unicode", "lastIndex"});
        PROTOTYPE_METHODS.put("element", new String[]{
                "addEventListener", "removeEventListener", "setAttribute", "getAttribute", "removeAttribute",
                "hasAttribute", "toggleAttribute", "closest", "matches", "querySelector", "querySelectorAll",
                "classList", "style", "dataset", "innerHTML", "textContent", "innerText", "outerHTML", "outerText",
                "appendChild", "removeChild", "insertBefore", "replaceChild", "replaceChildren", "cloneNode",
                "contains", "remove", "before", "after", "prepend", "append", "insertAdjacentHTML",
                "insertAdjacentElement", "insertAdjacentText", "getBoundingClientRect", "getClientRects",
                "scrollIntoView", "scroll", "scrollTo", "scrollBy", "focus", "blur", "click",
                "animate", "getAnimations", "requestFullscreen", "attachShadow",
                "id", "className", "tagName", "localName", "parentElement", "parentNode",
                "children", "childNodes", "childElementCount", "firstChild", "lastChild",
                "firstElementChild", "lastElementChild", "nextSibling", "previousSibling",
                "nextElementSibling", "previousElementSibling",
                "offsetWidth", "offsetHeight", "offsetTop", "offsetLeft", "offsetParent",
                "clientWidth", "clientHeight", "clientTop", "clientLeft",
                "scrollWidth", "scrollHeight", "scrollTop", "scrollLeft",
                "hidden", "isConnected", "slot", "assignedSlot",
                "value", "type", "checked", "disabled", "readOnly", "name", "form",
                "min", "max", "step", "placeholder", "required", "maxLength", "minLength",
                "play", "pause", "load", "currentTime", "duration", "paused", "muted", "volume", "src", "playbackRate"
        });
        PROTOTYPE_METHODS.put("nodelist", new String[]{"forEach", "entries", "keys", "values", "item", "length"});
        PROTOTYPE_METHODS.put("response", new String[]{"json", "text", "blob", "arrayBuffer", "formData", "clone", "ok", "status", "statusText", "headers", "url", "type", "redirected"});
        PROTOTYPE_METHODS.put("event", new String[]{"preventDefault", "stopPropagation", "stopImmediatePropagation", "target", "currentTarget", "type", "bubbles", "cancelable", "composed", "timeStamp", "isTrusted", "defaultPrevented", "eventPhase"});
        PROTOTYPE_METHODS.put("classlist", new String[]{"add", "remove", "toggle", "contains", "replace", "item", "length", "value", "entries", "keys", "values", "forEach", "supports"});
        PROTOTYPE_METHODS.put("style", new String[]{"getPropertyValue", "setProperty", "removeProperty", "cssText", "length", "item"});
        PROTOTYPE_METHODS.put("canvascontext", new String[]{"arc", "arcTo", "beginPath", "bezierCurveTo", "clearRect", "clip", "closePath", "createImageData", "createLinearGradient", "createPattern", "createRadialGradient", "drawFocusIfNeeded", "drawImage", "ellipse", "fill", "fillRect", "fillText", "getImageData", "getLineDash", "isPointInPath", "isPointInStroke", "lineTo", "measureText", "moveTo", "putImageData", "quadraticCurveTo", "rect", "restore", "rotate", "save", "scale", "setLineDash", "setTransform", "stroke", "strokeRect", "strokeText", "transform", "translate", "fillStyle", "font", "globalAlpha", "globalCompositeOperation", "imageSmoothingEnabled", "lineCap", "lineDashOffset", "lineJoin", "lineWidth", "miterLimit", "shadowBlur", "shadowColor", "shadowOffsetX", "shadowOffsetY", "strokeStyle", "textAlign", "textBaseline"});
        PROTOTYPE_METHODS.put("blob", new String[]{"size", "type", "arrayBuffer", "slice", "stream", "text"});
        PROTOTYPE_METHODS.put("file", new String[]{"name", "lastModified", "size", "type", "arrayBuffer", "slice", "stream", "text"});
        PROTOTYPE_METHODS.put("filereader", new String[]{"readAsArrayBuffer", "readAsBinaryString", "readAsDataURL", "readAsText", "abort", "error", "readyState", "result", "onload", "onloadstart", "onloadend", "onprogress", "onabort", "onerror"});

        for (String m : new String[]{"filter", "map", "slice", "concat", "flat", "flatMap", "sort", "reverse", "toReversed", "toSorted", "splice", "copyWithin", "fill", "from", "of", "keys", "values", "entries"}) {
            CHAIN_RETURN_TYPES.put(m, "array");
        }
        for (String m : new String[]{"trim", "trimStart", "trimEnd", "toLowerCase", "toUpperCase", "replace", "replaceAll", "slice", "substring", "padStart", "padEnd", "repeat", "normalize", "concat", "charAt", "at", "toLocaleLowerCase", "toLocaleUpperCase"}) {
            CHAIN_RETURN_TYPES.put(m, "string");
        }
        for (String m : new String[]{"then", "catch", "finally", "all", "race", "allSettled", "any", "resolve", "reject"}) {
            CHAIN_RETURN_TYPES.put(m, "promise");
        }
        for (String m : new String[]{"querySelector", "getElementById", "createElement", "closest", "parentElement", "firstElementChild", "lastElementChild", "nextElementSibling", "previousElementSibling", "cloneNode"}) {
            CHAIN_RETURN_TYPES.put(m, "element");
        }
        for (String m : new String[]{"querySelectorAll", "getElementsByClassName", "getElementsByTagName", "childNodes", "children"}) {
            CHAIN_RETURN_TYPES.put(m, "nodelist");
        }
        CHAIN_RETURN_TYPES.put("classList", "classlist");
        CHAIN_RETURN_TYPES.put("style", "style");
        CHAIN_RETURN_TYPES.put("json", "promise");
        CHAIN_RETURN_TYPES.put("text", "promise");
        CHAIN_RETURN_TYPES.put("blob", "promise");
        CHAIN_RETURN_TYPES.put("arrayBuffer", "promise");
        CHAIN_RETURN_TYPES.put("formData", "promise");

        Collections.addAll(PROMISE_FUNCTIONS, "fetch", "axios", "axios.get", "axios.post", "axios.put", "axios.delete");
    }
}
