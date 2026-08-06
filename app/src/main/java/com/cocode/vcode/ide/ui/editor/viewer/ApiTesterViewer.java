package com.cocode.vcode.ide.ui.editor.viewer;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.language.json.JsonSyntaxHighlighter;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.data.model.EditorFile;
import com.cocode.vcode.ide.databinding.ViewerApiTesterBinding;
import com.cocode.vcode.ide.ui.editor.EditorViewModel;
import com.cocode.vcode.ide.utils.CodeFormatter;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;
import com.cocode.vcode.ide.views.CodeEditText;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class ApiTesterViewer implements IFileViewer {

    private ViewerApiTesterBinding binding;
    private EditorFile currentFile;
    private EditorViewModel viewModel;
    private Context context;

    private String currentMethod = "GET";
    private boolean isUpdating = false;

    private final TextWatcher stateWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            saveStateToVirtualFile();
        }
    };

    @Override
    public View getView(Context context, ViewGroup parent) {
        if (binding == null) {
            this.context = context;
            binding = ViewerApiTesterBinding.inflate(LayoutInflater.from(context), parent, false);
            setupUI();
        }
        return binding.getRoot();
    }

    private void setupUI() {
        int radius = UiUtils.dpToPx(context, 10);
        int elevatedColor = ContextCompat.getColor(context, R.color.vcode_bg_elevated);

        UiUtils.setViewRounded(binding.tvMethod, radius, elevatedColor);
        UiUtils.setViewRounded(binding.etUrl, radius, elevatedColor);

        FontManager fontManager = FontManager.getInstance();
        binding.tvMethod.setTypeface(fontManager.getUiSemiBold(context));
        binding.etUrl.setTypeface(fontManager.getUiMedium(context));
        binding.tvTabHeaders.setTypeface(fontManager.getUiSemiBold(context));
        binding.tvTabBody.setTypeface(fontManager.getUiSemiBold(context));
        binding.btnSend.setTypeface(fontManager.getUiSemiBold(context));
        binding.btnAddHeader.setTypeface(fontManager.getUiSemiBold(context));
        binding.btnAddBody.setTypeface(fontManager.getUiSemiBold(context));
        binding.tvResponseLabel.setTypeface(fontManager.getUiSemiBold(context));
        binding.tvResponseStatus.setTypeface(fontManager.getUiSemiBold(context));
        binding.tvResponse.setTypeface(fontManager.getCodeFont(context));
        binding.tvTimeBadge.setTypeface(fontManager.getUiMedium(context));
        binding.tvSizeBadge.setTypeface(fontManager.getUiMedium(context));

        // TextView handles its own selection and isn't editable

        binding.tvMethod.setOnClickListener(v -> showMethodSelector());
        binding.btnSend.setOnClickListener(v -> executeRequest());

        binding.etUrl.addTextChangedListener(stateWatcher);

        binding.tvTabHeaders.setOnClickListener(v -> selectTab(true));
        binding.tvTabBody.setOnClickListener(v -> selectTab(false));

        binding.btnAddHeader.setOnClickListener(v -> {
            addHeaderRow("", "");
            saveStateToVirtualFile();
        });

        binding.btnAddBody.setOnClickListener(v -> {
            addBodyRow("", "");
            saveStateToVirtualFile();
        });
    }

    private void selectTab(boolean headers) {
        if (headers) {
            binding.tvTabHeaders.setTextColor(ContextCompat.getColor(context, R.color.vcode_accent_primary));
            binding.tvTabBody.setTextColor(ContextCompat.getColor(context, R.color.vcode_text_secondary));
            binding.layoutHeadersContainer.setVisibility(View.VISIBLE);
            binding.layoutBodyContainer.setVisibility(View.GONE);
        } else {
            binding.tvTabBody.setTextColor(ContextCompat.getColor(context, R.color.vcode_accent_primary));
            binding.tvTabHeaders.setTextColor(ContextCompat.getColor(context, R.color.vcode_text_secondary));
            binding.layoutBodyContainer.setVisibility(View.VISIBLE);
            binding.layoutHeadersContainer.setVisibility(View.GONE);
        }
    }

    private void addHeaderRow(String key, String value) {
        View rowView = LayoutInflater.from(context).inflate(R.layout.item_api_header_row, binding.containerHeadersList, false);
        EditText etKey = rowView.findViewById(R.id.et_header_key);
        EditText etValue = rowView.findViewById(R.id.et_header_value);
        View btnDelete = rowView.findViewById(R.id.btn_delete);

        FontManager fm = FontManager.getInstance();
        etKey.setTypeface(fm.getUiMedium(context));
        etValue.setTypeface(fm.getUiMedium(context));

        int radius = UiUtils.dpToPx(context, 8);
        int elevatedColor = ContextCompat.getColor(context, R.color.vcode_bg_elevated);
        UiUtils.setViewRounded(etKey, radius, elevatedColor);
        UiUtils.setViewRounded(etValue, radius, elevatedColor);

        etKey.setText(key);
        etValue.setText(value);

        etKey.addTextChangedListener(stateWatcher);
        etValue.addTextChangedListener(stateWatcher);

        btnDelete.setOnClickListener(v -> {
            binding.containerHeadersList.removeView(rowView);
            saveStateToVirtualFile();
        });

        binding.containerHeadersList.addView(rowView);
    }

    private void addBodyRow(String key, String value) {
        View rowView = LayoutInflater.from(context).inflate(R.layout.item_api_header_row, binding.containerBodyList, false);
        EditText etKey = rowView.findViewById(R.id.et_header_key);
        EditText etValue = rowView.findViewById(R.id.et_header_value);
        View btnDelete = rowView.findViewById(R.id.btn_delete);

        FontManager fm = FontManager.getInstance();
        etKey.setTypeface(fm.getUiMedium(context));
        etValue.setTypeface(fm.getUiMedium(context));

        int radius = UiUtils.dpToPx(context, 8);
        int elevatedColor = ContextCompat.getColor(context, R.color.vcode_bg_elevated);
        UiUtils.setViewRounded(etKey, radius, elevatedColor);
        UiUtils.setViewRounded(etValue, radius, elevatedColor);

        etKey.setText(key);
        etValue.setText(value);

        etKey.addTextChangedListener(stateWatcher);
        etValue.addTextChangedListener(stateWatcher);

        btnDelete.setOnClickListener(v -> {
            binding.containerBodyList.removeView(rowView);
            saveStateToVirtualFile();
        });

        binding.containerBodyList.addView(rowView);
    }

    private JSONObject getHeadersJson() {
        JSONObject obj = new JSONObject();
        for (int i = 0; i < binding.containerHeadersList.getChildCount(); i++) {
            View row = binding.containerHeadersList.getChildAt(i);
            EditText etKey = row.findViewById(R.id.et_header_key);
            EditText etValue = row.findViewById(R.id.et_header_value);
            String k = etKey.getText().toString().trim();
            String v = etValue.getText().toString().trim();
            if (!k.isEmpty()) {
                try {
                    obj.put(k, v);
                } catch (JSONException ignored) {
                }
            }
        }
        return obj;
    }

    private JSONObject getBodyJson() {
        JSONObject obj = new JSONObject();
        for (int i = 0; i < binding.containerBodyList.getChildCount(); i++) {
            View row = binding.containerBodyList.getChildAt(i);
            EditText etKey = row.findViewById(R.id.et_header_key);
            EditText etValue = row.findViewById(R.id.et_header_value);
            String k = etKey.getText().toString().trim();
            String v = etValue.getText().toString().trim();
            if (!k.isEmpty()) {
                try {
                    obj.put(k, v);
                } catch (JSONException ignored) {
                }
            }
        }
        return obj;
    }

    private void showMethodSelector() {
        List<String> methods = Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE");
        String currentSelection = binding.tvMethod.getText().toString();

        com.cocode.vcode.ide.ui.dialogs.CustomListDialog.show(
                context,
                "Select HTTP Method",
                methods,
                currentSelection,
                selected -> {
                    currentMethod = selected;
                    binding.tvMethod.setText(currentMethod);
                    saveStateToVirtualFile();
                }
        );
    }

    private void executeRequest() {
        String urlString = binding.etUrl.getText().toString().trim();
        if (urlString.isEmpty()) {
            Toast.makeText(context, R.string.vcode_url_cannot_be_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
            urlString = "http://" + urlString;
            binding.etUrl.setText(urlString);
        }

        String finalUrl = urlString;
        JSONObject headersJson = getHeadersJson();
        JSONObject bodyJson = getBodyJson();
        String bodyText = bodyJson.length() > 0 ? bodyJson.toString() : "";

        binding.tvResponseStatus.setVisibility(View.GONE);
        binding.tvTimeBadge.setVisibility(View.GONE);
        binding.tvSizeBadge.setVisibility(View.GONE);
        binding.tvResponse.setVisibility(View.INVISIBLE);
        binding.pbLoading.setVisibility(View.VISIBLE);

        ExecutorProvider.getInstance().runOnIo(() -> {
            long startTime = System.currentTimeMillis();
            try {
                URL url = new URL(finalUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod(currentMethod);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);

                Iterator<String> keys = headersJson.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    conn.setRequestProperty(key, headersJson.getString(key));
                }

                boolean canHaveBody = currentMethod.equals("POST") || currentMethod.equals("PUT") || currentMethod.equals("PATCH");
                if (canHaveBody && !bodyText.isEmpty()) {
                    conn.setDoOutput(true);
                    try (OutputStream os = conn.getOutputStream()) {
                        byte[] input = bodyText.getBytes(StandardCharsets.UTF_8);
                        os.write(input, 0, input.length);
                    }
                }

                int statusCode = conn.getResponseCode();
                String statusMessage = conn.getResponseMessage();

                StringBuilder response = getStatusCode(statusCode, conn);
                conn.disconnect();

                long timeTaken = System.currentTimeMillis() - startTime;

                String rawResponse = response.toString();
                int byteSize = rawResponse.getBytes(StandardCharsets.UTF_8).length;
                String sizeStr = formatByteSize(byteSize);

                String formattedResponse = rawResponse;
                try {
                    if (formattedResponse.trim().startsWith("{") || formattedResponse.trim().startsWith("[")) {
                        formattedResponse = CodeFormatter.format(formattedResponse, FileType.JSON);
                    }
                } catch (Exception ignored) {
                }

                String finalFormattedResponse = formattedResponse;
                CharSequence highlightedResponse = finalFormattedResponse;
                try {
                    JsonSyntaxHighlighter highlighter = new JsonSyntaxHighlighter(context);
                    highlightedResponse = highlighter.highlight(finalFormattedResponse);
                } catch (Exception ignored) {
                }

                postResult(highlightedResponse, statusCode, statusMessage, timeTaken, sizeStr, statusCode >= 200 && statusCode < 300);

            } catch (Exception e) {
                long timeTaken = System.currentTimeMillis() - startTime;
                postResult("Error: " + e.getMessage(), -1, null, timeTaken, "0 B", false);
            }
        });
    }

    @NonNull
    private StringBuilder getStatusCode(int statusCode, HttpURLConnection conn) throws IOException {
        InputStream is = (statusCode >= 200 && statusCode < 300) ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder response = new StringBuilder();
        if (is != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line).append("\n");
                }
            }
        }
        return response;
    }

    private String formatByteSize(int bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024f);
        return String.format(Locale.getDefault(),"%.2f MB", bytes / (1024f * 1024f));
    }

    private void postResult(CharSequence result, int statusCode, String statusMessage, long timeTaken, String sizeStr, boolean success) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (binding == null) return;

            String statusText;
            int radius = UiUtils.dpToPx(context, 10);
            if (statusCode == -1) {
                statusText = "ERROR";
                UiUtils.setViewRounded(binding.tvResponseStatus, radius, Color.parseColor("#F44336"));
            } else {
                statusText = statusCode + " " + (statusMessage != null ? statusMessage : "OK");
                int color = success ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336");
                UiUtils.setViewRounded(binding.tvResponseStatus, radius, color);
            }

            binding.tvResponseStatus.setTextColor(Color.WHITE);
            binding.tvResponseStatus.setText(statusText);
            binding.tvTimeBadge.setText(timeTaken + "ms");
            binding.tvSizeBadge.setText(sizeStr);

            binding.tvResponseStatus.setVisibility(View.VISIBLE);
            binding.tvTimeBadge.setVisibility(View.VISIBLE);
            binding.tvSizeBadge.setVisibility(View.VISIBLE);
            binding.tvResponse.setVisibility(View.VISIBLE);
            binding.pbLoading.setVisibility(View.GONE);

            binding.tvResponse.setTextIsSelectable(true);
            binding.tvResponse.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
            binding.tvResponse.setText(result);
        });
    }

    private void saveStateToVirtualFile() {
        if (isUpdating || currentFile == null || viewModel == null) return;
        try {
            JSONObject state = new JSONObject();
            state.put("method", currentMethod);
            state.put("url", binding.etUrl.getText().toString());

            state.put("headers_obj", getHeadersJson());
            state.put("body_obj", getBodyJson());

            currentFile.setContent(state.toString());
            currentFile.setDirty(true);
            viewModel.notifyFileDirtyStatusChanged();
            viewModel.triggerAutoSave();
        } catch (JSONException ignored) {
        }
    }

    private void loadStateFromVirtualFile(String content) {
        if (content == null || content.isEmpty()) return;
        try {
            isUpdating = true;
            JSONObject state = new JSONObject(content);
            currentMethod = state.optString("method", "GET");
            binding.tvMethod.setText(currentMethod);
            binding.etUrl.setText(state.optString("url", ""));

            binding.containerHeadersList.removeAllViews();

            if (state.has("headers_obj")) {
                JSONObject hObj = state.getJSONObject("headers_obj");
                Iterator<String> keys = hObj.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    addHeaderRow(k, hObj.getString(k));
                }
            } else if (state.has("headers")) {
                String oldHeaders = state.getString("headers");
                try {
                    JSONObject hObj = new JSONObject(oldHeaders);
                    Iterator<String> keys = hObj.keys();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        addHeaderRow(k, hObj.getString(k));
                    }
                } catch (Exception ignored) {
                }
            }

            if (binding.containerHeadersList.getChildCount() == 0) {
                addHeaderRow("", "");
            }

            binding.containerBodyList.removeAllViews();

            if (state.has("body_obj")) {
                JSONObject bObj = state.getJSONObject("body_obj");
                Iterator<String> keys = bObj.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    addBodyRow(k, bObj.getString(k));
                }
            } else if (state.has("body")) {
                String oldBody = state.getString("body");
                try {
                    JSONObject bObj = new JSONObject(oldBody);
                    Iterator<String> keys = bObj.keys();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        addBodyRow(k, bObj.getString(k));
                    }
                } catch (Exception ignored) {
                }
            }

            if (binding.containerBodyList.getChildCount() == 0) {
                addBodyRow("", "");
            }

        } catch (JSONException ignored) {
        } finally {
            isUpdating = false;
        }
    }

    @Override
    public void bindFile(EditorFile file, EditorViewModel viewModel) {
        this.currentFile = file;
        this.viewModel = viewModel;

        binding.containerHeadersList.removeAllViews();
        binding.containerBodyList.removeAllViews();
        loadStateFromVirtualFile(file.getContent());

        if (binding.containerHeadersList.getChildCount() == 0) {
            addHeaderRow("", "");
        }
        if (binding.containerBodyList.getChildCount() == 0) {
            addBodyRow("", "");
        }

        selectTab(true);
    }

    @Override
    public void onResume() {
    }

    @Override
    public void onPause() {
        UiUtils.hideKeyboard((android.app.Activity) context);
    }

    @Override
    public void destroy() {
        binding = null;
        context = null;
        currentFile = null;
        viewModel = null;
    }

    @Override
    public CodeEditText getCodeEditor() {
        return null;
    }
}
