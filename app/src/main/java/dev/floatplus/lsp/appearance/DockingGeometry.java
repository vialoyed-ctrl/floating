package dev.floatplus.lsp.appearance;

/** Window-origin limits that keep a drawable's visible bounds tangent to the usable screen. */
public final class DockingGeometry {
    public final float minX;
    public final float minY;
    public final float maxX;
    public final float maxY;

    public DockingGeometry(float screenLeft, float screenTop, float screenRight, float screenBottom,
            float inkLeft, float inkTop, float inkRight, float inkBottom) {
        if (!(screenRight > screenLeft && screenBottom > screenTop
                && inkRight > inkLeft && inkBottom > inkTop)) {
            throw new IllegalArgumentException("Empty screen or ball bounds");
        }
        minX = screenLeft - inkLeft;
        minY = screenTop - inkTop;
        maxX = screenRight - inkRight;
        maxY = screenBottom - inkBottom;
        if (maxX < minX || maxY < minY) throw new IllegalArgumentException("Ball exceeds screen");
    }

    public int clampX(int x) { return Math.round(Math.max(minX, Math.min(maxX, x))); }
    public int clampY(int y) { return Math.round(Math.max(minY, Math.min(maxY, y))); }
}
