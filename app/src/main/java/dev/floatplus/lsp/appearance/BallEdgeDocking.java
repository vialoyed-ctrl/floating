package dev.floatplus.lsp.appearance;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/** Adapts ColorOS's existing spring bounds to the ball's painted outline and scaled window. */
public final class BallEdgeDocking {
    private static final String TARGET = "com.coloros.floatassistant";
    private static final String TAG = "FloatBallPlus";
    private static final Map<View, DockState> STATES = new WeakHashMap<>();
    private static final Map<Object, WeakReference<View>> ROOTS = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> OEM_BOUNDS = ThreadLocal.withInitial(() -> false);
    private static Class<?> managerClass;
    private static boolean errorLogged;

    private BallEdgeDocking() {}

    public static void install(ClassLoader loader) {
        try {
            managerClass = XposedHelpers.findClass("q2.e", loader);
            Class<?> listener = XposedHelpers.findClass("c9.a", loader);
            Class<?> updateListener = XposedHelpers.findClass("c9.b", loader);
            XposedHelpers.findAndHookMethod(managerClass, "m", Context.class, Rect.class,
                    listener, updateListener, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    View root = param.args[2] instanceof View ? (View) param.args[2] : null;
                    if (!isBall(root)) {
                        ROOTS.remove(param.thisObject);
                        return;
                    }
                    if (!STATES.containsKey(root)) onAppearanceChanged(root);
                    // Cancel any queued initial alignment when a user begins moving the ball.
                    // The native spring now owns its position until the steady callback.
                    STATES.get(root).generation++;
                    ROOTS.put(param.thisObject, new WeakReference<>(root));
                    Rect position = (Rect) param.args[1];
                    // Both OEM modes rebuild this rectangle from the fixed, unscaled G()*2.
                    // The spring must receive the real window size, including touch padding.
                    if (root.getWidth() > 0 && root.getHeight() > 0) {
                        position.right = position.left + root.getWidth();
                        position.bottom = position.top + root.getHeight();
                    }
                }
            });
            XposedHelpers.findAndHookMethod(managerClass, "j", Context.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable() || OEM_BOUNDS.get()) return;
                    WeakReference<View> reference = ROOTS.get(param.thisObject);
                    View root = reference == null ? null : reference.get();
                    if (root == null || !root.isAttachedToWindow()) return;
                    try {
                        Measurement measured = measure(root, (Rect) param.getResult());
                        if (measured == null) return;
                        DockingGeometry limits = measured.limits;
                        // The OEM spring subtracts its body width/height from right/bottom.
                        param.setResult(new Rect(Math.round(limits.minX), Math.round(limits.minY),
                                Math.round(limits.maxX + root.getWidth()),
                                Math.round(limits.maxY + root.getHeight())));
                        Log.i(TAG, "Dock bounds: window=" + root.getWidth() + "x" + root.getHeight()
                                + " painted=" + measured.ink + " origins=" + limits.minX + ".." + limits.maxX);
                    } catch (Throwable error) { report(error); }
                }
            });
            Log.i(TAG, "Hooked ColorOS docking bounds for painted ball edges");
        } catch (Throwable error) { report(error); }

        hookSteady(loader, "t2.a", "D");
        hookSteady(loader, "x2.b", "G");
        try {
            XposedHelpers.findAndHookMethod(ViewGroup.class, "dispatchTouchEvent", MotionEvent.class,
                    new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    View root = (View) param.thisObject;
                    if (!isBallClass(root)) return;
                    DockState state = STATES.get(root);
                    if (state == null) return;
                    int action = ((MotionEvent) param.args[0]).getActionMasked();
                    if (action == MotionEvent.ACTION_DOWN) {
                        state.touching = true;
                        state.generation++;
                    }
                    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) state.touching = false;
                }
            });
        } catch (Throwable error) { report(error); }
    }

    private static void hookSteady(ClassLoader loader, String type, String method) {
        try {
            XposedHelpers.findAndHookMethod(type, loader, method, float.class, float.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable()) return;
                    View root = (View) param.thisObject;
                    DockState state = STATES.get(root);
                    if (state == null) return;
                    try {
                        Object manager = XposedHelpers.callStaticMethod(managerClass, "i");
                        Object side = XposedHelpers.callMethod(manager, "h");
                        if (side instanceof Number) {
                            int bit = ((Number) side).intValue();
                            state.edge = bit == 1 ? 1 : bit == 2 ? 2 : bit == 4 ? 3 : bit == 8 ? 4 : 0;
                        }
                        align(root);
                    } catch (Throwable error) { report(error); }
                }
            });
        } catch (Throwable error) { report(error); }
    }

    public static void onAppearanceChanged(View root) {
        if (!isBall(root)) return;
        DockState state = STATES.get(root);
        if (state == null) {
            state = new DockState();
            STATES.put(root, state);
            View.OnLayoutChangeListener changed = (view, l, t, r, b, oldL, oldT, oldR, oldB) -> schedule(root);
            root.addOnLayoutChangeListener(changed);
            ballImage(root, "img_big_ball").addOnLayoutChangeListener(changed);
        }
        schedule(root);
    }

    private static void schedule(View root) {
        DockState state = STATES.get(root);
        if (state == null || state.scheduled) return;
        state.scheduled = true;
        int generation = state.generation;
        root.post(() -> {
            state.scheduled = false;
            if (!state.touching && state.generation == generation) align(root);
        });
    }

    private static void align(View root) {
        DockState state = STATES.get(root);
        if (state == null || state.touching || state.applying || !root.isAttachedToWindow()
                || !(root.getLayoutParams() instanceof WindowManager.LayoutParams)) return;
        try {
            Measurement measured = measure(root, originalLimits(root));
            if (measured == null) return;
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) root.getLayoutParams();
            Object config = nativeConfig(root);
            if (state.edge == 0) {
                state.edge = ((Number) XposedHelpers.callMethod(config, "k")).intValue();
                if (state.edge < 1 || state.edge > 4) {
                    state.edge = Math.abs(params.x - measured.limits.minX)
                            < Math.abs(params.x - measured.limits.maxX) ? 1 : 2;
                }
            }
            DockingGeometry limits = measured.limits;
            int x = limits.clampX(params.x);
            int y = limits.clampY(params.y);
            if (state.edge == 1) x = Math.round(limits.minX);
            if (state.edge == 2) x = Math.round(limits.maxX);
            if (state.edge == 3) y = Math.round(limits.minY);
            if (state.edge == 4) y = Math.round(limits.maxY);
            if (x == params.x && y == params.y) return;
            state.applying = true;
            try {
                params.x = x;
                params.y = y;
                boolean gesture = "t2.a".equals(root.getClass().getName());
                Rect position = (Rect) XposedHelpers.getObjectField(root, gesture ? "L" : "x");
                position.set(x, y, x + root.getWidth(), y + root.getHeight());
                XposedHelpers.setIntField(root, gesture ? "o" : "g", x);
                XposedHelpers.setIntField(root, gesture ? "p" : "k", y);
                ((WindowManager) root.getContext().getSystemService(Context.WINDOW_SERVICE))
                        .updateViewLayout(root, params);
                XposedHelpers.callMethod(config, "e0", x, y);
                Log.i(TAG, "Ball edge aligned: side=" + state.edge + " origin=" + x + "," + y
                        + " painted=" + measured.ink);
            } finally { state.applying = false; }
        } catch (Throwable error) { report(error); }
    }

    private static Measurement measure(View root, Rect nativeBounds) {
        if (root.getWidth() <= 0 || root.getHeight() <= 0) return null;
        RectF ink = BallInkBounds.measure(root, ballImage(root, "img_big_ball"), ballImage(root, "img_bg"));
        if (ink.isEmpty()) return null;
        Point display = new Point();
        if (root.getDisplay() == null) return null;
        root.getDisplay().getRealSize(display);
        // Keep native top/bottom safe areas, but remove fixed icon-dependent left/right offsets.
        int top = nativeBounds == null ? 0 : Math.max(0, nativeBounds.top);
        int bottom = nativeBounds == null ? display.y : Math.min(display.y, nativeBounds.bottom);
        return new Measurement(ink, new DockingGeometry(0, top, display.x, bottom,
                ink.left, ink.top, ink.right, ink.bottom));
    }

    private static Rect originalLimits(View root) {
        if (managerClass == null) return null;
        OEM_BOUNDS.set(true);
        try {
            Object manager = XposedHelpers.callStaticMethod(managerClass, "i");
            return (Rect) XposedHelpers.callMethod(manager, "j", root.getContext());
        } finally { OEM_BOUNDS.remove(); }
    }

    private static Object nativeConfig(View root) {
        Class<?> type = XposedHelpers.findClass(TARGET + ".c", root.getClass().getClassLoader());
        return XposedHelpers.callStaticMethod(type, "s", root.getContext());
    }

    private static boolean isBallClass(View root) {
        if (root == null) return false;
        String type = root.getClass().getName();
        return "t2.a".equals(type) || "x2.b".equals(type);
    }

    private static boolean isBall(View root) {
        return isBallClass(root) && ballImage(root, "img_bg") != null && ballImage(root, "img_big_ball") != null;
    }

    private static ImageView ballImage(View root, String name) {
        int id = root.getResources().getIdentifier(name, "id", TARGET);
        View view = id == 0 ? null : root.findViewById(id);
        return view instanceof ImageView ? (ImageView) view : null;
    }

    private static void report(Throwable error) {
        if (errorLogged) return;
        errorLogged = true;
        Log.e(TAG, "Could not adapt floating-ball edge geometry", error);
    }

    private static final class DockState {
        int edge;
        int generation;
        boolean touching, scheduled, applying;
    }

    private static final class Measurement {
        final RectF ink;
        final DockingGeometry limits;
        Measurement(RectF ink, DockingGeometry limits) { this.ink = ink; this.limits = limits; }
    }
}
