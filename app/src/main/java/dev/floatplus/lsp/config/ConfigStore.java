package dev.floatplus.lsp.config;

import android.content.Context;
import android.content.SharedPreferences;

public final class ConfigStore {
    public static final String PREFS = "floatball_config";
    public static final String SINGLE = "gesture_single";
    public static final String DOUBLE = "gesture_double";
    public static final String LONG = "gesture_long";
    public static final String BALL_SIZE_PERCENT = "ball_size_percent";
    public static final String BALL_STYLE = "ball_style";
    public static final String BALL_OPACITY_PERCENT = "ball_opacity_percent";
    public static final String STYLE_ORIGINAL = "original";
    public static final String STYLE_IOS = "ios";
    public static final String STYLE_IOS_INVERTED = "ios_inverted";
    public static final int DEFAULT_SIZE_PERCENT = 100;
    public static final int MIN_SIZE_PERCENT = 70;
    public static final int MAX_SIZE_PERCENT = 200;
    public static final int DEFAULT_OPACITY_PERCENT = 40;

    private ConfigStore() {}

    public static boolean isIosStyle(String style) {
        return STYLE_IOS.equals(style) || STYLE_IOS_INVERTED.equals(style);
    }

    private static synchronized SharedPreferences preferences(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (preferences.getInt("appearance_style_schema", 0) < 1) {
            SharedPreferences.Editor editor = preferences.edit();
            // 0.2.10 used "ios" for the inverted palette. Preserve that appearance on upgrade.
            if (!preferences.getAll().isEmpty()
                    && STYLE_IOS.equals(preferences.getString(BALL_STYLE, STYLE_IOS))) {
                editor.putString(BALL_STYLE, STYLE_IOS_INVERTED);
            }
            editor.putInt("appearance_style_schema", 1).apply();
        }
        return preferences;
    }

    public static String menuKey(int oneBasedSlot) {
        return "menu_app_" + oneBasedSlot;
    }

    public static String get(Context context, String key) {
        SharedPreferences preferences = preferences(context);
        if (BALL_SIZE_PERCENT.equals(key)) {
            return Integer.toString(preferences.getInt(key, DEFAULT_SIZE_PERCENT));
        }
        if (BALL_OPACITY_PERCENT.equals(key)) {
            return Integer.toString(preferences.getInt(key, DEFAULT_OPACITY_PERCENT));
        }
        if (BALL_STYLE.equals(key)) {
            return preferences.getString(key, STYLE_IOS);
        }
        return preferences.getString(key, "");
    }

    public static void put(Context context, String key, String value) {
        SharedPreferences.Editor editor = preferences(context).edit();
        if (value == null || value.isEmpty()) {
            editor.remove(key);
        } else {
            editor.putString(key, value);
        }
        editor.apply();
    }

    public static int getInt(Context context, String key, int defaultValue) {
        return preferences(context).getInt(key, defaultValue);
    }

    public static void putInt(Context context, String key, int value) {
        preferences(context).edit().putInt(key, value).apply();
    }
}
