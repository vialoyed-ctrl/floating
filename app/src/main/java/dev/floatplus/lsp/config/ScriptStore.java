package dev.floatplus.lsp.config;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Local shell script library. Only generated IDs are used in shell paths. */
public final class ScriptStore {
    public static final String PREFS = "floatball_scripts";
    public static final String LIBRARY_KEY = "library_json";
    public static final String LIBRARY_COLUMN = "script_library";
    public static final int MAX_SCRIPT_BYTES = 1024 * 1024;

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

    public static String read(Context context, String id) throws Exception {
        if (find(context, id) == null) throw new IllegalArgumentException("脚本不存在");
        File file = fileForId(context, id);
        long length = file.length();
        if (!file.isFile() || length > MAX_SCRIPT_BYTES) throw new java.io.IOException("脚本文件不可用或超过 1 MB");
        byte[] bytes = new byte[(int) length];
        try (java.io.FileInputStream input = new java.io.FileInputStream(file)) {
            int offset = 0;
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) break;
                offset += count;
            }
            if (offset != bytes.length || input.read() != -1) throw new java.io.IOException("读取脚本失败");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static Script create(Context context, String name, String contents) throws Exception {
        String safeName = sanitizeName(name);
        validateContents(contents);
        String id = UUID.randomUUID().toString().replace("-", "");
        writeScript(context, id, contents);
        List<Script> scripts = list(context);
        scripts.add(new Script(id, safeName));
        if (!save(context, scripts)) {
            fileForId(context, id).delete();
            throw new java.io.IOException("无法保存脚本列表");
        }
        return new Script(id, safeName);
    }

    public static Script update(Context context, String id, String name, String contents) throws Exception {
        if (id == null || !id.matches("[a-f0-9]{32}")) throw new IllegalArgumentException("脚本 ID 无效");
        String safeName = sanitizeName(name);
        validateContents(contents);
        List<Script> scripts = list(context);
        int index = -1;
        for (int i = 0; i < scripts.size(); i++) {
            if (scripts.get(i).id.equals(id)) { index = i; break; }
        }
        if (index < 0) throw new IllegalArgumentException("脚本不存在");
        writeScript(context, id, contents);
        Script updated = new Script(id, safeName);
        scripts.set(index, updated);
        if (!save(context, scripts)) throw new java.io.IOException("无法保存脚本列表");
        return updated;
    }

    public static boolean delete(Context context, String id) {
        if (id == null || !id.matches("[a-f0-9]{32}")) return false;
        List<Script> scripts = list(context);
        boolean removed = scripts.removeIf(script -> script.id.equals(id));
        if (!removed || !save(context, scripts)) return false;
        fileForId(context, id).delete();
        ConfigStore.clearScriptReferences(context, id);
        return true;
    }

    public static String pathForId(String id) {
        if (id == null || !id.matches("[a-f0-9]{32}")) return null;
        return "/data/user/0/dev.floatplus.lsp/files/scripts/" + id + ".sh";
    }

    private static void writeScript(Context context, String id, String contents) throws Exception {
        byte[] bytes = contents.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_SCRIPT_BYTES) throw new IllegalArgumentException("脚本不能超过 1 MB");
        File directory = new File(context.getFilesDir(), "scripts");
        if (!directory.exists() && !directory.mkdirs()) throw new java.io.IOException("无法创建脚本目录");
        File target = fileForId(context, id);
        File temporary = new File(directory, id + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(bytes);
            output.getFD().sync();
        } catch (Exception error) {
            temporary.delete();
            throw error;
        }
        try {
            Os.rename(temporary.getAbsolutePath(), target.getAbsolutePath());
        } catch (ErrnoException error) {
            temporary.delete();
            throw new java.io.IOException("无法保存脚本文件", error);
        }
        target.setReadable(false, false);
        target.setReadable(true, true);
    }

    private static File fileForId(Context context, String id) {
        return new File(new File(context.getFilesDir(), "scripts"), id + ".sh");
    }

    private static void validateContents(String contents) {
        if (contents == null || contents.trim().isEmpty()) throw new IllegalArgumentException("请粘贴脚本内容");
        if (contents.getBytes(StandardCharsets.UTF_8).length > MAX_SCRIPT_BYTES) {
            throw new IllegalArgumentException("脚本不能超过 1 MB");
        }
    }

    private static boolean save(Context context, List<Script> scripts) {
        JSONArray array = new JSONArray();
        for (Script script : scripts) {
            JSONObject item = new JSONObject();
            itemToJson(item, script);
            array.put(item);
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(LIBRARY_KEY, array.toString()).commit();
    }

    private static void itemToJson(JSONObject item, Script script) {
        try { item.put("id", script.id); item.put("name", script.name); }
        catch (Exception ignored) {}
    }

    private static String sanitizeName(String value) {
        String name = value == null ? "" : value.trim().replaceAll("[\\p{Cntrl}/\\\\]", "_");
        if (name.isEmpty()) throw new IllegalArgumentException("请输入脚本名称");
        if (name.length() > 80) name = name.substring(0, 80);
        return name;
    }

    public static final class Script {
        public final String id;
        public final String name;
        public Script(String id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return name; }
    }
}
