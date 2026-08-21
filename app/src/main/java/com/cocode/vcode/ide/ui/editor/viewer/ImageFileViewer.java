package com.cocode.vcode.ide.ui.editor.viewer;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.bumptech.glide.Glide;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.data.model.EditorFile;
import com.cocode.vcode.ide.ui.editor.EditorViewModel;
import com.cocode.vcode.ide.views.CodeEditText;
import com.cocode.vcode.ide.views.ZoomImageView;

/**
 * File viewer tab component for displaying raster and vector images.
 */
public class ImageFileViewer implements IFileViewer {

    private ZoomImageView imageView;
    private Context context;

    @Override
    public View getView(Context context, ViewGroup parent) {
        if (imageView == null) {
            this.context = context;
            imageView = new ZoomImageView(context);
            imageView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            imageView.setBackgroundColor(Color.TRANSPARENT);
        }
        return imageView;
    }

    @Override
    public void bindFile(EditorFile file, EditorViewModel viewModel) {
        if (imageView == null || file == null || file.getFile() == null) return;

        FileType type = file.getFileType();
        if (type == FileType.GIF) {
            Glide.with(context).asGif().load(file.getFile()).into(imageView);
        } else {
            imageView.setImageURI(Uri.fromFile(file.getFile()));
        }
    }

    @Override
    public void onResume() {
    }

    @Override
    public void onPause() {
    }

    @Override
    public void destroy() {
        if (imageView != null && context != null) {
            Glide.with(context.getApplicationContext()).clear(imageView);
        }
        imageView = null;
        context = null;
    }

    @Override
    public CodeEditText getCodeEditor() {
        return null;
    }
}
