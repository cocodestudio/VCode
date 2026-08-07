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
import com.cocode.vcode.ide.core.model.SearchResult;
import com.cocode.vcode.ide.databinding.BottomSheetProjectSearchBinding;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;
import com.cocode.vcode.ide.views.span.SolidHighlightSpan;
import com.cocode.vcode.ide.data.repository.ProjectRepository;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ProjectSearchBottomSheet extends BottomSheetDialogFragment {

    private File projectRoot;
    private SearchEngine searchEngine;
    private SearchAdapter adapter;
    private ProjectSearchListener listener;

    private BottomSheetProjectSearchBinding binding;

    private Runnable pendingSearch;

    public void setProjectRoot(File root) {
        this.projectRoot = root;
    }

    public void setListener(ProjectSearchListener listener) {
        this.listener = listener;
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
        searchEngine = new SearchEngine();

        UiUtils.setViewRounded(binding.etSearchQuery, UiUtils.dpToPx(requireContext(), 10), androidx.core.content.ContextCompat.getColor(requireContext(), R.color.vcode_bg_elevated));
        binding.etSearchQuery.setTypeface(FontManager.getInstance().getUiMedium(requireContext()));

        binding.tvTitle.setTypeface(FontManager.getInstance().getUiSemiBold(requireContext()));

        binding.rvSearchResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SearchAdapter();
        binding.rvSearchResults.setAdapter(adapter);

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

    private void performSearch(String query) {
        if (query == null || query.trim().isEmpty() || projectRoot == null) {
            adapter.setResults(new ArrayList<>());
            return;
        }

        binding.progressSearch.setVisibility(View.VISIBLE);
        ExecutorProvider.getInstance().runOnCpu(() -> {
            List<FileGroup> allResults = new ArrayList<>();
            searchInDirectory(projectRoot, query, allResults);

            ExecutorProvider.getInstance().runOnMain(() -> {
                binding.progressSearch.setVisibility(View.INVISIBLE);
                adapter.setResults(allResults);
            });
        });
    }

    private void searchInDirectory(File dir, String query, List<FileGroup> outResults) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            String name = f.getName().toLowerCase();
            // Directory exclusions
            if (name.equals(".git") || name.equals("node_modules") || name.equals(".idea") || name.equals("build"))
                continue;

            if (f.isDirectory()) {
                searchInDirectory(f, query, outResults);
            } else {
                // File exclusions
                if (name.equals(ProjectRepository.META_FILE) || name.equals(ProjectRepository.SESSION_FILE) || name.equals("snippets.json"))
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
                    List<SearchResult> results = searchEngine.find(query, content, false, false, false);
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
                            group.matches.add(new ProjectSearchResult(f, r.lineNumber, snippet, matchStart, matchEnd));
                        }
                        outResults.add(group);
                        if (outResults.size() > 100) return; // limit files
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    public interface ProjectSearchListener {
        void onSearchResultSelected(File file, int lineNumber);
    }

    private class ProjectSearchResult {
        File file;
        int line;
        String snippet;
        int matchStart;
        int matchEnd;

        ProjectSearchResult(File file, int line, String snippet, int matchStart, int matchEnd) {
            this.file = file;
            this.line = line;
            this.snippet = snippet;
            this.matchStart = matchStart;
            this.matchEnd = matchEnd;
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

                mh.binding.tvLineNumber.setText(match.line + ":");
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
                        listener.onSearchResultSelected(match.file, match.line);
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
