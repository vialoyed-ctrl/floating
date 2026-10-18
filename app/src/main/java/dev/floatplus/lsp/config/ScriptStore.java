package dev.floatplus.lsp.config;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Local .sh script library. Only generated IDs are used in shell paths. */
public final class ScriptStore {
    public static final String PREFS = "floatball_scripts";
    public static final String LIBRARY_KEY = "library_json";
    public static final String LIBRARY_COLUMN = "script_library";
    private static final int MAX_SCRIPT_BYTES = 1024 * 1024;

    private ScriptStore() {}

    public static List<Script> list(Context context) {
        return decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LIBRARY_KEY, "[]"));
    }

    public static List<Script> decode(String json) {
        List<Script> scripts = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json == null ? "[]" : json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("id", "");
                String name = item.optString("name", "");
                if (id.matches("[a-f0-9]{32}") && !name.isEmpty()) scripts.add(new Script(id, name));
            }
        } catch (Exception ignored) {}
        return scripts;
    }

    public static String libraryJson(Context context) {
        JSONArray array = new JSONArray();
        for (Script script : list(context)) {
            JSONObject item = new JSONObject();
            itemToJson(item, script);
            array.put(item);
        }
        return array.toString();
    }

    public static Script find(Context context, String id) {
        for (Script script : list(context)) if (script.id.equals(id)) return script;
        return null;
    }

    public static Script importFromUri(Context context, Uri uri) throws Exception {
        String name = displayName(context, uri);
        if (name == null || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".sh")) {
            throw new IllegalArgumentException("请选择扩展名为 .sh 的脚本文件");
        }
        name = sanitizeName(name);
        String id = UUID.randomUUID().toString().replace("-", "");
        File directory = new File(context.getFilesDir(), "scripts");
        if (!directory.exists() && !directory.mkdirs()) throw new java.io.IOException("无法创建脚本目录");
        File target = new File(directory, id + ".sh");
        File temporary = new File(directory, id + ".tmp");
        int total = 0;
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(temporary)) {
            if (input == null) throw new java.io.IOException("无法读取所选文件");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_SCRIPT_BYTES) throw new IllegalArgumentException("脚本不能超过 1 MB");
                output.write(buffer, 0, count);
            }
            output.getFD().sync();
        } catch (Exception error) {
            temporary.delete();
            throw error;
        }
        if (!temporary.renameTo(target)) {
            temporary.delete();
            throw new java.io.IOException("无法保存脚本文件");
        }
        target.setReadable(false, false);
        target.setReadable(true, true);
        List<Script> scripts = list(context);
        scripts.add(new Script(id, name));
        save(context, scripts);
        return new Script(id, name);
    }

    public static boolean delete(Context context, String id) {
        if (id == null || !id.matches("[a-f0-9]{32}")) return false;
        List<Script> scripts = list(context);
        boolean removed = scripts.removeIf(script -> script.id.equals(id));
        if (!removed) return false;
        new File(new File(context.getFilesDir(), "scripts"), id + ".sh").delete();
        save(context, scripts);
        ConfigStore.clearScriptReferences(context, id);
        return true;
    }

    public static String pathForId(String id) {
        if (id == null || !id.matches("[a-f0-9]{32}")) return null;
        return "/data/user/0/dev.floatplus.lsp/files/scripts/" + id + ".sh";
    }

    private static void save(Context context, List<Script> scripts) {
        JSONArray array = new JSONArray();
        for (Script script : scripts) {
            JSONObject item = new JSONObject();
            itemToJson(item, script);
            array.put(item);
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(LIBRARY_KEY, array.toString()).commit();
    }

    private static void itemToJson(JSONObject item, Script script) {
        try { item.put("id", script.id); item.put("name", script.name); }
        catch (Exception ignored) {}
    }

    private static String displayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {}
        String fallback = uri.getLastPathSegment();
        return fallback == null ? null : fallback;
    }

    private static String sanitizeName(String value) {
        String name = new File(value).getName().replaceAll("[\\p{Cntrl}]", "_");
        if (name.length() > 80) name = name.substring(name.length() - 80);
        return name;
    }

    public static final class Script {
        public final String id;
        public final String name;
        public Script(String id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return name; }
    }
}
