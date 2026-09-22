package com.cocode.vcode.ide.ui.settings;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.core.template.FileTemplate;
import com.cocode.vcode.ide.databinding.ItemTemplateBinding;
import com.cocode.vcode.ide.utils.FontManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for displaying, searching, and managing file templates in Settings.
 */
public class TemplatesSettingsAdapter extends RecyclerView.Adapter<TemplatesSettingsAdapter.TemplateViewHolder> {

    public interface OnTemplateClickListener {
        void onTemplateClick(@NonNull FileTemplate template);
    }

    private final Context context;
    private final List<FileTemplate> allTemplates = new ArrayList<>();
    private final List<FileTemplate> filteredTemplates = new ArrayList<>();
    private final OnTemplateClickListener clickListener;
    private String currentQuery = "";

    public TemplatesSettingsAdapter(@NonNull Context context,
                                    @NonNull List<FileTemplate> templates,
                                    @Nullable OnTemplateClickListener clickListener) {
        this.context = context;
        this.allTemplates.addAll(templates);
        this.filteredTemplates.addAll(templates);
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public TemplateViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTemplateBinding binding = ItemTemplateBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new TemplateViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull TemplateViewHolder holder, int position) {
        FileTemplate template = filteredTemplates.get(position);
        holder.bind(template);
    }

    @Override
    public int getItemCount() {
        return filteredTemplates.size();
    }

    public void setTemplates(@NonNull List<FileTemplate> templates) {
        allTemplates.clear();
        allTemplates.addAll(templates);
        filter(currentQuery);
    }

    public void filter(@Nullable String query) {
        currentQuery = (query != null) ? query.trim().toLowerCase(Locale.getDefault()) : "";
        filteredTemplates.clear();

        if (currentQuery.isEmpty()) {
            filteredTemplates.addAll(allTemplates);
        } else {
            for (FileTemplate t : allTemplates) {
                String name = t.getName().toLowerCase(Locale.getDefault());
                String ext = t.getExtension().toLowerCase(Locale.getDefault());
                String content = t.getContent().toLowerCase(Locale.getDefault());

                if (name.contains(currentQuery) || ext.contains(currentQuery) || content.contains(currentQuery)) {
                    filteredTemplates.add(t);
                }
            }
        }
        notifyDataSetChanged();
    }

    public void refresh() {
        filter(currentQuery);
    }

    class TemplateViewHolder extends RecyclerView.ViewHolder {
        private final ItemTemplateBinding binding;

        TemplateViewHolder(ItemTemplateBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            FontManager fm = FontManager.getInstance();
            binding.tvTemplateName.setTypeface(fm.getUiMedium(context));
            binding.tvTemplateSnippet.setTypeface(fm.getCodeFont(context));
        }

        void bind(@NonNull FileTemplate template) {
            String extSuffix = " (." + template.getExtension() + ")";
            String title = template.getName();
            if (!title.toLowerCase(Locale.getDefault()).endsWith(extSuffix.toLowerCase(Locale.getDefault()))) {
                title += extSuffix;
            }
            binding.tvTemplateName.setText(title);

            // Extract at least 3 lines of content snippet
            String content = template.getContent().trim();
            String[] lines = content.split("\r?\n");
            StringBuilder snippet = new StringBuilder();
            int count = 0;
            for (String line : lines) {
                if (count >= 3) break;
                if (count > 0) snippet.append('\n');
                snippet.append(line);
                count++;
            }
            binding.tvTemplateSnippet.setText(snippet.length() > 0 ? snippet.toString() : context.getString(R.string.vcode_none));

            FileType fileType = FileType.fromExtension(template.getExtension());
            binding.ivFileIcon.setImageResource(fileType.getIconResId());
            binding.ivFileIcon.setColorFilter(ContextCompat.getColor(context, fileType.getColorResId()));

            binding.getRoot().setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onTemplateClick(template);
                }
            });
        }
    }
}
