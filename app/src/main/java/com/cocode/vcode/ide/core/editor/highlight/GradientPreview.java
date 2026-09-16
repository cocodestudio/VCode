package com.cocode.vcode.ide.core.editor.highlight;

import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;

/**
 * Immutable representation of a CSS gradient color preview for drawing inside the code editor.
 * Supports linear, radial, and conic (sweep) gradients with directional angles, stop colors, and positions.
 */
public final class GradientPreview {

    public static final int TYPE_LINEAR = 0;
    public static final int TYPE_RADIAL = 1;
    public static final int TYPE_CONIC = 2;

    public final int type;
    public final int[] colors;
    public final float[] positions;
    public final float angleDegrees;
    public final boolean repeating;

    public GradientPreview(int type, int[] colors, float[] positions, float angleDegrees, boolean repeating) {
        this.type = type;
        this.colors = (colors != null && colors.length >= 2)
                ? colors
                : (colors != null && colors.length == 1
                ? new int[]{colors[0], colors[0]}
                : new int[]{0xFF000000, 0xFFFFFFFF});
        this.positions = positions;
        this.angleDegrees = angleDegrees;
        this.repeating = repeating;
    }

    /**
     * Creates an Android {@link Shader} tailored for a preview circle with center (cx, cy) and radius.
     */
    public Shader createShader(float cx, float cy, float radius) {
        Shader.TileMode tileMode = repeating ? Shader.TileMode.REPEAT : Shader.TileMode.CLAMP;

        switch (type) {
            case TYPE_RADIAL:
                return new RadialGradient(cx, cy, Math.max(1f, radius), colors, positions, tileMode);

            case TYPE_CONIC:
                SweepGradient sweep = new SweepGradient(cx, cy, colors, positions);
                if (angleDegrees != 0f) {
                    Matrix matrix = new Matrix();
                    matrix.setRotate(angleDegrees, cx, cy);
                    sweep.setLocalMatrix(matrix);
                }
                return sweep;

            case TYPE_LINEAR:
            default:
                // In CSS gradients:
                // 0deg = to top, 90deg = to right, 180deg = to bottom (default), 270deg = to left
                double radians = Math.toRadians(angleDegrees);
                float dx = (float) (radius * Math.sin(radians));
                float dy = (float) (-radius * Math.cos(radians));

                float x0 = cx - dx;
                float y0 = cy - dy;
                float x1 = cx + dx;
                float y1 = cy + dy;

                return new LinearGradient(x0, y0, x1, y1, colors, positions, tileMode);
        }
    }
}
