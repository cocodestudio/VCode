package com.cocode.vcode.ide.core.keybinding;

import android.view.KeyEvent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable value object representing a physical key combination (modifiers + key code).
 * Supports both PC (Ctrl) and Mac (Cmd/Meta) seamlessly.
 */
public final class KeyStroke {

    public final int keyCode;
    public final boolean ctrl;
    public final boolean alt;
    public final boolean shift;
    public final boolean meta;

    public KeyStroke(int keyCode, boolean ctrl, boolean alt, boolean shift, boolean meta) {
        this.keyCode = keyCode;
        // Normalize: if either ctrl or meta is pressed, mark ctrl as true for uniform comparison
        this.ctrl = ctrl || meta;
        this.alt = alt;
        this.shift = shift;
        this.meta = false; // normalized into ctrl
    }

    /**
     * Checks if this KeyStroke matches an incoming hardware KeyEvent.
     */
    public boolean matches(@Nullable KeyEvent event) {
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }
        if (event.getKeyCode() != this.keyCode) {
            return false;
        }

        boolean eventCtrlOrMeta = event.isCtrlPressed() || event.isMetaPressed();
        if (this.ctrl != eventCtrlOrMeta) {
            return false;
        }
        if (this.alt != event.isAltPressed()) {
            return false;
        }
        if (this.shift != event.isShiftPressed()) {
            return false;
        }

        return true;
    }

    /**
     * Constructs a KeyStroke directly from an active hardware KeyEvent.
     * Returns null if the event represents only a modifier key (Shift, Ctrl, Alt, Meta).
     */
    @Nullable
    public static KeyStroke fromKeyEvent(@Nullable KeyEvent event) {
        if (event == null) return null;
        int code = event.getKeyCode();
        if (isModifierKeyCode(code) || code == KeyEvent.KEYCODE_BACK) return null;

        boolean ctrl = event.isCtrlPressed() || event.isMetaPressed();
        boolean alt = event.isAltPressed();
        boolean shift = event.isShiftPressed();

        return new KeyStroke(code, ctrl, alt, shift, false);
    }

    /**
     * Parses a key specification string such as "Ctrl+Shift+P", "Alt+Up", "F5", "Ctrl+,".
     */
    @Nullable
    public static KeyStroke fromString(@Nullable String spec) {
        if (spec == null || spec.trim().isEmpty()) {
            return null;
        }
        spec = spec.trim();

        boolean ctrl = false;
        boolean alt = false;
        boolean shift = false;
        boolean meta = false;

        String keyPart;
        // Handle cases like "Ctrl++" or "Ctrl+="
        if (spec.endsWith("++")) {
            keyPart = "+";
            spec = spec.substring(0, spec.length() - 2);
        } else {
            int lastPlus = spec.lastIndexOf('+');
            if (lastPlus >= 0 && lastPlus < spec.length() - 1) {
                keyPart = spec.substring(lastPlus + 1).trim();
                spec = spec.substring(0, lastPlus);
            } else {
                keyPart = spec;
                spec = "";
            }
        }

        if (!spec.isEmpty()) {
            String[] modifiers = spec.split("\\+");
            for (String mod : modifiers) {
                String m = mod.trim().toLowerCase(Locale.US);
                switch (m) {
                    case "ctrl":
                    case "control":
                        ctrl = true;
                        break;
                    case "alt":
                    case "option":
                        alt = true;
                        break;
                    case "shift":
                        shift = true;
                        break;
                    case "meta":
                    case "cmd":
                    case "command":
                        meta = true;
                        break;
                }
            }
        }

        int keyCode = parseKeyCode(keyPart);
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            return null;
        }

        return new KeyStroke(keyCode, ctrl, alt, shift, meta);
    }

    /**
     * Formats this KeyStroke into a compact specification string (e.g. "Ctrl+Shift+P").
     */
    @NonNull
    public String toSpecString() {
        StringBuilder sb = new StringBuilder();
        if (ctrl) sb.append("Ctrl+");
        if (alt) sb.append("Alt+");
        if (shift) sb.append("Shift+");
        sb.append(keyCodeToString(keyCode));
        return sb.toString();
    }

    /**
     * Formats this KeyStroke for user-facing UI badges (e.g. "Ctrl + Shift + P").
     */
    @NonNull
    public String toDisplayString() {
        StringBuilder sb = new StringBuilder();
        if (ctrl) sb.append("Ctrl + ");
        if (alt) sb.append("Alt + ");
        if (shift) sb.append("Shift + ");
        sb.append(keyCodeToString(keyCode));
        return sb.toString();
    }

    /**
     * Returns individual token strings for badge rendering (e.g. ["Ctrl", "Shift", "P"]).
     */
    @NonNull
    public java.util.List<String> toDisplayParts() {
        java.util.List<String> parts = new java.util.ArrayList<>(4);
        if (ctrl) parts.add("Ctrl");
        if (alt) parts.add("Alt");
        if (shift) parts.add("Shift");
        parts.add(keyCodeToString(keyCode));
        return parts;
    }

    public static boolean isModifierKeyCode(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT
                || keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT
                || keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT
                || keyCode == KeyEvent.KEYCODE_META_LEFT || keyCode == KeyEvent.KEYCODE_META_RIGHT;
    }

    public static int parseKeyCode(String key) {
        if (key == null || key.isEmpty()) return KeyEvent.KEYCODE_UNKNOWN;
        String upper = key.trim().toUpperCase(Locale.US);

        if (upper.length() == 1) {
            char ch = upper.charAt(0);
            if (ch >= 'A' && ch <= 'Z') {
                return KeyEvent.KEYCODE_A + (ch - 'A');
            }
            if (ch >= '0' && ch <= '9') {
                return KeyEvent.KEYCODE_0 + (ch - '0');
            }
            switch (ch) {
                case ',': return KeyEvent.KEYCODE_COMMA;
                case '.': return KeyEvent.KEYCODE_PERIOD;
                case '/': return KeyEvent.KEYCODE_SLASH;
                case '\\': return KeyEvent.KEYCODE_BACKSLASH;
                case '-':
                case '_': return KeyEvent.KEYCODE_MINUS;
                case '=':
                case '+': return KeyEvent.KEYCODE_EQUALS;
                case ';': return KeyEvent.KEYCODE_SEMICOLON;
                case '\'': return KeyEvent.KEYCODE_APOSTROPHE;
                case '`': return KeyEvent.KEYCODE_GRAVE;
                case '[': return KeyEvent.KEYCODE_LEFT_BRACKET;
                case ']': return KeyEvent.KEYCODE_RIGHT_BRACKET;
            }
        }

        switch (upper) {
            case "ENTER":
            case "RETURN":
                return KeyEvent.KEYCODE_ENTER;
            case "TAB":
                return KeyEvent.KEYCODE_TAB;
            case "SPACE":
            case "SPACEBAR":
                return KeyEvent.KEYCODE_SPACE;
            case "BACKSPACE":
                return KeyEvent.KEYCODE_DEL;
            case "DELETE":
            case "DEL":
                return KeyEvent.KEYCODE_FORWARD_DEL;
            case "ESC":
            case "ESCAPE":
                return KeyEvent.KEYCODE_ESCAPE;
            case "HOME":
                return KeyEvent.KEYCODE_MOVE_HOME;
            case "END":
                return KeyEvent.KEYCODE_MOVE_END;
            case "PAGEUP":
            case "PAGE_UP":
            case "PAGE UP":
                return KeyEvent.KEYCODE_PAGE_UP;
            case "PAGEDOWN":
            case "PAGE_DOWN":
            case "PAGE DOWN":
                return KeyEvent.KEYCODE_PAGE_DOWN;
            case "UP":
            case "ARROWUP":
                return KeyEvent.KEYCODE_DPAD_UP;
            case "DOWN":
            case "ARROWDOWN":
                return KeyEvent.KEYCODE_DPAD_DOWN;
            case "LEFT":
            case "ARROWLEFT":
                return KeyEvent.KEYCODE_DPAD_LEFT;
            case "RIGHT":
            case "ARROWRIGHT":
                return KeyEvent.KEYCODE_DPAD_RIGHT;
            case "F1": return KeyEvent.KEYCODE_F1;
            case "F2": return KeyEvent.KEYCODE_F2;
            case "F3": return KeyEvent.KEYCODE_F3;
            case "F4": return KeyEvent.KEYCODE_F4;
            case "F5": return KeyEvent.KEYCODE_F5;
            case "F6": return KeyEvent.KEYCODE_F6;
            case "F7": return KeyEvent.KEYCODE_F7;
            case "F8": return KeyEvent.KEYCODE_F8;
            case "F9": return KeyEvent.KEYCODE_F9;
            case "F10": return KeyEvent.KEYCODE_F10;
            case "F11": return KeyEvent.KEYCODE_F11;
            case "F12": return KeyEvent.KEYCODE_F12;
            default:
                return KeyEvent.KEYCODE_UNKNOWN;
        }
    }

    @NonNull
    public static String keyCodeToString(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
            return String.valueOf((char) ('A' + (keyCode - KeyEvent.KEYCODE_A)));
        }
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            return String.valueOf((char) ('0' + (keyCode - KeyEvent.KEYCODE_0)));
        }
        if (keyCode >= KeyEvent.KEYCODE_F1 && keyCode <= KeyEvent.KEYCODE_F12) {
            return "F" + (1 + (keyCode - KeyEvent.KEYCODE_F1));
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_COMMA: return ",";
            case KeyEvent.KEYCODE_PERIOD: return ".";
            case KeyEvent.KEYCODE_SLASH: return "/";
            case KeyEvent.KEYCODE_BACKSLASH: return "\\";
            case KeyEvent.KEYCODE_MINUS: return "-";
            case KeyEvent.KEYCODE_EQUALS: return "=";
            case KeyEvent.KEYCODE_SEMICOLON: return ";";
            case KeyEvent.KEYCODE_APOSTROPHE: return "'";
            case KeyEvent.KEYCODE_GRAVE: return "`";
            case KeyEvent.KEYCODE_LEFT_BRACKET: return "[";
            case KeyEvent.KEYCODE_RIGHT_BRACKET: return "]";
            case KeyEvent.KEYCODE_ENTER: return "Enter";
            case KeyEvent.KEYCODE_TAB: return "Tab";
            case KeyEvent.KEYCODE_SPACE: return "Space";
            case KeyEvent.KEYCODE_DEL: return "Backspace";
            case KeyEvent.KEYCODE_FORWARD_DEL: return "Delete";
            case KeyEvent.KEYCODE_ESCAPE: return "Esc";
            case KeyEvent.KEYCODE_MOVE_HOME: return "Home";
            case KeyEvent.KEYCODE_MOVE_END: return "End";
            case KeyEvent.KEYCODE_PAGE_UP: return "PageUp";
            case KeyEvent.KEYCODE_PAGE_DOWN: return "PageDown";
            case KeyEvent.KEYCODE_DPAD_UP: return "Up";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "Down";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "Left";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "Right";
            default:
                return "Key_" + keyCode;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KeyStroke that = (KeyStroke) o;
        return keyCode == that.keyCode &&
                ctrl == that.ctrl &&
                alt == that.alt &&
                shift == that.shift;
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyCode, ctrl, alt, shift);
    }

    @NonNull
    @Override
    public String toString() {
        return toSpecString();
    }
}
