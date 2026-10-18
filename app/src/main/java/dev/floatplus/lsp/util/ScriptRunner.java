package dev.floatplus.lsp.util;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import dev.floatplus.lsp.config.ConfigProvider;
import dev.floatplus.lsp.config.ScriptStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Runs an imported script through the user's root manager and records its result. */
public final class ScriptRunner {
    private static final String TAG = "FloatBallPlus";
    private static final int MAX_OUTPUT_CHARS = 3000;

    private ScriptRunner() {}

    public static void run(Context context, String scriptId, String scriptName) {
        Context appContext = context.getApplicationContext();
        new Thread(() -> execute(appContext, scriptId, scriptName),
                "FloatBallPlus-script").start();
    }

    private static void execute(Context context, String id, String name) {
        String path = ScriptStore.pathForId(id);
        if (path == null) {
            publish(context, "脚本无效：" + safe(name));
            return;
        }
        publish(context, "正在运行：" + safe(name));
        Process process = null;
        Thread reader = null;
        StringBuilder output = new StringBuilder();
        try {
            process = new ProcessBuilder("su", "-c", "/system/bin/sh " + path)
                    .redirectErrorStream(true).start();
            process.getOutputStream().close();
            Process running = process;
            reader = new Thread(() -> readOutput(running, output), "FloatBallPlus-script-output");
            reader.setDaemon(true);
            reader.start();
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
                publish(context, "运行超时（120 秒）：" + safe(name) + appendOutput(output));
                return;
            }
            if (reader != null) reader.join(1500);
            int code = process.exitValue();
            publish(context, (code == 0 ? "运行成功" : "运行失败") + "（退出码 " + code + "）："
                    + safe(name) + appendOutput(output));
        } catch (Throwable error) {
            Log.e(TAG, "Could not run local shell script " + safe(name), error);
            if (process != null) process.destroy();
            publish(context, "运行失败：" + safe(name) + "\n" + error.getMessage() + appendOutput(output));
        } finally {
            if (reader != null && reader.isAlive()) reader.interrupt();
            if (process != null) process.destroy();
        }
    }

    private static void readOutput(Process process, StringBuilder output) {
        try (BufferedReader input = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = input.readLine()) != null) {
                synchronized (output) {
                    if (output.length() < MAX_OUTPUT_CHARS) {
                        int remaining = MAX_OUTPUT_CHARS - output.length();
                        output.append(line, 0, Math.min(line.length(), remaining));
                        if (output.length() < MAX_OUTPUT_CHARS) output.append('\n');
                    }
                }
            }
        } catch (IOException ignored) {}
    }

    private static String appendOutput(StringBuilder output) {
        synchronized (output) {
            return output.length() == 0 ? "" : "\n" + output;
        }
    }

    private static String safe(String value) { return value == null ? "脚本" : value; }

    private static void publish(Context context, String result) {
        if (context == null) return;
        try {
            ContentValues values = new ContentValues();
            values.put(ConfigProvider.SCRIPT_STATUS_VALUE, result);
            context.getContentResolver().update(
                    Uri.parse("content://" + ConfigProvider.AUTHORITY + "/script-status"),
                    values, null, null);
        } catch (Throwable error) {
            Log.e(TAG, "Could not save script execution result", error);
        }
    }
}
