package com.cocode.vcode.ide.ui.editor.viewer;

import android.content.Context;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.terminal.TerminalKeyBar;
import com.cocode.vcode.ide.core.terminal.TerminalSession;
import com.cocode.vcode.ide.core.terminal.TerminalView;
import com.cocode.vcode.ide.data.model.EditorFile;
import com.cocode.vcode.ide.databinding.ViewerTerminalBinding;
import com.cocode.vcode.ide.ui.editor.EditorViewModel;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;
import com.cocode.vcode.ide.views.CodeEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * FileViewer implementation for the in-editor integrated terminal.
 * Binds the terminal screen, accessory keyboard bar, and underlying shell session.
 */
public class TerminalViewer implements IFileViewer {

    private ViewerTerminalBinding binding;
    private Context context;
    private EditorFile currentFile;
    private EditorViewModel viewModel;
    private TerminalSession session;

    @Override
    public View getView(Context context, ViewGroup parent) {
        if (binding == null) {
            this.context = context;
            binding = ViewerTerminalBinding.inflate(LayoutInflater.from(context), parent, false);
            setupUI();
        }
        return binding.getRoot();
    }

    private void setupUI() {
        binding.btnClear.setOnClickListener(v -> {
            if (session != null) {
                session.clearScreen();
                if (binding != null) {
                    binding.terminalView.resetScroll();
                }
                saveStateToVirtualFile();
            }
        });

        binding.btnRestart.setOnClickListener(v -> {
            if (session != null) {
                session.restart();
                if (binding != null) {
                    binding.terminalView.resetScroll();
                }
                updateStatusIndicator(true);
                saveStateToVirtualFile();
            }
        });

        binding.terminalKeyBar.setOnKeyActionListener(new TerminalKeyBar.OnKeyActionListener() {
            @Override
            public void onSpecialKey(int specialKey) {
                if (session != null) {
                    session.sendSpecialKey(specialKey);
                }
            }

            @Override
            public void onInsertText(String text) {
                if (session != null && text != null) {
                    session.handleInput(text);
                }
            }

            @Override
            public void onCtrlToggled(boolean active) {
                if (session != null) {
                    session.setCtrlActive(active);
                }
            }

            @Override
            public void onAltToggled(boolean active) {
                if (session != null) {
                    session.setAltActive(active);
                }
            }
        });
    }

    @Override
    public void bindFile(EditorFile file, EditorViewModel viewModel) {
        this.currentFile = file;
        this.viewModel = viewModel;

        File projectRoot = viewModel != null ? viewModel.getProjectRoot() : null;
        if (projectRoot == null && file != null && file.getFile() != null) {
            projectRoot = file.getFile().getParentFile();
        }

        if (session == null && projectRoot != null) {
            session = new TerminalSession(context, projectRoot);
            int initialCols = TerminalView.calculateColumnsForDisplay(context);
            session.getEmulator().setColumns(initialCols);
            session.setModifierResetListener(() -> {
                if (binding != null) {
                    binding.terminalKeyBar.consumeCtrl();
                    binding.terminalKeyBar.consumeAlt();
                }
            });
            loadStateFromVirtualFile(file != null ? file.getContent() : null);
            session.setListener(new TerminalSession.TerminalSessionListener() {
                @Override
                public void onOutput() {
                    if (binding != null) {
                        binding.terminalView.invalidate();
                    }
                }

                @Override
                public void onProcessTerminated(int exitCode) {
                    if (binding != null) {
                        updateStatusIndicator(false);
                    }
                }

                @Override
                public void onCommandExecuted(String command) {
                    saveStateToVirtualFile();
                    if (viewModel != null) {
                        viewModel.onFileSystemChanged();
                    }
                }

                @Override
                public void onCommandFinished(String command, int exitCode) {
                    saveStateToVirtualFile();
                    if (viewModel != null) {
                        viewModel.onFileSystemChanged();
                    }
                }
            });
            session.start();
        }

        if (session != null && binding != null) {
            binding.terminalView.attachSession(session);
            binding.terminalView.updateTheme();
            binding.terminalKeyBar.updateTheme();
            updateStatusIndicator(session.isRunning());
            binding.terminalView.post(() -> binding.terminalView.requestFocus());
        }
    }

    private void updateStatusIndicator(boolean running) {
        if (binding == null || context == null) return;
        int colorRes = running ? R.color.vcode_accent_success : R.color.vcode_accent_error;
        binding.statusDot.getBackground().setColorFilter(
                ContextCompat.getColor(context, colorRes),
                PorterDuff.Mode.SRC_IN
        );
    }

    @Override
    public void onResume() {
        if (binding != null) {
            binding.terminalView.updateTheme();
            binding.terminalKeyBar.updateTheme();
            binding.terminalView.post(() -> binding.terminalView.requestFocus());
        }
        if (session != null) {
            updateStatusIndicator(session.isRunning());
        }
    }

    @Override
    public void onPause() {
        saveStateToVirtualFile();
    }

    @Override
    public void destroy() {
        if (session != null) {
            session.destroy();
            session = null;
        }
        binding = null;
        context = null;
        currentFile = null;
        viewModel = null;
    }

    @Override
    public void flushContentToViewModel() {
        saveStateToVirtualFile();
    }

    private void saveStateToVirtualFile() {
        if (currentFile == null || session == null || viewModel == null) return;
        try {
            JSONObject state = new JSONObject();

            JSONArray historyArray = new JSONArray();
            List<String> histList = session.getHistory();
            int start = Math.max(0, histList.size() - 50);
            for (int i = start; i < histList.size(); i++) {
                String cmd = histList.get(i);
                if (cmd != null && !cmd.isEmpty()) {
                    historyArray.put(cmd);
                }
            }
            state.put("history", historyArray);

            File curDir = session.getCurrentDir();
            File projectRoot = viewModel.getProjectRoot();
            if (curDir != null && projectRoot != null) {
                String relPath = com.cocode.vcode.ide.utils.FileUtils.getRelativePath(projectRoot, curDir);
                state.put("cwd", relPath != null ? relPath : "");
            }

            String bufferText = session.getBuffer().getAllAnsiText();
            if (bufferText != null && !bufferText.isEmpty()) {
                String cleanBuffer = cleanTrailingPrompt(bufferText);
                state.put("buffer", cleanBuffer);
            }

            currentFile.setContent(state.toString());
            currentFile.setDirty(true);
            viewModel.notifyFileDirtyStatusChanged();
            viewModel.triggerAutoSave();
        } catch (Exception ignored) {
        }
    }

    private String cleanTrailingPrompt(String text) {
        if (text == null) return "";
        int lastNewline = text.lastIndexOf('\n');
        if (lastNewline >= 0) {
            String lastLine = text.substring(lastNewline + 1);
            if (lastLine.endsWith("$ ") || lastLine.endsWith("# ")) {
                return text.substring(0, lastNewline);
            }
        } else if (text.endsWith("$ ") || text.endsWith("# ")) {
            return "";
        }
        return text;
    }

    private void loadStateFromVirtualFile(String content) {
        if (content == null || content.trim().isEmpty() || session == null) return;
        try {
            JSONObject state = new JSONObject(content);
            if (state.has("history")) {
                JSONArray historyArray = state.getJSONArray("history");
                List<String> hist = new ArrayList<>();
                for (int i = 0; i < historyArray.length(); i++) {
                    String cmd = historyArray.optString(i, null);
                    if (cmd != null && !cmd.isEmpty()) {
                        hist.add(cmd);
                    }
                }
                session.setHistory(hist);
            }

            if (state.has("cwd")) {
                String relCwd = state.optString("cwd", "");
                if (!relCwd.isEmpty() && viewModel != null && viewModel.getProjectRoot() != null) {
                    File targetDir = new File(viewModel.getProjectRoot(), relCwd);
                    if (targetDir.exists() && targetDir.isDirectory()) {
                        session.setCurrentDir(targetDir);
                    }
                }
            }

            if (state.has("buffer")) {
                String buf = state.optString("buffer", "");
                if (!buf.isEmpty()) {
                    session.setInitialBuffer(buf);
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Nullable
    @Override
    public CodeEditText getCodeEditor() {
        return null;
    }

    @Override
    public EditorFile getCurrentFile() {
        return currentFile;
    }

    @Nullable
    public TerminalView getTerminalView() {
        return binding != null ? binding.terminalView : null;
    }
}
