package com.cocode.vcode.ide.ui.settings;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.data.preferences.ConfigurationItem;
import com.cocode.vcode.ide.data.preferences.ConfigurationRegistry;
import com.cocode.vcode.ide.databinding.FragmentSettingsExtensionsBinding;
import com.cocode.vcode.ide.utils.FontManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.List;

/**
 * Settings fragment dynamically rendering configurable preferences contributed by
 * project facets, language tooling, and third-party extensions.
 */
public class ExtensionSettingsFragment extends Fragment {

    private FragmentSettingsExtensionsBinding binding;

    public static ExtensionSettingsFragment newInstance() {
        return new ExtensionSettingsFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsExtensionsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FontManager fm = FontManager.getInstance();
        binding.appBarTitle.setTypeface(fm.getUiSemiBold(requireContext()));
        binding.btnBack.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());

        populateExtensionSettings();
    }

    private void populateExtensionSettings() {
        ConfigurationRegistry registry = AppSettings.getConfigurationRegistry();
        List<ConfigurationItem> items = registry.getAllItems();

        if (items.isEmpty()) {
            binding.tvEmptyState.setVisibility(View.VISIBLE);
            binding.cardItemsContainer.setVisibility(View.GONE);
            return;
        }

        binding.tvEmptyState.setVisibility(View.GONE);
        binding.cardItemsContainer.setVisibility(View.VISIBLE);
        binding.containerItems.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        FontManager fm = FontManager.getInstance();

        for (ConfigurationItem item : items) {
            View row = createSettingRow(inflater, fm, item, registry);
            binding.containerItems.addView(row);
        }
    }

    @NonNull
    private View createSettingRow(@NonNull LayoutInflater inflater,
                                  @NonNull FontManager fm,
                                  @NonNull ConfigurationItem item,
                                  @NonNull ConfigurationRegistry registry) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        int padH = (int) (16 * getResources().getDisplayMetrics().density);
        int padV = (int) (12 * getResources().getDisplayMetrics().density);
        row.setPadding(padH, padV, padH, padV);

        // Standard selectable item background
        android.util.TypedValue outValue = new android.util.TypedValue();
        requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        row.setBackgroundResource(outValue.resourceId);

        LinearLayout textLayout = new LinearLayout(requireContext());
        textLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textLayout.setLayoutParams(textParams);

        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText(item.getTitle());
        tvTitle.setTypeface(fm.getUiMedium(requireContext()));
        tvTitle.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);

        TextView tvDesc = new TextView(requireContext());
        tvDesc.setText(item.getDescription());
        tvDesc.setTypeface(fm.getUiFont(requireContext()));
        tvDesc.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);

        textLayout.addView(tvTitle);
        textLayout.addView(tvDesc);
        row.addView(textLayout);

        Object val = registry.get(item.getKey());
        if (val == null) {
            val = item.getDefaultValue();
        }

        if (item.getType() == Boolean.class) {
            MaterialSwitch switchView = new MaterialSwitch(requireContext());
            boolean isChecked = val instanceof Boolean ? (Boolean) val : false;
            switchView.setChecked(isChecked);
            switchView.setOnCheckedChangeListener((buttonView, checked) -> {
                registry.set(item.getKey(), checked);
            });
            row.setOnClickListener(v -> switchView.setChecked(!switchView.isChecked()));
            row.addView(switchView);
        } else {
            TextView tvValue = new TextView(requireContext());
            tvValue.setText(val != null ? String.valueOf(val) : "");
            tvValue.setTypeface(fm.getUiFont(requireContext()));
            row.addView(tvValue);

            final Object currentVal = val;
            row.setOnClickListener(v -> {
                EditText input = new EditText(requireContext());
                input.setText(currentVal != null ? String.valueOf(currentVal) : "");
                if (item.getType() == Integer.class || item.getType() == Long.class) {
                    input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
                }

                FrameLayout container = new FrameLayout(requireContext());
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.leftMargin = padH;
                params.rightMargin = padH;
                input.setLayoutParams(params);
                container.addView(input);

                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(item.getTitle())
                        .setView(container)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            String text = input.getText().toString().trim();
                            if (item.getType() == Integer.class) {
                                try {
                                    int intVal = Integer.parseInt(text);
                                    registry.set(item.getKey(), intVal);
                                    tvValue.setText(String.valueOf(intVal));
                                } catch (NumberFormatException ignored) {
                                }
                            } else if (item.getType() == Long.class) {
                                try {
                                    long longVal = Long.parseLong(text);
                                    registry.set(item.getKey(), longVal);
                                    tvValue.setText(String.valueOf(longVal));
                                } catch (NumberFormatException ignored) {
                                }
                            } else {
                                registry.set(item.getKey(), text);
                                tvValue.setText(text);
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        }

        return row;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
