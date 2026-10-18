package dev.floatplus.lsp.appearance;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;

import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;

/** Measures painted pixels, excluding transparent padding and transient press/drag transforms. */
final class BallInkBounds {
    private static final Map<Drawable, CachedInk> CACHE = new WeakHashMap<>();

    private BallInkBounds() {}

    static RectF measure(View root, ImageView inner, ImageView outside) {
        RectF result = new RectF();
        include(root, inner, inner, result);
        include(root, outside, inner, result);
        return result;
    }

    private static void include(View root, ImageView view, ImageView animatedInner, RectF result) {
        if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0
                || view.getVisibility() != View.VISIBLE) return;
        RectF ink = rasterBounds(view.getBackground(), view.getWidth(), view.getHeight());
        includeMapped(root, view, animatedInner, ink, result);
        Drawable image = view.getDrawable();
        if (image != null) {
            int width = image.getBounds().width();
            int height = image.getBounds().height();
            if (width <= 0) width = image.getIntrinsicWidth();
            if (height <= 0) height = image.getIntrinsicHeight();
            ink = rasterBounds(image, width, height);
            if (!ink.isEmpty()) {
                view.getImageMatrix().mapRect(ink);
                ink.offset(view.getPaddingLeft(), view.getPaddingTop());
                includeMapped(root, view, animatedInner, ink, result);
            }
        }
    }

    private static void includeMapped(View root, View view, View animatedInner, RectF ink, RectF result) {
        if (ink.isEmpty()) return;
        View current = view;
        while (current != root) {
            ViewParent parent = current.getParent();
            if (!(parent instanceof View)) return;
            View parentView = (View) parent;
            // ColorOS animates img_big_ball's scale/translation during a gesture. Dock to its
            // layout position at rest; static transforms such as img_bg's 0.8 scale still apply.
            if (current != animatedInner) current.getMatrix().mapRect(ink);
            ink.offset(current.getLeft() - parentView.getScrollX(),
                    current.getTop() - parentView.getScrollY());
            current = parentView;
        }
        if (result.isEmpty()) result.set(ink); else result.union(ink);
    }

    private static RectF rasterBounds(Drawable drawable, int width, int height) {
        if (drawable == null || width <= 0 || height <= 0) return new RectF();
        if (drawable instanceof ColorDrawable
                && Color.alpha(((ColorDrawable) drawable).getColor()) == 0) return new RectF();
        int state = Arrays.hashCode(drawable.getState());
        CachedInk cached = CACHE.get(drawable);
        if (cached != null && cached.width == width && cached.height == height
                && cached.state == state && cached.level == drawable.getLevel()
                && cached.alpha == drawable.getAlpha()) return new RectF(cached.ink);
        if (width > 2048 || height > 2048) throw new IllegalArgumentException("Unexpected ball size");
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Rect oldBounds = new Rect(drawable.getBounds());
        RectF ink;
        try {
            drawable.setBounds(0, 0, width, height);
            drawable.draw(new Canvas(bitmap));
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            int left = width, top = height, right = -1, bottom = -1;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if ((pixels[y * width + x] >>> 24) < 2) continue;
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x);
                    bottom = Math.max(bottom, y);
                }
            }
            ink = right < left ? new RectF() : new RectF(left, top, right + 1, bottom + 1);
        } finally {
            drawable.setBounds(oldBounds);
            bitmap.recycle();
        }
        CACHE.put(drawable, new CachedInk(width, height, state, drawable.getLevel(), drawable.getAlpha(), ink));
        return new RectF(ink);
    }

    private static final class CachedInk {
        final int width, height, state, level, alpha;
        final RectF ink;
        CachedInk(int width, int height, int state, int level, int alpha, RectF ink) {
            this.width = width;
            this.height = height;
            this.state = state;
            this.level = level;
            this.alpha = alpha;
            this.ink = ink;
        }
    }
}
