package dev.floatplus.lsp.appearance;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** One complete AssistiveTouch face, so OEM view translations cannot separate its rings. */
public final class AssistiveTouchDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF face = new RectF();
    private final boolean inverted;
    private int alpha = 255;
    private float cx;
    private float cy;
    private float radius;
    private float centerRadius;
    private Shader outerGradient;
    private Shader ringGradient;
    private Shader centerGradient;
    private Shader centerShadow;

    public AssistiveTouchDrawable(int opacityPercent, boolean inverted) {
        this.inverted = inverted;
        setAlpha(Math.round(255 * Math.max(15, Math.min(100, opacityPercent)) / 100f));
    }

    @Override protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        float side = Math.min(bounds.width(), bounds.height());
        cx = bounds.exactCenterX();
        cy = bounds.exactCenterY();
        radius = side * 0.49f;
        centerRadius = radius * (inverted ? 0.43f : 0.48f);
        face.set(cx - radius, cy - radius, cx + radius, cy + radius);
        if (radius <= 0 || inverted) return;
        // Cache shaders at the rendered size; OEM animation frames only redraw the same face.
        outerGradient = new LinearGradient(cx, cy - radius * 0.15f,
                cx + radius * 0.9f, cy + radius,
                Color.rgb(39, 48, 58), Color.rgb(29, 36, 43), Shader.TileMode.CLAMP);
        ringGradient = new LinearGradient(cx, cy - radius * 0.78f,
                cx, cy + radius * 0.78f,
                Color.rgb(93, 100, 107), Color.rgb(76, 83, 91), Shader.TileMode.CLAMP);
        centerGradient = new LinearGradient(cx, cy - centerRadius, cx, cy + centerRadius,
                Color.WHITE, Color.rgb(248, 250, 252), Shader.TileMode.CLAMP);
        centerShadow = new RadialGradient(cx + radius * 0.018f, cy + radius * 0.026f,
                centerRadius + radius * 0.036f,
                new int[]{Color.argb(70, 31, 46, 57), Color.TRANSPARENT},
                new float[]{0.80f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override public void draw(Canvas canvas) {
        if (radius <= 0 || alpha == 0) return;
        int saved = canvas.saveLayerAlpha(face, alpha);
        if (inverted) {
            // Light reference: #EBEBEB / #A8A8A8 / #5E5E5E / #222222.
            fillCircle(canvas, radius, Color.rgb(235, 235, 235));
            strokeCircle(canvas, radius * 0.991f, radius * 0.018f, Color.rgb(94, 94, 94));
            fillCircle(canvas, radius * 0.76f, Color.rgb(168, 168, 168));
            fillCircle(canvas, radius * 0.62f, Color.rgb(94, 94, 94));
            fillCircle(canvas, centerRadius, Color.rgb(34, 34, 34));
        } else {
            // Dark reference: blue-gray outer disc, gray bevel and a larger white center.
            gradientCircle(canvas, cx, cy, radius, outerGradient);
            gradientCircle(canvas, cx, cy, radius * 0.78f, ringGradient);
            strokeCircle(canvas, radius * 0.775f, radius * 0.012f, Color.rgb(67, 78, 87));
            fillCircle(canvas, radius * 0.62f, Color.rgb(174, 178, 181));
            strokeCircle(canvas, radius * 0.62f, radius * 0.012f, Color.rgb(65, 81, 92));
            gradientCircle(canvas, cx + radius * 0.018f, cy + radius * 0.026f,
                    centerRadius + radius * 0.036f, centerShadow);
            gradientCircle(canvas, cx, cy, centerRadius, centerGradient);
            strokeCircle(canvas, centerRadius, radius * 0.014f, Color.rgb(101, 126, 139));
        }
        paint.setShader(null);
        canvas.restoreToCount(saved);
    }

    private void fillCircle(Canvas canvas, float circleRadius, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawCircle(cx, cy, circleRadius, paint);
    }

    private void gradientCircle(Canvas canvas, float x, float y, float circleRadius, Shader shader) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setShader(shader);
        canvas.drawCircle(x, y, circleRadius, paint);
    }

    private void strokeCircle(Canvas canvas, float circleRadius, float width, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setColor(color);
        canvas.drawCircle(cx, cy, circleRadius, paint);
    }

    @Override public void setAlpha(int value) {
        alpha = Math.max(0, Math.min(255, value));
        invalidateSelf();
    }

    @Override public int getAlpha() { return alpha; }

    @Override public void setColorFilter(ColorFilter filter) {
        paint.setColorFilter(filter);
        invalidateSelf();
    }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
