package dev.floatplus.lsp.config;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Stores the currently active floating-ball profile and migrates existing preferences once. */
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

    private static final String PROFILE_IDS = "profile_ids";
    private static final String ACTIVE_PROFILE = "active_profile";
    private static final String PROFILE_SCHEMA = "profile_schema";
    private static final String LAST_SCRIPT_RESULT = "last_script_result";
    private static final List<String> CONFIG_KEYS = Arrays.asList(
            SINGLE, DOUBLE, LONG, BALL_SIZE_PERCENT, BALL_STYLE, BALL_OPACITY_PERCENT,
            menuKey(1), menuKey(2), menuKey(3), menuKey(4), menuKey(5));

    private ConfigStore() {}

    public static boolean isIosStyle(String style) {
        return STYLE_IOS.equals(style) || STYLE_IOS_INVERTED.equals(style);
    }

    public static String menuKey(int oneBasedSlot) {
        return "menu_app_" + oneBasedSlot;
    }

    private static String profileKey(String id, String key) {
        return "profile." + id + "." + key;
    }

    private static synchronized SharedPreferences preferences(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getInt("appearance_style_schema", 0) < 1) {
            SharedPreferences.Editor editor = prefs.edit();
            // 0.2.10 used "ios" for the inverted palette. Preserve that appearance on upgrade.
            if (!prefs.getAll().isEmpty()
                    && STYLE_IOS.equals(prefs.getString(BALL_STYLE, STYLE_IOS))) {
                editor.putString(BALL_STYLE, STYLE_IOS_INVERTED);
            }
            editor.putInt("appearance_style_schema", 1).commit();
        }
        migrateProfiles(prefs);
        return prefs;
    }

    private static void migrateProfiles(SharedPreferences prefs) {
        if (prefs.getInt(PROFILE_SCHEMA, 0) >= 1) return;
        SharedPreferences.Editor editor = prefs.edit();
        String[] ids = {"mode1", "mode2", "mode3"};
        for (String key : CONFIG_KEYS) {
            String value = legacyValue(prefs, key);
            for (String id : ids) editor.putString(profileKey(id, key), value);
        }
        editor.putString("profile_name_mode1", "模式1")
                .putString("profile_name_mode2", "模式2")
                .putString("profile_name_mode3", "模式3")
                .putString(PROFILE_IDS, "mode1,mode2,mode3")
                .putString(ACTIVE_PROFILE, "mode1")
                .putInt(PROFILE_SCHEMA, 1)
                .commit();
    }

    private static String legacyValue(SharedPreferences prefs, String key) {
        if (BALL_SIZE_PERCENT.equals(key)) {
            return Integer.toString(prefs.getInt(key, DEFAULT_SIZE_PERCENT));
        }
        if (BALL_OPACITY_PERCENT.equals(key)) {
            return Integer.toString(prefs.getInt(key, DEFAULT_OPACITY_PERCENT));
        }
        if (BALL_STYLE.equals(key)) return prefs.getString(key, STYLE_IOS);
        return prefs.getString(key, "");
    }

    public static synchronized List<Profile> getProfiles(Context context) {
        SharedPreferences prefs = preferences(context);
        List<Profile> result = new ArrayList<>();
        for (String id : profileIds(prefs)) {
            result.add(new Profile(id, prefs.getString("profile_name_" + id, id)));
        }
        return result;
    }

    public static synchronized String getActiveProfileId(Context context) {
        SharedPreferences prefs = preferences(context);
        String active = prefs.getString(ACTIVE_PROFILE, "mode1");
        return profileIds(prefs).contains(active) ? active : profileIds(prefs).get(0);
    }

    public static synchronized boolean setActiveProfile(Context context, String id) {
        SharedPreferences prefs = preferences(context);
        if (!profileIds(prefs).contains(id)) return false;
        prefs.edit().putString(ACTIVE_PROFILE, id).commit();
        return true;
    }

    public static synchronized String addProfile(Context context, String name) {
        SharedPreferences prefs = preferences(context);
        List<String> ids = profileIds(prefs);
        String id = "p" + UUID.randomUUID().toString().replace("-", "");
        String source = prefs.getString(ACTIVE_PROFILE, ids.get(0));
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : CONFIG_KEYS) {
            editor.putString(profileKey(id, key), prefs.getString(profileKey(source, key), legacyDefault(key)));
        }
        editor.putString("profile_name_" + id, cleanName(name, "新模式"));
        ids.add(id);
        editor.putString(PROFILE_IDS, join(ids)).putString(ACTIVE_PROFILE, id);
        editor.commit();
        return id;
    }

    public static synchronized boolean renameProfile(Context context, String id, String name) {
        SharedPreferences prefs = preferences(context);
        if (!profileIds(prefs).contains(id)) return false;
        return prefs.edit().putString("profile_name_" + id, cleanName(name, "新模式")).commit();
    }

    /** Deletes a profile, while always keeping at least one usable configuration. */
    public static synchronized boolean deleteProfile(Context context, String id) {
        SharedPreferences prefs = preferences(context);
        List<String> ids = profileIds(prefs);
        if (ids.size() <= 1 || !ids.contains(id)) return false;
        ids.remove(id);
        SharedPreferences.Editor editor = prefs.edit().remove("profile_name_" + id);
        for (String key : CONFIG_KEYS) editor.remove(profileKey(id, key));
        if (id.equals(prefs.getString(ACTIVE_PROFILE, ids.get(0)))) {
            editor.putString(ACTIVE_PROFILE, ids.get(0));
        }
        editor.putString(PROFILE_IDS, join(ids));
        return editor.commit();
    }

    public static synchronized String get(Context context, String key) {
        SharedPreferences prefs = preferences(context);
        String profile = prefs.getString(ACTIVE_PROFILE, "mode1");
        String stored = prefs.getString(profileKey(profile, key), null);
        return stored == null ? legacyDefault(key) : stored;
    }

    public static synchronized void put(Context context, String key, String value) {
        SharedPreferences prefs = preferences(context);
        String profile = prefs.getString(ACTIVE_PROFILE, "mode1");
        SharedPreferences.Editor editor = prefs.edit();
        String scopedKey = profileKey(profile, key);
        if (value == null || value.isEmpty()) editor.remove(scopedKey);
        else editor.putString(scopedKey, value);
        editor.apply();
    }

    public static synchronized int getInt(Context context, String key, int defaultValue) {
        try { return Integer.parseInt(get(context, key)); }
        catch (NumberFormatException ignored) { return defaultValue; }
    }

    public static synchronized void putInt(Context context, String key, int value) {
        put(context, key, Integer.toString(value));
    }

    public static String appAction(String component) { return component == null ? "" : "app:" + component; }
    public static String scriptAction(String id) { return id == null ? "" : "script:" + id; }
    public static boolean isScriptAction(String value) { return value != null && value.startsWith("script:"); }
    public static boolean isAppAction(String value) {
        return value != null && (value.startsWith("app:") || value.contains("/"));
    }
    public static String actionValue(String value) {
        if (value == null) return "";
        if (value.startsWith("app:") || value.startsWith("script:")) return value.substring(value.indexOf(':') + 1);
        return value;
    }

    public static synchronized void saveLastScriptResult(Context context, String result) {
        preferences(context).edit().putString(LAST_SCRIPT_RESULT, result == null ? "" : result).apply();
    }

    public static synchronized String getLastScriptResult(Context context) {
        return preferences(context).getString(LAST_SCRIPT_RESULT, "");
    }

    public static synchronized void clearScriptReferences(Context context, String id) {
        SharedPreferences prefs = preferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        String action = scriptAction(id);
        for (String profile : profileIds(prefs)) {
            for (String key : CONFIG_KEYS) {
                String scopedKey = profileKey(profile, key);
                if (action.equals(prefs.getString(scopedKey, ""))) editor.remove(scopedKey);
            }
        }
        editor.apply();
    }

    private static String legacyDefault(String key) {
        if (BALL_SIZE_PERCENT.equals(key)) return Integer.toString(DEFAULT_SIZE_PERCENT);
        if (BALL_OPACITY_PERCENT.equals(key)) return Integer.toString(DEFAULT_OPACITY_PERCENT);
        if (BALL_STYLE.equals(key)) return STYLE_IOS;
        return "";
    }

    private static String cleanName(String name, String fallback) {
        if (name == null) return fallback;
        String value = name.trim();
        if (value.isEmpty()) return fallback;
        return value.length() > 32 ? value.substring(0, 32) : value;
    }

    private static List<String> profileIds(SharedPreferences prefs) {
        List<String> ids = new ArrayList<>();
        String serialized = prefs.getString(PROFILE_IDS, "mode1,mode2,mode3");
        if (serialized != null) {
            for (String id : serialized.split(",")) if (!id.trim().isEmpty()) ids.add(id.trim());
        }
        if (ids.isEmpty()) ids.add("mode1");
        return ids;
    }

    private static String join(List<String> ids) {
        StringBuilder out = new StringBuilder();
        for (String id : ids) { if (out.length() > 0) out.append(','); out.append(id); }
        return out.toString();
    }

    public static final class Profile {
        public final String id;
        public final String name;
        public Profile(String id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return name; }
    }
}
