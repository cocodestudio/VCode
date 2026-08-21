package com.cocode.vcode.ide.ui.editor.viewer;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.data.model.EditorFile;
import com.cocode.vcode.ide.ui.editor.EditorViewModel;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FileUtils;
import com.cocode.vcode.ide.views.CodeEditText;

import java.io.File;
import java.io.FileInputStream;

/**
 * Custom file viewer for inspecting and previewing TTF/OTF font files.
 */
public class FontFileViewer implements IFileViewer {

    private LinearLayout container;
    private TextView tvFontName;
    private EditText etFontPreview;
    private WebView webviewFontPreview;
    private Context context;

    @Override
    public View getView(Context context, ViewGroup parent) {
        if (container == null) {
            this.context = context;

            container = new LinearLayout(context);
            container.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            container.setOrientation(LinearLayout.VERTICAL);
            container.setGravity(Gravity.CENTER);
            container.setPadding(60, 60, 60, 60);
            container.setBackgroundColor(Color.TRANSPARENT);

            tvFontName = new TextView(context);
            tvFontName.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            tvFontName.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
            tvFontName.setTextColor(ContextCompat.getColor(context, R.color.vcode_text_primary));

            etFontPreview = new EditText(context);
            LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            etParams.topMargin = 60;
            etFontPreview.setLayoutParams(etParams);
            etFontPreview.setBackground(null);
            etFontPreview.setGravity(Gravity.CENTER);
            etFontPreview.setHint("Type here to preview font...");
            etFontPreview.setText(R.string.vcode_the_quick_brown_fox_jumps);
            etFontPreview.setTextColor(ContextCompat.getColor(context, R.color.vcode_text_primary));
            etFontPreview.setTextSize(24f);

            webviewFontPreview = new WebView(context);
            LinearLayout.LayoutParams wvParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            wvParams.topMargin = 60;
            webviewFontPreview.setLayoutParams(wvParams);
            webviewFontPreview.setBackgroundColor(Color.TRANSPARENT);

            container.addView(tvFontName);
            container.addView(etFontPreview);
            container.addView(webviewFontPreview);
        }
        return container;
    }

    @Override
    public void bindFile(EditorFile file, EditorViewModel viewModel) {
        if (container == null || file == null || file.getFile() == null) return;

        File f = file.getFile();
        tvFontName.setText(f.getName());

        String ext = FileUtils.getExtension(f.getName()).toLowerCase();
        if (ext.equals("ttf") || ext.equals("otf")) {
            webviewFontPreview.setVisibility(View.GONE);
            etFontPreview.setVisibility(View.VISIBLE);
            try {
                Typeface tf = Typeface.createFromFile(f);
                etFontPreview.setTypeface(tf);
            } catch (Exception e) {
                Toast.makeText(context, R.string.vcode_unable_to_preview_this_font_2, Toast.LENGTH_SHORT).show();
                etFontPreview.setVisibility(View.GONE);
                webviewFontPreview.setVisibility(View.VISIBLE);
                loadWebFontPreview(f, ext);
            }
        }
    }

    private void loadWebFontPreview(File fontFile, String ext) {
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                byte[] bytes = new byte[(int) fontFile.length()];
                try (FileInputStream fis = new FileInputStream(fontFile)) {
                    fis.read(bytes);
                }

                String base64Font = Base64.encodeToString(bytes, Base64.NO_WRAP);
                String format = ext.equals("woff2") ? "woff2" : (ext.equals("eot") ? "embedded-opentype" : "woff");
                String mime = ext.equals("woff2") ? "font/woff2" : "font/woff";

                int colorInt = ContextCompat.getColor(context, R.color.vcode_text_primary);
                String hexColor = String.format("#%06X", (0xFFFFFF & colorInt));

                String html = "<!DOCTYPE html><html><head><style>" +
                        "@font-face { font-family: 'Preview'; src: url(data:" + mime + ";charset=utf-8;base64," + base64Font + ") format('" + format + "'); }" +
                        "body { font-family: 'Preview', sans-serif; color: " + hexColor + "; font-size: 24px; text-align: center; display: flex; align-items: center; justify-content: center; margin: 0; background: transparent; }" +
                        "div { outline: none; border: none; width: 100%; margin-top: 24px; }" +
                        "</style></head><body>" +
                        "<div contenteditable=\"true\" spellcheck=\"false\">The quick brown fox jumps over the lazy dog<br>0123456789</div>" +
                        "</body></html>";

                Handler mainHandler = ExecutorProvider.getInstance().getMainHandler();
                mainHandler.post(() -> {
                    if (webviewFontPreview != null) {
                        webviewFontPreview.getSettings().setJavaScriptEnabled(false);
                        webviewFontPreview.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
                    }
                });
            } catch (Exception e) {
                Handler mainHandler = ExecutorProvider.getInstance().getMainHandler();
                mainHandler.post(() -> {
                    if (context != null)
                        Toast.makeText(context, R.string.vcode_unable_to_preview_this_font, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    public void onResume() {
    }

    @Override
    public void onPause() {
    }

    @Override
    public void destroy() {
        if (webviewFontPreview != null) {
            webviewFontPreview.destroy();
        }
        webviewFontPreview = null;
        etFontPreview = null;
        tvFontName = null;
        container = null;
        context = null;
    }

    @Override
    public CodeEditText getCodeEditor() {
        return null;
    }
}