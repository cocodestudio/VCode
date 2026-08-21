package com.cocode.vcode.ide.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * ImageView supporting pinch-to-zoom, panning, and double-tap zoom gestures.
 * Handles matrix transformations, viewport boundary clamping, and aspect-ratio preservation.
 */
public class ZoomImageView extends AppCompatImageView {

    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;

    private final PointF last = new PointF();
    private final PointF start = new PointF();
    private final float maxScale = 5f;
    private Matrix matrix;
    private int mode = NONE;
    private float[] m;

    private int viewWidth, viewHeight;
    private float saveScale = 1f;
    private float origWidth, origHeight;

    private ScaleGestureDetector mScaleDetector;
    private GestureDetector mGestureDetector;

    public ZoomImageView(Context context) {
        super(context);
        sharedConstructing(context);
    }

    public ZoomImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        sharedConstructing(context);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void sharedConstructing(Context context) {
        super.setClickable(true);
        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        mGestureDetector = new GestureDetector(context, new GestureListener());
        matrix = new Matrix();
        m = new float[9];
        setImageMatrix(matrix);
        setScaleType(ScaleType.MATRIX);

        setOnTouchListener((v, event) -> {
            mScaleDetector.onTouchEvent(event);
            mGestureDetector.onTouchEvent(event);

            PointF curr = new PointF(event.getX(), event.getY());

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    last.set(curr);
                    start.set(last);
                    mode = DRAG;
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (mode == DRAG) {
                        float deltaX = curr.x - last.x;
                        float deltaY = curr.y - last.y;

                        float fixTransX = getFixDragTrans(deltaX, viewWidth, origWidth * saveScale);
                        float fixTransY = getFixDragTrans(deltaY, viewHeight, origHeight * saveScale);

                        matrix.postTranslate(fixTransX, fixTransY);
                        fixTrans();
                        last.set(curr.x, curr.y);
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    mode = NONE;
                    break;
            }

            setImageMatrix(matrix);
            invalidate();
            return true;
        });
    }

    @Override
    public void setImageURI(Uri uri) {
        super.setImageURI(uri);
        resetZoomState();
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        resetZoomState();
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        super.setImageBitmap(bm);
        resetZoomState();
    }

    private void resetZoomState() {
        saveScale = 1f;
        if (matrix != null) {
            matrix.reset();
            setImageMatrix(matrix);
        }
        requestLayout();
    }

    private void fixTrans() {
        matrix.getValues(m);
        float transX = m[Matrix.MTRANS_X];
        float transY = m[Matrix.MTRANS_Y];

        float fixTransX = getFixTrans(transX, viewWidth, origWidth * saveScale);
        float fixTransY = getFixTrans(transY, viewHeight, origHeight * saveScale);

        if (fixTransX != 0 || fixTransY != 0) {
            matrix.postTranslate(fixTransX, fixTransY);
        }
    }

    private float getFixTrans(float trans, float viewSize, float contentSize) {
        float minTrans, maxTrans;

        if (contentSize <= viewSize) {
            minTrans = 0;
            maxTrans = viewSize - contentSize;
        } else {
            minTrans = viewSize - contentSize;
            maxTrans = 0;
        }

        if (trans < minTrans) return -trans + minTrans;
        if (trans > maxTrans) return -trans + maxTrans;
        return 0;
    }

    private float getFixDragTrans(float delta, float viewSize, float contentSize) {
        if (contentSize <= viewSize) return 0;
        return delta;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        viewWidth = MeasureSpec.getSize(widthMeasureSpec);
        viewHeight = MeasureSpec.getSize(heightMeasureSpec);

        if (saveScale == 1f && viewWidth > 0 && viewHeight > 0) {
            Drawable drawable = getDrawable();
            if (drawable == null || drawable.getIntrinsicWidth() == 0 || drawable.getIntrinsicHeight() == 0)
                return;

            int bmWidth = drawable.getIntrinsicWidth();
            int bmHeight = drawable.getIntrinsicHeight();

            float scaleX = (float) viewWidth / (float) bmWidth;
            float scaleY = (float) viewHeight / (float) bmHeight;
            float scale = Math.min(scaleX, scaleY);

            matrix.setScale(scale, scale);

            float redundantYSpace = (float) viewHeight - (scale * (float) bmHeight);
            float redundantXSpace = (float) viewWidth - (scale * (float) bmWidth);

            redundantYSpace /= 2f;
            redundantXSpace /= 2f;

            matrix.postTranslate(redundantXSpace, redundantYSpace);

            origWidth = viewWidth - 2 * redundantXSpace;
            origHeight = viewHeight - 2 * redundantYSpace;
            setImageMatrix(matrix);
        }
        fixTrans();
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            float targetScale = (saveScale == 1f) ? maxScale : 1f;
            float scaleFactor = targetScale / saveScale;

            matrix.postScale(scaleFactor, scaleFactor, e.getX(), e.getY());
            saveScale = targetScale;
            fixTrans();
            setImageMatrix(matrix);
            invalidate();
            return true;
        }
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
            mode = ZOOM;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float mScaleFactor = detector.getScaleFactor();
            float origScale = saveScale;
            saveScale *= mScaleFactor;
            float minScale = 1f;

            if (saveScale > maxScale) {
                saveScale = maxScale;
                mScaleFactor = maxScale / origScale;
            } else if (saveScale < minScale) {
                saveScale = minScale;
                mScaleFactor = minScale / origScale;
            }

            if (origWidth * saveScale <= viewWidth || origHeight * saveScale <= viewHeight) {
                matrix.postScale(mScaleFactor, mScaleFactor, viewWidth / 2f, viewHeight / 2f);
            } else {
                matrix.postScale(mScaleFactor, mScaleFactor, detector.getFocusX(), detector.getFocusY());
            }

            fixTrans();
            return true;
        }
    }
}