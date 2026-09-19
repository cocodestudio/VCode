package com.cocode.vcode.ide.core.keybinding;

import android.content.Context;
import android.view.KeyEvent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.cocode.vcode.ide.R;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Manages active keybindings, hardware KeyEvent resolution, JSON serialization,
 * and conflict detection across VCode.
 */
public class KeybindingManager {

    private static final String KEYBINDINGS_FILE_NAME = "keybindings.json";

    private static volatile KeybindingManager sInstance;

    private final Context appContext;
    private final File keybindingsFile;
    private final Map<KeyCommand, KeyStroke> activeBindings = Collections.synchronizedMap(new EnumMap<>(KeyCommand.class));
    private final List<OnKeybindingsChangedListener> listeners = new CopyOnWriteArrayList<>();

    public interface OnKeybindingsChangedListener {
        void onKeybindingsChanged();
    }

    private KeybindingManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.keybindingsFile = new File(appContext.getFilesDir(), KEYBINDINGS_FILE_NAME);
        loadDefaults();
        loadFromFile();
    }

    public static KeybindingManager getInstance(@NonNull Context context) {
        if (sInstance == null) {
            synchronized (KeybindingManager.class) {
                if (sInstance == null) {
                    sInstance = new KeybindingManager(context);
                }
            }
        }
        return sInstance;
    }

    /**
     * Initializes all commands with their hardcoded default KeyStrokes.
     */
    private void loadDefaults() {
        for (KeyCommand cmd : KeyCommand.values()) {
            activeBindings.put(cmd, KeyStroke.fromString(cmd.getDefaultKeySpec()));
        }
    }

    /**
     * Loads custom overrides from keybindings.json if present.
     */
    public synchronized void loadFromFile() {
        if (!keybindingsFile.exists()) {
            return;
        }

        try (FileInputStream fis = new FileInputStream(keybindingsFile);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }

            parseJsonAndApply(sb.toString());
        } catch (Exception ignored) {
            // If the custom file is corrupt, retain defaults
        }
    }

    /**
     * Encapsulates the result of importing keybindings from JSON or file,
     * including whether the operation succeeded and how many duplicate shortcuts
     * were unassigned to resolve conflicts.
     */
    public static class ImportResult {
        private final boolean success;
        private final int conflictCount;

        public ImportResult(boolean success, int conflictCount) {
            this.success = success;
            this.conflictCount = conflictCount;
        }

        public boolean isSuccess() {
            return success;
        }

        public int getConflictCount() {
            return conflictCount;
        }

        public static ImportResult success(int conflictCount) {
            return new ImportResult(true, conflictCount);
        }

        public static ImportResult failure() {
            return new ImportResult(false, 0);
        }
    }

    /**
     * Parses a JSON string conforming to the keybindings array schema and applies bindings.
     */
    public synchronized boolean parseJsonAndApply(@Nullable String jsonStr) {
        return parseJsonAndApplyWithResult(jsonStr).isSuccess();
    }

    /**
     * Parses a JSON string conforming to the keybindings array schema, applies bindings,
     * and automatically resolves any duplicate shortcuts by unassigning the conflicting actions.
     *
     * @param jsonStr The JSON string to parse and apply.
     * @return ImportResult containing success status and count of duplicate shortcuts unassigned.
     */
    public synchronized ImportResult parseJsonAndApplyWithResult(@Nullable String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return ImportResult.failure();
        }

        try {
            JSONArray array = new JSONArray(jsonStr);
            Map<KeyCommand, KeyStroke> newBindings = new EnumMap<>(KeyCommand.class);
            synchronized (activeBindings) {
                newBindings.putAll(activeBindings);
            }
            int conflictCount = 0;

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) continue;

                String cmdId = obj.optString("command", null);
                if (cmdId == null) continue;

                KeyCommand cmd = KeyCommand.fromCommandId(cmdId);
                if (cmd == null) continue;

                String keyStr = obj.optString("key", "").trim();
                if (keyStr.isEmpty() || keyStr.equalsIgnoreCase("none") || keyStr.equalsIgnoreCase("unassigned")) {
                    newBindings.put(cmd, null);
                } else {
                    KeyStroke stroke = KeyStroke.fromString(keyStr);
                    if (stroke != null) {
                        // If another command already has this exact shortcut, unassign it to resolve conflict
                        for (Map.Entry<KeyCommand, KeyStroke> entry : newBindings.entrySet()) {
                            if (entry.getKey() != cmd && stroke.equals(entry.getValue())) {
                                entry.setValue(null);
                                conflictCount++;
                            }
                        }
                        newBindings.put(cmd, stroke);
                    }
                }
            }

            synchronized (activeBindings) {
                activeBindings.clear();
                activeBindings.putAll(newBindings);
            }
            return ImportResult.success(conflictCount);
        } catch (Exception e) {
            return ImportResult.failure();
        }
    }

    /**
     * Resolves an incoming hardware KeyEvent into a matching KeyCommand using GLOBAL scope.
     */
    @Nullable
    public KeyCommand findCommand(@Nullable KeyEvent event) {
        return findCommand(event, KeyCommand.Scope.GLOBAL);
    }

    /**
     * Resolves an incoming hardware KeyEvent into a matching KeyCommand.
     * Takes scope priority into account (editor commands take precedence when editor is active).
     */
    @Nullable
    public KeyCommand findCommand(@Nullable KeyEvent event, @NonNull KeyCommand.Scope activeScope) {
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) {
            return null;
        }

        KeyCommand fallbackGlobal = null;

        synchronized (activeBindings) {
            for (Map.Entry<KeyCommand, KeyStroke> entry : activeBindings.entrySet()) {
                KeyStroke stroke = entry.getValue();
                if (stroke != null && stroke.matches(event)) {
                    KeyCommand cmd = entry.getKey();
                    if (cmd.getScope() == activeScope) {
                        return cmd;
                    }
                    if (activeScope == KeyCommand.Scope.EDITOR && cmd.getScope() == KeyCommand.Scope.GLOBAL) {
                        if (fallbackGlobal == null) {
                            fallbackGlobal = cmd;
                        }
                    }
                }
            }
        }

        if (fallbackGlobal != null) {
            return fallbackGlobal;
        }

        // Standard alias: Ctrl+Shift+Z / Cmd+Shift+Z maps to REDO
        if (event.getKeyCode() == KeyEvent.KEYCODE_Z && (event.isCtrlPressed() || event.isMetaPressed()) && event.isShiftPressed()) {
            return KeyCommand.REDO;
        }

        return null;
    }

    /**
     * Returns the current KeyStroke assigned to the command, or null if unassigned.
     */
    @Nullable
    public KeyStroke getKeyStroke(@NonNull KeyCommand command) {
        return activeBindings.get(command);
    }

    /**
     * Returns a human-readable display string for the command's shortcut.
     */
    @NonNull
    public String getDisplayString(@NonNull KeyCommand command) {
        KeyStroke stroke = activeBindings.get(command);
        if (stroke == null) {
            return appContext.getString(R.string.vcode_key_unassigned);
        }
        return stroke.toDisplayString();
    }

    /**
     * Returns true if the command has been modified from its default key combination.
     */
    public boolean isCustomized(@NonNull KeyCommand command) {
        KeyStroke current = activeBindings.get(command);
        KeyStroke def = KeyStroke.fromString(command.getDefaultKeySpec());
        if (current == null && def == null) return false;
        if (current == null || def == null) return true;
        return !current.equals(def);
    }

    /**
     * Updates the keybinding for a given command, persists to disk, and notifies listeners.
     * If another command already has this keystroke, it is unassigned to resolve conflicts.
     */
    public synchronized void setKeybinding(@NonNull KeyCommand command, @Nullable KeyStroke stroke) {
        synchronized (activeBindings) {
            if (stroke != null) {
                for (Map.Entry<KeyCommand, KeyStroke> entry : activeBindings.entrySet()) {
                    if (entry.getKey() != command && stroke.equals(entry.getValue())) {
                        entry.setValue(null);
                    }
                }
            }
            activeBindings.put(command, stroke);
        }
        saveToFile();
        notifyListeners();
    }

    /**
     * Resets a single command to its default shortcut.
     */
    public synchronized void resetKeybinding(@NonNull KeyCommand command) {
        activeBindings.put(command, KeyStroke.fromString(command.getDefaultKeySpec()));
        saveToFile();
        notifyListeners();
    }

    /**
     * Resets all commands to their factory defaults.
     */
    public synchronized void resetToDefaults() {
        loadDefaults();
        saveToFile();
        notifyListeners();
    }

    /**
     * Checks if another command in an overlapping scope already uses the given KeyStroke.
     * Returns the conflicting command, or null if there is no conflict.
     */
    @Nullable
    public KeyCommand findConflictingCommand(@NonNull KeyCommand command, @Nullable KeyStroke stroke) {
        if (stroke == null) return null;

        synchronized (activeBindings) {
            for (Map.Entry<KeyCommand, KeyStroke> entry : activeBindings.entrySet()) {
                KeyCommand otherCmd = entry.getKey();
                if (otherCmd == command) continue;

                KeyStroke otherStroke = entry.getValue();
                if (otherStroke != null && otherStroke.equals(stroke)) {
                    // Conflict if in the same scope or one is Global and the other is Editor
                    return otherCmd;
                }
            }
        }
        return null;
    }

    /**
     * Serializes all current keybindings to a JSON string.
     */
    @NonNull
    public String getSerializedJson() {
        JSONArray array = new JSONArray();
        synchronized (activeBindings) {
            for (KeyCommand cmd : KeyCommand.values()) {
                JSONObject obj = new JSONObject();
                try {
                    obj.put("command", cmd.getCommandId());
                    KeyStroke stroke = activeBindings.get(cmd);
                    obj.put("key", stroke != null ? stroke.toSpecString() : "");
                    array.put(obj);
                } catch (Exception ignored) {
                }
            }
        }
        try {
            return array.toString(2);
        } catch (Exception e) {
            return array.toString();
        }
    }

    /**
     * Persists the active keybindings to keybindings.json.
     */
    public synchronized boolean saveToFile() {
        try (FileOutputStream fos = new FileOutputStream(keybindingsFile);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            writer.write(getSerializedJson());
            writer.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Exports keybindings to an external file.
     */
    public boolean exportToFile(@NonNull File destination) {
        try (FileOutputStream fos = new FileOutputStream(destination);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            writer.write(getSerializedJson());
            writer.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Imports keybindings from a source file, resolves any duplicate shortcut conflicts,
     * persists to disk, and updates the active configuration.
     *
     * @param source The source keybindings JSON file.
     * @return ImportResult containing success status and count of duplicate shortcuts unassigned.
     */
    public synchronized ImportResult importFromFile(@NonNull File source) {
        if (!source.exists() || !source.canRead()) {
            return ImportResult.failure();
        }
        try (FileInputStream fis = new FileInputStream(source);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }

            ImportResult result = parseJsonAndApplyWithResult(sb.toString());
            if (result.isSuccess()) {
                saveToFile();
                notifyListeners();
            }
            return result;
        } catch (Exception e) {
            return ImportResult.failure();
        }
    }

    @NonNull
    public File getKeybindingsJsonFile() {
        return keybindingsFile;
    }

    @NonNull
    public List<KeyCommand> getAllCommands() {
        List<KeyCommand> list = new ArrayList<>();
        Collections.addAll(list, KeyCommand.values());
        return list;
    }

    public void addListener(@NonNull OnKeybindingsChangedListener listener) {
        listeners.add(listener);
    }

    public void removeListener(@NonNull OnKeybindingsChangedListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (OnKeybindingsChangedListener listener : listeners) {
            listener.onKeybindingsChanged();
        }
    }
}
