package com.cocode.vcode.ide.utils;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.core.model.FileType;

/**
 * Utility class for setting file icons and their corresponding colors.
 * Supports both tinted monochrome icons and full-color multi-colored icons
 * (where colorResId is 0 or null, clearing the color filter).
 */
public class FileIconHelper {

    /**
     * Resolves and applies the appropriate icon and color for a file based on its extension.
     *
     * @param imageView The ImageView to set the icon and color on.
     * @param fileName  The name of the file to determine the icon for.
     */
    public static void setFileIconAndColor(ImageView imageView, String fileName) {
        if (imageView == null || fileName == null) {
            return;
        }

        String ext = FileUtils.getExtension(fileName.toLowerCase());
        FileType fileType = FileType.fromExtension(ext);

        imageView.setImageResource(fileType.getIconResId());
        applyFileTypeColor(imageView, fileType);
    }

    /**
     * Binds a custom drawable icon and optional tint color to an ImageView.
     * If colorResId is 0 or null, any active color filter is cleared so that
     * multi-color icons (like Python, React, Vue, or extension icon packs) render
     * with their full original colors.
     *
     * @param imageView  The ImageView to render into.
     * @param drawable   The icon drawable to set.
     * @param colorResId The color resource to tint with, or 0/null to preserve natural colors.
     */
    public static void setCustomIconAndColor(ImageView imageView, Drawable drawable, @Nullable Integer colorResId) {
        if (imageView == null) return;
        imageView.setImageDrawable(drawable);
        if (colorResId == null || colorResId == 0) {
            imageView.clearColorFilter();
        } else {
            imageView.setColorFilter(
                    ContextCompat.getColor(imageView.getContext(), colorResId),
                    PorterDuff.Mode.SRC_IN
            );
        }
    }

    /**
     * Applies the appropriate color for a FileType to an ImageView's current drawable.
     * If the FileType specifies colorResId == 0, clears any active color filter to preserve
     * natural multi-color rendering.
     */
    public static void applyFileTypeColor(ImageView imageView, FileType fileType) {
        if (imageView == null || fileType == null) return;
        int colorResId = fileType.getColorResId();
        if (colorResId == 0) {
            imageView.clearColorFilter();
        } else {
            Context context = imageView.getContext();
            imageView.setColorFilter(
                    ContextCompat.getColor(context, colorResId),
                    PorterDuff.Mode.SRC_IN
            );
        }
    }
}
