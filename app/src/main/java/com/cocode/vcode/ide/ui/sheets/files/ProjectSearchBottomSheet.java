package com.cocode.vcode.ide.ui.sheets.files;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.editor.search.SearchEngine;
import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspLocation;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.core.model.SearchResult;
import com.cocode.vcode.ide.databinding.BottomSheetProjectSearchBinding;
import com.cocode.vcode.ide.ui.sheets.BaseBottomSheetDialogFragment;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;
import com.cocode.vcode.ide.views.span.SolidHighlightSpan;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bottom sheet dialog for searching and replacing text across all files in the project.
 */
public class ProjectSearchBottomSheet extends BaseBottomSheetDialogFragment {

    private Mode mode = Mode.FIND_IN_FILES;
    private File projectRoot;
    private SearchEngine searchEngine;
    private SearchAdapter adapter;
    private ProjectSearchListener listener;
    private BottomSheetProjectSearchBinding binding;
    private Runnable pendingSearch;
    private boolean isRegex = false;
    private boolean isCaseSensitive = false;
    private boolean isWholeWord = false;
    // Store latest results for replace all
    private List<FileGroup> currentResults = new ArrayList<>();
    // Usages mode state
    private String customTitle;
    private String queryWord;
    private List<LspLocation> locations;

    static ProjectSearchResult createMatchSnippet(File file, int lineNum, String rawLine,
                                                  int colStart, int colEnd, String query) {
        String line = rawLine != null ? rawLine.replace('\r', ' ').replace('\n', ' ') : "";

        if (colStart < 0 || colEnd <= colStart || colStart >= line.length()) {
            if (query != null && !query.isEmpty()) {
                int idx = line.indexOf(query);
                if (idx < 0) {
                    idx = line.toLowerCase().indexOf(query.toLowerCase());
                }
                if (idx >= 0) {
                    colStart = idx;
                    colEnd = idx + query.length();
                }
            }
        }

        if (colEnd < colStart) {
            colEnd = colStart;
        }

        int indent = 0;
        while (indent < line.length() && (line.charAt(indent) == ' ' || line.charAt(indent) == '\t')) {
            indent++;
        }

        String snippet;
        int matchStart;
        int matchEnd;

        int trimmedLen = line.length() - indent;
        if (trimmedLen <= 120) {
            snippet = line.substring(indent);
            matchStart = Math.max(0, colStart - indent);
            matchEnd = Math.min(snippet.length(), Math.max(matchStart, colEnd - indent));
        } else {
            int winStart = Math.max(indent, colStart - 25);
            int winEnd = Math.min(line.length(), Math.max(colEnd + 35, winStart + 80));
            String prefix = winStart > indent ? "..." : "";
            String suffix = winEnd < line.length() ? "..." : "";
            snippet = prefix + line.substring(winStart, winEnd) + suffix;
            matchStart = prefix.length() + Math.max(0, colStart - winStart);
            matchEnd = matchStart + Math.max(0, colEnd - colStart);
            matchEnd = Math.min(snippet.length(), matchEnd);
        }

        int column = colStart >= 0 ? colStart + 1 : 1;
        return new ProjectSearchResult(file, lineNum, column, snippet, matchStart, matchEnd);
    }

    public void setProjectRoot(File root) {
        this.projectRoot = root;
        this.mode = Mode.FIND_IN_FILES;
    }

    public void setListener(ProjectSearchListener listener) {
        this.listener = listener;
    }

    public void setUsages(String title, String query, List<LspLocation> locations) {
        this.mode = Mode.FIND_USAGES;
        this.customTitle = title;
        this.queryWord = query;
        this.locations = locations;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetProjectSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.tvTitle.setTypeface(FontManager.getInstance().getUiSemiBold(requireContext()));
        binding.rvSearchResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SearchAdapter();
        binding.rvSearchResults.setAdapter(adapter);

        if (mode == Mode.FIND_USAGES) {
            binding.llSearchInputsContainer.setVisibility(View.GONE);
            binding.tvTitle.setText(customTitle != null && !customTitle.isEmpty()
                    ? customTitle
                    : getString(R.string.vcode_find_usages));
            loadUsages();
        } else {
            binding.llSearchInputsContainer.setVisibility(View.VISIBLE);
            binding.tvTitle.setText(R.string.vcode_find_in_files);
            searchEngine = new SearchEngine();

            UiUtils.setViewRounded(binding.etSearchQuery, UiUtils.dpToPx(requireContext(), 10), androidx.core.content.ContextCompat.getColor(requireContext(), R.color.vcode_bg_elevated));
            UiUtils.setViewRounded(binding.etReplaceQuery, UiUtils.dpToPx(requireContext(), 10), androidx.core.content.ContextCompat.getColor(requireContext(), R.color.vcode_bg_elevated));
            binding.etSearchQuery.setTypeface(FontManager.getInstance().getUiMedium(requireContext()));
            binding.etReplaceQuery.setTypeface(FontManager.getInstance().getUiMedium(requireContext()));

            setupToggles();
            setupReplaceAll();

            binding.etSearchQuery.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (pendingSearch != null) {
                        binding.etSearchQuery.removeCallbacks(pendingSearch);
                    }
                    pendingSearch = () -> performSearch(s.toString());
                    binding.etSearchQuery.postDelayed(pendingSearch, 300);
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }
    }

    private void setupToggles() {
        binding.btnToggleReplace.setOnClickListener(v -> {
            boolean isVisible = binding.llReplaceContainer.getVisibility() == View.VISIBLE;
            binding.llReplaceContainer.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            binding.btnToggleReplace.setRotation(isVisible ? 0 : 90);
        });

        View.OnClickListener toggleListener = v -> {
            if (v == binding.btnToggleCase) isCaseSensitive = !isCaseSensitive;
            else if (v == binding.btnToggleWord) isWholeWord = !isWholeWord;
            else if (v == binding.btnToggleRegex) isRegex = !isRegex;

            updateToggleUi(binding.btnToggleCase, isCaseSensitive);
            updateToggleUi(binding.btnToggleWord, isWholeWord);
            updateToggleUi(binding.btnToggleRegex, isRegex);

            if (binding.etSearchQuery.getText() != null) {
                performSearch(binding.etSearchQuery.getText().toString());
            }
        };

        binding.btnToggleCase.setOnClickListener(toggleListener);
        binding.btnToggleWord.setOnClickListener(toggleListener);
        binding.btnToggleRegex.setOnClickListener(toggleListener);
    }

    private void updateToggleUi(com.google.android.material.button.MaterialButton btn, boolean isActive) {
        if (isActive) {
            btn.setBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.vcode_branch_chip_bg));
            btn.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.vcode_accent_primary));
        } else {
            btn.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            btn.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.vcode_text_secondary));
        }
    }

    private void setupReplaceAll() {
        binding.btnReplaceAll.setOnClickListener(v -> {
            if (currentResults == null || currentResults.isEmpty()) return;

            String query = binding.etSearchQuery.getText() != null ? binding.etSearchQuery.getText().toString() : "";
            if (query.trim().isEmpty()) return;

            android.view.View dialogView = android.view.LayoutInflater.from(requireContext()).inflate(R.layout.dialog_replace_all_confirm, null);
            androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setView(dialogView)
                    .setCancelable(true)
                    .create();

            android.widget.TextView tvTitle = dialogView.findViewById(R.id.tv_replace_title);
            android.widget.TextView tvDesc = dialogView.findViewById(R.id.tv_replace_desc);
            com.google.android.material.button.MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel_replace);
            com.google.android.material.button.MaterialButton btnConfirm = dialogView.findViewById(R.id.btn_confirm_replace);

            tvTitle.setTypeface(com.cocode.vcode.ide.utils.FontManager.getInstance().getUiSemiBold(requireContext()));
            tvDesc.setTypeface(com.cocode.vcode.ide.utils.FontManager.getInstance().getUiMedium(requireContext()));
            btnCancel.setTypeface(com.cocode.vcode.ide.utils.FontManager.getInstance().getUiSemiBold(requireContext()));
            btnConfirm.setTypeface(com.cocode.vcode.ide.utils.FontManager.getInstance().getUiSemiBold(requireContext()));

            tvDesc.setText(requireContext().getString(R.string.vcode_confirm_replace_all_desc, currentResults.size()));

            btnCancel.setOnClickListener(v2 -> dialog.dismiss());
            btnConfirm.setOnClickListener(v2 -> {
                dialog.dismiss();
                performReplaceAll();
            });
            dialog.show();

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                int screenWidth = requireContext().getResources().getDisplayMetrics().widthPixels;
                int maxWidth = requireContext().getResources().getDimensionPixelSize(R.dimen.dialog_max_width);
                int targetWidth = Math.min((int) (screenWidth * 0.92f), maxWidth);
                dialog.getWindow().setLayout(targetWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
    }

    private void performReplaceAll() {
        if (currentResults == null || currentResults.isEmpty()) return;

        String query = binding.etSearchQuery.getText() != null ? binding.etSearchQuery.getText().toString() : "";
        String replaceText = binding.etReplaceQuery.getText() != null ? binding.etReplaceQuery.getText().toString() : "";
        if (query.isEmpty()) return;

        binding.progressSearch.setVisibility(View.VISIBLE);
        ExecutorProvider.getInstance().runOnCpu(() -> {
            for (FileGroup group : currentResults) {
                try {
                    String content = new String(java.nio.file.Files.readAllBytes(group.file.toPath()), StandardCharsets.UTF_8);
                    String newContent = searchEngine.replaceAll(query, content, replaceText, isCaseSensitive, isRegex, isWholeWord);
                    if (!content.equals(newContent)) {
                        java.nio.file.Files.write(group.file.toPath(), newContent.getBytes(StandardCharsets.UTF_8));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            ExecutorProvider.getInstance().runOnMain(() -> {
                binding.progressSearch.setVisibility(View.INVISIBLE);
                android.widget.Toast.makeText(requireContext(), getString(R.string.vcode_replaced_in_files, currentResults.size()), android.widget.Toast.LENGTH_SHORT).show();
                performSearch(query);
            });
        });
    }

    private void performSearch(String query) {
        if (query == null || query.trim().isEmpty() || projectRoot == null) {
            currentResults.clear();
            adapter.setResults(new ArrayList<>());
            updateEmptyState(false);
            return;
        }

        binding.progressSearch.setVisibility(View.VISIBLE);
        ExecutorProvider.getInstance().runOnCpu(() -> {
            List<FileGroup> allResults = new ArrayList<>();
            searchInDirectory(projectRoot, query, allResults);

            ExecutorProvider.getInstance().runOnMain(() -> {
                if (!isAdded()) return;
                binding.progressSearch.setVisibility(View.INVISIBLE);
                currentResults = allResults;
                adapter.setResults(allResults);
                updateEmptyState(allResults.isEmpty());
            });
        });
    }

    private void updateEmptyState(boolean isEmpty) {
        if (binding != null && binding.tvEmptyState != null) {
            binding.tvEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
    }

    private void loadUsages() {
        if (locations == null || locations.isEmpty()) {
            adapter.setResults(new ArrayList<>());
            updateEmptyState(true);
            return;
        }

        binding.progressSearch.setVisibility(View.VISIBLE);
        updateEmptyState(false);

        List<LspLocation> locCopy = new ArrayList<>(locations);
        String targetQuery = queryWord != null ? queryWord : "";

        ExecutorProvider.getInstance().runOnCpu(() -> {
            List<FileGroup> groups = buildUsagesGroups(locCopy, targetQuery);

            ExecutorProvider.getInstance().runOnMain(() -> {
                if (!isAdded()) return;
                binding.progressSearch.setVisibility(View.INVISIBLE);
                currentResults = groups;
                adapter.setResults(groups);
                updateEmptyState(groups.isEmpty());
            });
        });
    }

    private List<FileGroup> buildUsagesGroups(List<LspLocation> locList, String query) {
        Map<String, List<LspLocation>> byFile = new LinkedHashMap<>();
        for (LspLocation loc : locList) {
            if (loc == null || loc.uri == null) continue;
            String path = loc.uri;
            if (path.startsWith("file://")) {
                try {
                    path = new java.net.URI(loc.uri).getPath();
                } catch (Exception e) {
                    path = path.substring(7);
                }
            }
            List<LspLocation> list = byFile.get(path);
            if (list == null) {
                list = new ArrayList<>();
                byFile.put(path, list);
            }
            list.add(loc);
        }

        List<FileGroup> groups = new ArrayList<>();
        String effectiveQuery = query;

        for (Map.Entry<String, List<LspLocation>> entry : byFile.entrySet()) {
            File file = new File(entry.getKey());
            if (!file.exists()) continue;

            List<String> lines = getFileLines(file);
            if (lines.isEmpty()) continue;

            if (effectiveQuery.isEmpty() && !entry.getValue().isEmpty()) {
                LspLocation firstLoc = entry.getValue().get(0);
                if (firstLoc.range != null && firstLoc.range.start != null) {
                    int l0 = firstLoc.range.start.line;
                    if (l0 >= 0 && l0 < lines.size()) {
                        String sampleLine = lines.get(l0);
                        int sc = firstLoc.range.start.character;
                        int ec = firstLoc.range.end != null && firstLoc.range.end.character > sc
                                ? firstLoc.range.end.character
                                : sc;
                        if (sc >= 0 && sc < sampleLine.length()) {
                            effectiveQuery = sampleLine.substring(sc, Math.min(ec, sampleLine.length()));
                        }
                    }
                }
            }

            FileGroup group = new FileGroup();
            group.file = file;
            group.expanded = true;

            for (LspLocation loc : entry.getValue()) {
                int line0 = loc.range != null && loc.range.start != null ? loc.range.start.line : 0;
                int lineNum = line0 + 1;
                String rawLine = (line0 >= 0 && line0 < lines.size()) ? lines.get(line0) : "";

                int colStart = loc.range != null && loc.range.start != null ? loc.range.start.character : -1;
                int colEnd = loc.range != null && loc.range.end != null ? loc.range.end.character : -1;

                ProjectSearchResult match = createMatchSnippet(file, lineNum, rawLine, colStart, colEnd, effectiveQuery);
                group.matches.add(match);
            }

            if (!group.matches.isEmpty()) {
                groups.add(group);
            }
        }
        return groups;
    }

    private List<String> getFileLines(File file) {
        LspDocument doc = ProjectIndex.getInstance().getDocument(file.getAbsolutePath());
        if (doc != null && doc.text != null) {
            String[] split = doc.text.split("\r?\n", -1);
            return Arrays.asList(split);
        }

        List<String> lines = new ArrayList<>();
        if (file.length() > 5 * 1024 * 1024) return lines;
        try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        } catch (Exception ignored) {
        }
        return lines;
    }

    private void searchInDirectory(File dir, String query, List<FileGroup> outResults) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            String name = f.getName().toLowerCase();
            // Directory exclusions
            if (name.equals(".git") || name.equals("node_modules") || name.equals(".idea") || name.equals("build") || name.equals(".vcode"))
                continue;

            if (f.isDirectory()) {
                searchInDirectory(f, query, outResults);
            } else {
                // File exclusions
                if (name.equals("snippets.json"))
                    continue;

                // Binary and image exclusions
                if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                        name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".bmp") ||
                        name.endsWith(".ico") || name.endsWith(".ttf") || name.endsWith(".woff") ||
                        name.endsWith(".woff2") || name.endsWith(".eot") || name.endsWith(".pdf") ||
                        name.endsWith(".mp3") || name.endsWith(".mp4") || name.endsWith(".wav") ||
                        name.endsWith(".ogg") || name.endsWith(".zip") || name.endsWith(".tar") ||
                        name.endsWith(".gz") || name.endsWith(".apk") || name.endsWith(".jar") ||
                        name.endsWith(".class") || name.endsWith(".dex")) {
                    continue;
                }

                try {
                    // Only read reasonably sized files, skip files > 500kb
                    if (f.length() > 1024 * 500) continue;

                    StringBuilder sb = new StringBuilder();
                    try (java.io.BufferedReader br = new java.io.BufferedReader(
                            new java.io.InputStreamReader(new java.io.FileInputStream(f), StandardCharsets.UTF_8))) {
                        char[] buf = new char[4096];
                        int read;
                        while ((read = br.read(buf)) != -1) sb.append(buf, 0, read);
                    }
                    String content = sb.toString();
                    List<SearchResult> results = searchEngine.find(query, content, isCaseSensitive, isRegex, isWholeWord);
                    if (!results.isEmpty()) {
                        FileGroup group = new FileGroup();
                        group.file = f;
                        for (SearchResult r : results) {
                            int start = Math.max(0, r.absoluteStart - 30);
                            int end = Math.min(content.length(), r.absoluteEnd + 30);
                            String rawSnippet = content.substring(start, end);
                            String snippet = rawSnippet.replace('\n', ' ');
                            int matchStart = r.absoluteStart - start;
                            int matchEnd = r.absoluteEnd - start;
                            group.matches.add(new ProjectSearchResult(f, r.lineNumber, r.columnStart, snippet, matchStart, matchEnd));
                        }
                        outResults.add(group);
                        if (outResults.size() > 100) return; // limit files
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    public enum Mode {
        FIND_IN_FILES,
        FIND_USAGES
    }

    public interface ProjectSearchListener {
        void onSearchResultSelected(File file, int lineNumber, int column);
    }

    static class ProjectSearchResult {
        File file;
        int line;
        int column;
        String snippet;
        int matchStart;
        int matchEnd;

        ProjectSearchResult(File file, int line, int column, String snippet, int matchStart, int matchEnd) {
            this.file = file;
            this.line = line;
            this.column = column;
            this.snippet = snippet;
            this.matchStart = matchStart;
            this.matchEnd = matchEnd;
        }

        ProjectSearchResult(File file, int line, String snippet, int matchStart, int matchEnd) {
            this(file, line, 1, snippet, matchStart, matchEnd);
        }
    }

    private class FileGroup {
        File file;
        List<ProjectSearchResult> matches = new ArrayList<>();
        boolean expanded = true;
    }

    private class SearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_FILE = 0;
        private static final int TYPE_MATCH = 1;
        private final List<Object> flattenedItems = new ArrayList<>();
        private List<FileGroup> fileGroups = new ArrayList<>();

        @SuppressLint("NotifyDataSetChanged")
        void setResults(List<FileGroup> newItems) {
            this.fileGroups = newItems;
            flatten();
        }

        @SuppressLint("NotifyDataSetChanged")
        private void flatten() {
            flattenedItems.clear();
            for (FileGroup group : fileGroups) {
                flattenedItems.add(group);
                if (group.expanded) {
                    flattenedItems.addAll(group.matches);
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            if (flattenedItems.get(position) instanceof FileGroup) return TYPE_FILE;
            return TYPE_MATCH;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_FILE) {
                com.cocode.vcode.ide.databinding.ItemProjectSearchFileBinding binding =
                        com.cocode.vcode.ide.databinding.ItemProjectSearchFileBinding.inflate(inflater, parent, false);
                return new FileViewHolder(binding);
            } else {
                com.cocode.vcode.ide.databinding.ItemProjectSearchMatchBinding binding =
                        com.cocode.vcode.ide.databinding.ItemProjectSearchMatchBinding.inflate(inflater, parent, false);
                return new MatchViewHolder(binding);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object item = flattenedItems.get(position);

            if (holder instanceof FileViewHolder) {
                FileGroup group = (FileGroup) item;
                FileViewHolder fh = (FileViewHolder) holder;

                fh.binding.tvFileName.setText(group.file.getName());
                fh.binding.tvFileName.setTypeface(FontManager.getInstance().getUiSemiBold(holder.itemView.getContext()));
                fh.binding.tvMatchesCount.setText(String.valueOf(group.matches.size()));
                fh.binding.tvMatchesCount.setTypeface(FontManager.getInstance().getUiMedium(holder.itemView.getContext()));

                com.cocode.vcode.ide.utils.FileIconHelper.setFileIconAndColor(fh.binding.ivFileIcon, group.file.getName());

                if (group.expanded) {
                    fh.binding.ivChevron.setImageResource(R.drawable.ic_chevron_down);
                } else {
                    fh.binding.ivChevron.setImageResource(R.drawable.ic_chevron_right);
                }

                fh.itemView.setOnClickListener(v -> {
                    group.expanded = !group.expanded;
                    flatten();
                });

            } else if (holder instanceof MatchViewHolder) {
                ProjectSearchResult match = (ProjectSearchResult) item;
                MatchViewHolder mh = (MatchViewHolder) holder;

                String lineCol = match.line + (match.column > 0 ? ":" + match.column : "") + ":";
                mh.binding.tvLineNumber.setText(lineCol);
                mh.binding.tvLineNumber.setTypeface(FontManager.getInstance().getCodeFont(holder.itemView.getContext()));

                android.text.SpannableString ss = new android.text.SpannableString(match.snippet);
                int color = androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.vcode_accent_warning);
                ss.setSpan(new SolidHighlightSpan(androidx.core.graphics.ColorUtils.setAlphaComponent(color, 100)),
                        Math.max(0, match.matchStart),
                        Math.min(ss.length(), match.matchEnd),
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                mh.binding.tvSnippet.setText(ss);
                mh.binding.tvSnippet.setTypeface(FontManager.getInstance().getCodeFont(holder.itemView.getContext()));

                mh.itemView.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onSearchResultSelected(match.file, match.line, match.column);
                    }
                    dismiss();
                });
            }
        }

        @Override
        public int getItemCount() {
            return flattenedItems.size();
        }

        class FileViewHolder extends RecyclerView.ViewHolder {
            com.cocode.vcode.ide.databinding.ItemProjectSearchFileBinding binding;

            FileViewHolder(@NonNull com.cocode.vcode.ide.databinding.ItemProjectSearchFileBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }

        class MatchViewHolder extends RecyclerView.ViewHolder {
            com.cocode.vcode.ide.databinding.ItemProjectSearchMatchBinding binding;

            MatchViewHolder(@NonNull com.cocode.vcode.ide.databinding.ItemProjectSearchMatchBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
