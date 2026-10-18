import dev.floatplus.lsp.appearance.DockingGeometry;

/** Standalone regression checks; run with the project JDK, without Android or network dependencies. */
public final class DockingGeometryTest {
    public static void main(String[] args) {
        int checks = 0;
        for (int[] screen : new int[][]{{1272, 2772}, {2772, 1272}, {1080, 2400}}) {
            for (int percent = 70; percent <= 200; percent++) {
                int window = Math.round(214 * percent / 100f);
                for (int skin = 0; skin < 3; skin++) {
                    float body = 120 * percent / 100f;
                    float inset = skin == 2 ? 0 : body * 0.01f;
                    for (float offset : new float[]{0, window * 0.043f}) {
                        // Include asymmetric layout padding and fractional static transforms.
                        float left = (window - body) / 2f + inset + offset;
                        float top = (window - body) / 2f + inset;
                        float right = left + body - 2 * inset;
                        float bottom = top + body - 2 * inset;
                        DockingGeometry geometry = new DockingGeometry(0, 96, screen[0], screen[1] - 80,
                                left, top, right, bottom);
                        int nativeLeft = Math.round(geometry.minX);
                        int nativeRight = Math.round(geometry.maxX + window) - window;
                        int nativeTop = Math.round(geometry.minY);
                        int nativeBottom = Math.round(geometry.maxY + window) - window;
                        close(nativeLeft + left, 0);
                        close(nativeRight + right, screen[0]);
                        close(nativeTop + top, 96);
                        close(nativeBottom + bottom, screen[1] - 80);
                        if (geometry.clampX(nativeLeft - 100) != nativeLeft
                                || geometry.clampX(nativeRight + 100) != nativeRight) {
                            throw new AssertionError("Horizontal clipping at " + percent + "%");
                        }
                        if (geometry.clampY(nativeTop - 100) != nativeTop
                                || geometry.clampY(nativeBottom + 100) != nativeBottom) {
                            throw new AssertionError("Vertical clipping at " + percent + "%");
                        }
                        int interior = (nativeLeft + nativeRight) / 2;
                        if (geometry.clampX(interior) != interior) throw new AssertionError("Dragging constrained");
                        checks += 4;
                    }
                }
            }
        }
        // The former left gap/right crop: a 428px window with an unscaled 214px drag body.
        DockingGeometry regression = new DockingGeometry(0, 0, 1272, 2772, 84, 84, 344, 344);
        close(Math.round(regression.minX) + 84, 0);
        close(Math.round(regression.maxX + 428) - 428 + 344, 1272);
        if (Math.abs(Math.round(regression.maxX + 428) - 214 + 344 - 1272) < 1) {
            throw new AssertionError("Regression case failed to expose the unscaled OEM body");
        }
        System.out.println("PASS: " + checks + " edge cases across 70%-200%, three icon geometries,"
                + " portrait/landscape, and transparent/asymmetric margins.");
    }

    private static void close(float actual, float expected) {
        if (Math.abs(actual - expected) > 0.501f) {
            throw new AssertionError("Visible edge at " + actual + ", expected " + expected);
        }
    }
}
