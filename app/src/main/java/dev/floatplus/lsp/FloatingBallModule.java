package dev.floatplus.lsp;

import dev.floatplus.lsp.config.ConfigStore;
import dev.floatplus.lsp.appearance.AssistiveTouchDrawable;
import dev.floatplus.lsp.appearance.BallEdgeDocking;

import android.app.Application;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** LSPosed entry point for the OnePlus ColorOS floating-ball process. */
public final class FloatingBallModule implements IXposedHookLoadPackage {
    private static final String TARGET_PACKAGE = "com.coloros.floatassistant";
    private static final String TAG = "FloatBallPlus";
    private static final String MENU_VIEW_TAG = "dev.floatplus.lsp.menu_apps_entry";
    private static final ConcurrentHashMap<String, String> CONFIG = new ConcurrentHashMap<>();
    private static final Map<View, Drawable> ORIGINAL_BACKGROUNDS = new WeakHashMap<>();
    private static final Map<ImageView, Drawable> ORIGINAL_IMAGE_DRAWABLES = new WeakHashMap<>();
    private static final Map<WindowManager.LayoutParams, int[]> ORIGINAL_WINDOW_SIZES = new WeakHashMap<>();
    private static volatile boolean providerUnavailableLogged;
    private static volatile boolean configLoaded;
    private static final ThreadLocal<Boolean> APPLYING_BALL_STYLE = ThreadLocal.withInitial(() -> false);
    private static volatile ContentProviderClient configProviderClient;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) return;
        log("Target package loaded in process " + lpparam.processName, null);

        try {
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    readConfig((Context) param.args[0]);
                }
            });
        } catch (Throwable t) {
            log("Could not hook Application.attach", t);
        }

        hookGestureActionDispatch(lpparam.classLoader);
        hookNativeMenuBuilder(lpparam.classLoader);
        hookBallWindowAndStyle(lpparam.classLoader);
        hookBallLayoutInflation();
        hookBallDrawableUpdates();
        BallEdgeDocking.install(lpparam.classLoader);
    }

    /**
     * ColorOS's own gesture detector calls FloatWindowService.a(action). Intercepting here
     * keeps its single/double/long recognition, timing and drag handling intact.
     */
    private static void hookGestureActionDispatch(ClassLoader loader) {
        try {
            XposedHelpers.findAndHookMethod("com.coloros.floatassistant.FloatWindowService", loader,
                    "a", String.class, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            String action = (String) param.args[0];
                            String key = gestureKey(action);
                            if (key == null) return;

                            Context context = (Context) param.thisObject;
                            readConfig(context);
                            String component = CONFIG.get(key);
                            if (component == null || component.isEmpty()) return;

                            if (launchComponent(context, component)) {
                                param.setResult(null);
                            }
                        }
                    });
        } catch (Throwable t) {
            log("Could not hook ColorOS gesture action callback", t);
        }
    }

    private static String gestureKey(String action) {
        if ("single_click".equals(action)) return "gesture_single";
        if ("double_click".equals(action)) return "gesture_double";
        if ("long_press".equals(action)) return "gesture_long";
        return null;
    }

    /** Scale the OEM window and its inner ball while keeping the original views and animators. */
    private static void hookBallWindowAndStyle(ClassLoader loader) {
        try {
            XposedHelpers.findAndHookMethod("android.view.WindowManagerImpl", loader, "addView",
                    View.class, ViewGroup.LayoutParams.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    View root = (View) param.args[0];
                    if (root == null || !(param.args[1] instanceof WindowManager.LayoutParams)) return;
                    Context context = root.getContext();
                    if (findBallImage(root, context, "img_bg") == null
                            || findBallImage(root, context, "img_big_ball") == null) return;
                    readConfig(context);
                    float scale = getPercent(ConfigStore.BALL_SIZE_PERCENT, ConfigStore.DEFAULT_SIZE_PERCENT) / 100f;
                    WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) param.args[1];
                    int[] originalSize;
                    synchronized (ORIGINAL_WINDOW_SIZES) {
                        originalSize = ORIGINAL_WINDOW_SIZES.get(layoutParams);
                        if (originalSize == null) {
                            originalSize = new int[]{layoutParams.width, layoutParams.height};
                            ORIGINAL_WINDOW_SIZES.put(layoutParams, originalSize);
                        }
                    }
                    layoutParams.width = scaleDimension(originalSize[0], scale);
                    layoutParams.height = scaleDimension(originalSize[1], scale);
                    log("Ball window addView params " + originalSize[0] + "x" + originalSize[1] + " -> "
                            + layoutParams.width + "x" + layoutParams.height + " ("
                            + CONFIG.get(ConfigStore.BALL_SIZE_PERCENT) + "%), touch target scaled", null);
                }
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable()) return;
                    View root = (View) param.args[0];
                    if (root == null || !(param.args[1] instanceof WindowManager.LayoutParams)
                            || findBallImage(root, root.getContext(), "img_big_ball") == null) return;
                    WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) param.args[1];
                    Context context = root.getContext();
                    root.postDelayed(() -> retryBallWindowConfig(root, context, layoutParams, 0), 50);
                }
            });
        } catch (Throwable t) {
            log("Could not hook floating-ball WindowManager.addView", t);
        }
        try {
            XposedHelpers.findAndHookConstructor("x2.b", loader, Context.class, WindowManager.LayoutParams.class,
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            Context context = (Context) param.args[0];
                            View root = (View) param.thisObject;
                            applyBallAppearance(root, context, "view constructor");
                        }
                    });
        } catch (Throwable t) {
            log("Could not hook floating-ball view creation", t);
        }
    }    /**
     * The OEM service recreates the ball by inflating layout_ball. Hooking that resource gives
     * us a lifecycle point tied to the actual rendered view, even when its obfuscated wrapper
     * constructor differs between ColorOS builds.
     */
    private static void hookBallLayoutInflation() {
        try {
            XposedHelpers.findAndHookMethod(LayoutInflater.class, "inflate", int.class,
                    ViewGroup.class, boolean.class, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            View root = (View) param.getResult();
                            if (root == null) return;
                            Context context = root.getContext();
                            int ballLayoutId = context.getResources().getIdentifier(
                                    "layout_ball", "layout", TARGET_PACKAGE);
                            if (ballLayoutId == 0 || ((Integer) param.args[0]) != ballLayoutId) return;

                            // Size before the first layout, when ColorOS caches the ball's rest position.
                            applyBallAppearance(root, context, "layout_ball inflation");
                        }
                    });
            log("Hooked layout_ball inflation", null);
        } catch (Throwable t) {
            log("Could not hook floating-ball layout inflation", t);
        }
    }

    /** Protect the custom drawables while OEM touch, scale and alpha animations keep running. */
    private static void hookBallDrawableUpdates() {
        try {
            XposedHelpers.findAndHookMethod(ImageView.class, "setImageResource", int.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    ImageView image = (ImageView) param.thisObject;
                    int resourceId = (Integer) param.args[0];
                    if (resourceId != 0 && shouldSuppressBallImageSource(image)) {
                        log("Suppressed ColorOS ball image resource 0x" + Integer.toHexString(resourceId), null);
                        param.setResult(null);
                    }
                }
            });
            XposedHelpers.findAndHookMethod(ImageView.class, "setImageDrawable", Drawable.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    ImageView image = (ImageView) param.thisObject;
                    if (param.args[0] != null && shouldSuppressBallImageSource(image)) {
                        log("Suppressed ColorOS ball foreground drawable update", null);
                        param.setResult(null);
                    }
                }
            });
            XposedHelpers.findAndHookMethod(View.class, "setBackground", Drawable.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    View view = (View) param.thisObject;
                    if (!APPLYING_BALL_STYLE.get() && param.args[0] != null
                            && shouldSuppressBallAppearanceUpdate(view)) {
                        log("Suppressed ColorOS ball background drawable update", null);
                        param.setResult(null);
                    }
                }
            });
            XposedHelpers.findAndHookMethod(View.class, "setBackgroundResource", int.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    View view = (View) param.thisObject;
                    int resourceId = (Integer) param.args[0];
                    // t2.a restores ball_icon_outside on touch down. That vector contains the X.
                    if (!APPLYING_BALL_STYLE.get() && resourceId != 0
                            && shouldSuppressBallAppearanceUpdate(view)) {
                        log("Suppressed ColorOS ball background resource 0x"
                                + Integer.toHexString(resourceId) + " on "
                                + view.getResources().getResourceEntryName(view.getId()), null);
                        param.setResult(null);
                    }
                }
            });
            log("Hooked ColorOS ball image and background updates (0.2.14)", null);
        } catch (Throwable t) {
            log("Could not hook ColorOS ball drawable updates", t);
        }
    }

    private static boolean shouldSuppressBallImageSource(ImageView image) {
        return image != null && shouldSuppressBallAppearanceUpdate(image);
    }

    private static boolean shouldSuppressBallAppearanceUpdate(View view) {
        if (view == null || !ConfigStore.isIosStyle(
                CONFIG.getOrDefault(ConfigStore.BALL_STYLE, ConfigStore.STYLE_IOS))) return false;
        // Only protect the two ball views after their OEM backgrounds have been saved.
        // Initial inflation must run normally so switching back to the OEM style can restore it.
        synchronized (ORIGINAL_BACKGROUNDS) {
            return ORIGINAL_BACKGROUNDS.containsKey(view);
        }
    }
    private static void retryBallWindowConfig(View root, Context context,
            WindowManager.LayoutParams layoutParams, int attempt) {
        if (root == null || context == null || layoutParams == null || !root.isAttachedToWindow()) return;
        if (!readConfig(context)) {
            if (attempt < 9) {
                root.postDelayed(() -> retryBallWindowConfig(root, context, layoutParams, attempt + 1), 50);
            }
            return;
        }

        int[] originalSize;
        synchronized (ORIGINAL_WINDOW_SIZES) {
            originalSize = ORIGINAL_WINDOW_SIZES.get(layoutParams);
        }
        if (originalSize == null) return;

        float scale = getPercent(ConfigStore.BALL_SIZE_PERCENT, ConfigStore.DEFAULT_SIZE_PERCENT) / 100f;
        int width = scaleDimension(originalSize[0], scale);
        int height = scaleDimension(originalSize[1], scale);
        if (layoutParams.width != width || layoutParams.height != height) {
            layoutParams.width = width;
            layoutParams.height = height;
            try {
                WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                windowManager.updateViewLayout(root, layoutParams);
                log("Ball window resized after config became available -> " + width + "x" + height
                        + " (" + CONFIG.get(ConfigStore.BALL_SIZE_PERCENT) + "%)", null);
            } catch (Throwable t) {
                log("Could not update floating-ball window after config retry", t);
            }
        }
        applyBallAppearance(root, context, "window config retry");
    }
    private static void applyBallAppearance(View root, Context context, String source) {
        if (root == null || context == null) return;
        readConfig(context);
        float scale = getPercent(ConfigStore.BALL_SIZE_PERCENT, ConfigStore.DEFAULT_SIZE_PERCENT) / 100f;
        resizeBallContent(root, context, scale);
        String style = CONFIG.getOrDefault(ConfigStore.BALL_STYLE, ConfigStore.STYLE_IOS);
        int opacity = getPercent(ConfigStore.BALL_OPACITY_PERCENT, ConfigStore.DEFAULT_OPACITY_PERCENT);
        applyBallStyle(root, context, style, opacity);
        BallEdgeDocking.onAppearanceChanged(root);
        log("Ball appearance applied from " + source + "; size="
                + CONFIG.get(ConfigStore.BALL_SIZE_PERCENT) + "%, style=" + style
                + ", opacity=" + CONFIG.get(ConfigStore.BALL_OPACITY_PERCENT) + "%", null);
    }

    private static void resizeBallContent(View root, Context context, float scale) {
        if (!(root instanceof ViewGroup)) return;
        boolean changed = false;
        ViewGroup rootGroup = (ViewGroup) root;
        if (rootGroup.getChildCount() > 0) {
            View ballLayout = rootGroup.getChildAt(0);
            // The window is scaled in pixels. Fill it rather than mixing OEM window pixels
            // with app-density dp, which previously left asymmetric transparent margins.
            ViewGroup.LayoutParams params = ballLayout.getLayoutParams();
            if (params != null && (params.width != ViewGroup.LayoutParams.MATCH_PARENT
                    || params.height != ViewGroup.LayoutParams.MATCH_PARENT)) {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                ballLayout.setLayoutParams(params);
                changed = true;
            }
        }

        ImageView outside = findBallImage(root, context, "img_bg");
        ImageView inner = findBallImage(root, context, "img_big_ball");
        if (outside != null) changed |= setSize(outside, context, Math.round(48f * scale), Math.round(48f * scale));
        if (inner != null) changed |= setSize(inner, context, Math.round(40f * scale), Math.round(40f * scale));
        if (changed && inner != null && "t2.a".equals(root.getClass().getName())) {
            root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override public void onLayoutChange(View view, int left, int top, int right, int bottom,
                        int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    view.removeOnLayoutChangeListener(this);
                    // A delayed config read can resize after ColorOS cached the unscaled position.
                    // Update that destination once; its original touch/release animators still run.
                    inner.setTranslationX(0f);
                    inner.setTranslationY(0f);
                    try {
                        XposedHelpers.setFloatField(root, "q", inner.getX());
                        XposedHelpers.setFloatField(root, "r", inner.getY());
                        log("Ball rest position aligned after resize: "
                                + inner.getX() + "," + inner.getY(), null);
                    } catch (Throwable t) {
                        log("Could not align ColorOS ball rest position", t);
                    }
                }
            });
        }
    }

    private static int scaleDimension(int original, float scale) {
        return original > 0 ? Math.round(original * scale) : original;
    }
    private static boolean setSize(View view, Context context, int widthDp, int heightDp) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        int width = dp(context, widthDp);
        int height = dp(context, heightDp);
        if (params != null && params.width == width && params.height == height) return false;
        if (params == null) params = new ViewGroup.LayoutParams(width, height);
        params.width = width;
        params.height = height;
        view.setLayoutParams(params);
        return true;
    }

    private static ImageView findBallImage(View root, Context context, String resourceName) {
        int id = context.getResources().getIdentifier(resourceName, "id", TARGET_PACKAGE);
        if (id == 0) return null;
        View found = root.findViewById(id);
        return found instanceof ImageView ? (ImageView) found : null;
    }

    private static void applyBallStyle(View root, Context context, String style, int opacityPercent) {
        ImageView outside = findBallImage(root, context, "img_bg");
        ImageView inner = findBallImage(root, context, "img_big_ball");
        if (outside == null || inner == null) {
            log("Floating-ball image views were not found", null);
            return;
        }
        rememberOriginalBackground(outside);
        rememberOriginalBackground(inner);
        rememberOriginalImage(outside);
        rememberOriginalImage(inner);

        if (!ConfigStore.isIosStyle(style)) {
            restoreOriginalBackground(outside);
            restoreOriginalBackground(inner);
            restoreOriginalImage(outside);
            restoreOriginalImage(inner);
            return;
        }

        APPLYING_BALL_STYLE.set(true);
        try {
            // img_bg owns the OEM touch listener; keep it present without painting a second ball.
            outside.setBackground(new ColorDrawable(Color.TRANSPARENT));
            inner.setBackground(new AssistiveTouchDrawable(opacityPercent,
                    ConfigStore.STYLE_IOS_INVERTED.equals(style)));
            outside.setImageDrawable(null);
            inner.setImageDrawable(null); // iOS AssistiveTouch has no center icon.
        } finally {
            APPLYING_BALL_STYLE.set(false);
        }
    }

    private static void rememberOriginalBackground(View view) {
        synchronized (ORIGINAL_BACKGROUNDS) {
            if (ORIGINAL_BACKGROUNDS.containsKey(view)) return;
            Drawable background = view.getBackground();
            ORIGINAL_BACKGROUNDS.put(view, cloneDrawable(background, view));
        }
    }

    private static void restoreOriginalBackground(View view) {
        Drawable original;
        synchronized (ORIGINAL_BACKGROUNDS) {
            if (!ORIGINAL_BACKGROUNDS.containsKey(view)) return;
            original = ORIGINAL_BACKGROUNDS.get(view);
        }
        view.setBackground(cloneDrawable(original, view));
    }

    private static void rememberOriginalImage(ImageView view) {
        synchronized (ORIGINAL_IMAGE_DRAWABLES) {
            if (ORIGINAL_IMAGE_DRAWABLES.containsKey(view)) return;
            ORIGINAL_IMAGE_DRAWABLES.put(view, cloneDrawable(view.getDrawable(), view));
        }
    }

    private static void restoreOriginalImage(ImageView view) {
        Drawable original;
        synchronized (ORIGINAL_IMAGE_DRAWABLES) {
            if (!ORIGINAL_IMAGE_DRAWABLES.containsKey(view)) return;
            original = ORIGINAL_IMAGE_DRAWABLES.get(view);
        }
        view.setImageDrawable(cloneDrawable(original, view));
    }
    private static Drawable cloneDrawable(Drawable drawable, View view) {
        if (drawable == null) return null;
        Drawable.ConstantState state = drawable.getConstantState();
        return state == null ? drawable.mutate() : state.newDrawable(view.getResources()).mutate();
    }

    private static int getPercent(String key, int fallback) {
        String value = CONFIG.get(key);
        if (value == null || value.isEmpty()) return fallback;
        try {
            return Math.max(1, Math.min(200, Integer.parseInt(value)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /** Adds one native menu item; its callback opens a separate list of configured apps. */
    private static void hookNativeMenuBuilder(ClassLoader loader) {
        try {
            XposedHelpers.findAndHookMethod("x2.c$f", loader, "c", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    Object builder = param.thisObject;
                    try {
                        Object managerObject = XposedHelpers.getObjectField(builder, "d");
                        if (!(managerObject instanceof View)) return;
                        Context context = ((View) managerObject).getContext();
                        readConfig(context);
                        List<AppEntry> entries = configuredMenuApps(context);
                        if (entries.isEmpty()) return;

                        @SuppressWarnings("unchecked")
                        List<Object> nativeItems = (List<Object>) XposedHelpers.getObjectField(builder, "e");
                        if (containsOurItem(nativeItems)) return;

                        int width = dp(context, 56);
                        int height = dp(context, 56);
                        if (nativeItems != null && !nativeItems.isEmpty()) {
                            Object first = nativeItems.get(0);
                            int nativeWidth = XposedHelpers.getIntField(first, "c");
                            int nativeHeight = XposedHelpers.getIntField(first, "d");
                            if (nativeWidth > 0) width = nativeWidth;
                            if (nativeHeight > 0) height = nativeHeight;
                        }

                        View folder = createFolderButton(context, width, height);
                        Class<?> callbackType = XposedHelpers.findClass("x2.c$g$b", loader);
                        Object callback = Proxy.newProxyInstance(loader, new Class<?>[]{callbackType}, (proxy, method, args) -> {
                            if ("call".equals(method.getName())) {
                                showAppSubmenu(folder);
                                return null;
                            }
                            if (method.getDeclaringClass() == Object.class) {
                                if ("toString".equals(method.getName())) return "FloatBallPlusAppsCallback";
                                if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                                if ("equals".equals(method.getName())) return proxy == args[0];
                            }
                            return null;
                        });

                        Method addItem = builder.getClass().getDeclaredMethod(
                                "a", View.class, int.class, int.class, callbackType, boolean.class);
                        addItem.setAccessible(true);
                        addItem.invoke(builder, folder, width, height, callback, false);
                    } catch (Throwable t) {
                        log("Could not append the app submenu entry", t);
                    }
                }
            });
        } catch (Throwable t) {
            log("Could not hook the native floating-ball menu", t);
        }
    }

    private static boolean containsOurItem(List<Object> items) {
        if (items == null) return false;
        for (Object item : items) {
            try {
                Object view = XposedHelpers.getObjectField(item, "g");
                if (view instanceof View && MENU_VIEW_TAG.equals(((View) view).getTag())) return true;
            } catch (Throwable ignored) {
                // Skip entries from an unexpected OEM menu implementation.
            }
        }
        return false;
    }

    private static View createFolderButton(Context context, int width, int height) {
        LinearLayout button = new LinearLayout(context);
        button.setOrientation(LinearLayout.VERTICAL);
        button.setGravity(Gravity.CENTER);
        button.setTag(MENU_VIEW_TAG);
        button.setContentDescription("搴旂敤");
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(Color.WHITE);
        background.setStroke(dp(context, 1), Color.rgb(220, 224, 232));
        button.setBackground(background);
        button.setElevation(dp(context, 3));

        ImageView icon = new ImageView(context);
        icon.setImageResource(android.R.drawable.ic_menu_view);
        icon.setImageTintList(ColorStateList.valueOf(Color.rgb(40, 61, 97)));
        button.addView(icon, new LinearLayout.LayoutParams(dp(context, 24), dp(context, 24)));

        TextView label = new TextView(context);
        label.setText("搴旂敤");
        label.setTextColor(Color.rgb(40, 48, 64));
        label.setTextSize(10);
        label.setGravity(Gravity.CENTER);
        button.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 16)));

        button.setLayoutParams(new ViewGroup.LayoutParams(width, height));
        return button;
    }

    private static void showAppSubmenu(View anchor) {
        if (anchor == null || anchor.getContext() == null) return;
        Context context = anchor.getContext();
        readConfig(context);
        List<AppEntry> entries = configuredMenuApps(context);
        if (entries.isEmpty()) return;

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));
        GradientDrawable panelBackground = new GradientDrawable();
        panelBackground.setColor(Color.WHITE);
        panelBackground.setCornerRadius(dp(context, 18));
        panelBackground.setStroke(dp(context, 1), Color.rgb(225, 228, 234));
        panel.setBackground(panelBackground);
        panel.setElevation(dp(context, 12));

        TextView title = new TextView(context);
        title.setText("搴旂敤");
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.rgb(32, 38, 48));
        title.setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8));
        panel.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int rowHeight = dp(context, 52);
        for (AppEntry entry : entries) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(context, 10), 0, dp(context, 10), 0);
            ImageView icon = new ImageView(context);
            icon.setImageDrawable(entry.icon);
            row.addView(icon, new LinearLayout.LayoutParams(dp(context, 36), dp(context, 36)));
            TextView name = new TextView(context);
            name.setText(entry.label);
            name.setTextSize(15);
            name.setTextColor(Color.rgb(35, 40, 48));
            name.setSingleLine(true);
            name.setPadding(dp(context, 12), 0, 0, 0);
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.setOnClickListener(v -> {
                launchComponent(context, entry.component.flattenToString());
            });
            panel.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight));
        }

        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int popupWidth = Math.min(dp(context, 280), screenWidth - dp(context, 32));
        PopupWindow popup = new PopupWindow(panel, popupWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(panelBackground);
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(context, 12));
        popup.setClippingEnabled(true);

        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int x = Math.max(dp(context, 16), Math.min(location[0], screenWidth - popupWidth - dp(context, 16)));
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        int estimatedHeight = dp(context, 64 + entries.size() * 52);
        int y = location[1] + anchor.getHeight();
        if (y + estimatedHeight > screenHeight - dp(context, 24)) y = Math.max(dp(context, 24), location[1] - estimatedHeight);
        final int popupX = x;
        final int popupY = y;
        anchor.post(() -> {
            try {
                popup.showAtLocation(anchor, Gravity.TOP | Gravity.LEFT, popupX, popupY);
            } catch (Throwable t) {
                log("Could not show the app submenu", t);
            }
        });
    }

    private static List<AppEntry> configuredMenuApps(Context context) {
        List<AppEntry> result = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        for (int slot = 1; slot <= 5; slot++) {
            String componentString = CONFIG.get("menu_app_" + slot);
            if (componentString == null || componentString.isEmpty()) continue;
            ComponentName component = ComponentName.unflattenFromString(componentString);
            if (component == null) continue;
            try {
                ActivityInfo info = pm.getActivityInfo(component, 0);
                if (!info.enabled) continue;
                CharSequence label = info.loadLabel(pm);
                Drawable icon = info.loadIcon(pm);
                result.add(new AppEntry(component, label == null ? component.getPackageName() : label.toString(), icon));
            } catch (PackageManager.NameNotFoundException ignored) {
                // An app may have been uninstalled since the user selected it.
            }
        }
        return result;
    }

    private static boolean launchComponent(Context context, String componentString) {
        try {
            ComponentName component = ComponentName.unflattenFromString(componentString);
            if (component == null) return false;
            ActivityInfo info = context.getPackageManager().getActivityInfo(component, 0);
            if (!info.enabled) return false;
            Intent launch = new Intent(Intent.ACTION_MAIN);
            launch.addCategory(Intent.CATEGORY_LAUNCHER);
            launch.setComponent(component);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            context.startActivity(launch);
            return true;
        } catch (Throwable t) {
            log("Could not start configured app " + componentString, t);
            return false;
        }
    }

    private static boolean readConfig(Context context) {
        if (context == null) return false;
        String[] keys = new String[]{"gesture_single", "gesture_double", "gesture_long",
                ConfigStore.BALL_SIZE_PERCENT, ConfigStore.BALL_STYLE, ConfigStore.BALL_OPACITY_PERCENT,
                "menu_app_1", "menu_app_2", "menu_app_3", "menu_app_4", "menu_app_5"};
        if (!configLoaded) {
            for (String key : keys) CONFIG.put(key, defaultConfigValue(key));
        }
        try {
            ContentProviderClient client = getConfigProviderClient(context);
            if (client == null) {
                logProviderUnavailable(null);
                return false;
            }
            try (Cursor cursor = client.query(
                    android.net.Uri.parse("content://dev.floatplus.lsp.config/all"), null, null, null, null)) {
                if (cursor == null || !cursor.moveToFirst()) {
                    logProviderUnavailable(null);
                    return false;
                }
                providerUnavailableLogged = false;
                for (String key : keys) {
                    int column = cursor.getColumnIndex(key);
                    CONFIG.put(key, column < 0 ? defaultConfigValue(key) : safe(cursor.getString(column)));
                }
                configLoaded = true;
                return true;
            }
        } catch (Throwable t) {
            configProviderClient = null;
            logProviderUnavailable(t);
            return false;
        }
    }
    private static ContentProviderClient getConfigProviderClient(Context context) {
        ContentProviderClient client = configProviderClient;
        if (client != null) return client;
        synchronized (FloatingBallModule.class) {
            client = configProviderClient;
            if (client == null) {
                client = context.getContentResolver().acquireContentProviderClient(
                        android.net.Uri.parse("content://dev.floatplus.lsp.config/all"));
                configProviderClient = client;
            }
        }
        return client;
    }

    private static void logProviderUnavailable(Throwable error) {
        if (providerUnavailableLogged) return;
        providerUnavailableLogged = true;
        log("Module settings provider is unavailable; using defaults", error);
    }

    private static String defaultConfigValue(String key) {
        if (ConfigStore.BALL_SIZE_PERCENT.equals(key)) return Integer.toString(ConfigStore.DEFAULT_SIZE_PERCENT);
        if (ConfigStore.BALL_STYLE.equals(key)) return ConfigStore.STYLE_IOS;
        if (ConfigStore.BALL_OPACITY_PERCENT.equals(key)) return Integer.toString(ConfigStore.DEFAULT_OPACITY_PERCENT);
        return "";
    }

    private static String safe(String value) { return value == null ? "" : value; }

    private static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static void log(String message, Throwable error) {
        String line = TAG + ": " + message + (error == null ? "" : " - " + error);
        XposedBridge.log(line);
        Log.i(TAG, line);
    }

    private static final class AppEntry {
        final ComponentName component;
        final String label;
        final Drawable icon;

        AppEntry(ComponentName component, String label, Drawable icon) {
            this.component = component;
            this.label = label;
            this.icon = icon;
        }
    }
}
