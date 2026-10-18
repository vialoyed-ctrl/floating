package dev.floatplus.lsp.config;

import android.content.ContentProvider;
import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

import dev.floatplus.lsp.util.ScriptRunner;

public final class ConfigProvider extends ContentProvider {
    public static final String AUTHORITY = "dev.floatplus.lsp.config";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY + "/all");
    public static final String SCRIPT_STATUS_VALUE = "script_status";
    public static final String RUN_SCRIPT_METHOD = "run_script";
    public static final String SCRIPT_ID_ARGUMENT = "script_id";
    public static final String[] COLUMNS = {
            ConfigStore.SINGLE, ConfigStore.DOUBLE, ConfigStore.LONG,
            ConfigStore.BALL_SIZE_PERCENT, ConfigStore.BALL_STYLE, ConfigStore.BALL_OPACITY_PERCENT,
            ConfigStore.menuKey(1), ConfigStore.menuKey(2), ConfigStore.menuKey(3),
            ConfigStore.menuKey(4), ConfigStore.menuKey(5), ScriptStore.LIBRARY_COLUMN
    };

    @Override
    public boolean onCreate() {
        return true;
    }

    /** Start the provider while the user has opened the module settings screen. */
    public static void warm(Context context) {
        try (Cursor cursor = context.getContentResolver().query(URI, null, null, null, null)) {
            if (cursor != null) cursor.moveToFirst();
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (!isFloatingBallCaller()) {
            throw new SecurityException("Only the ColorOS floating-ball process may read module settings");
        }
        String[] columns = projection == null ? COLUMNS : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] values = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            values[i] = ScriptStore.LIBRARY_COLUMN.equals(columns[i])
                    ? ScriptStore.libraryJson(requireProviderContext())
                    : ConfigStore.get(requireProviderContext(), columns[i]);
        }
        cursor.addRow(values);
        return cursor;
    }

    private boolean isFloatingBallCaller() {
        if (getContext() == null) return false;
        int callingUid = Binder.getCallingUid();
        if (callingUid == getContext().getApplicationInfo().uid) return true;
        String[] packages = getContext().getPackageManager().getPackagesForUid(callingUid);
        if (packages == null) return false;
        for (String packageName : packages) {
            if ("com.coloros.floatassistant".equals(packageName)) return true;
        }
        return false;
    }

    private android.content.Context requireProviderContext() {
        if (getContext() == null) throw new IllegalStateException("Provider is not attached");
        return getContext();
    }

    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.dev.floatplus.config"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("Read only"); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("Read only"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        if (!isFloatingBallCaller()) {
            throw new SecurityException("Only the ColorOS floating-ball process may report script status");
        }
        if (uri == null || !"script-status".equals(uri.getLastPathSegment())
                || values == null || !values.containsKey(SCRIPT_STATUS_VALUE)) return 0;
        String status = values.getAsString(SCRIPT_STATUS_VALUE);
        if (status == null) status = "";
        if (status.length() > 4096) status = status.substring(0, 4096);
        ConfigStore.saveLastScriptResult(requireProviderContext(), status);
        return 1;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!RUN_SCRIPT_METHOD.equals(method)) return super.call(method, arg, extras);
        if (!isFloatingBallCaller()) {
            throw new SecurityException("Only the ColorOS floating-ball process may request a script run");
        }
        String id = extras == null ? "" : extras.getString(SCRIPT_ID_ARGUMENT, "");
        ScriptStore.Script script = ScriptStore.find(requireProviderContext(), id);
        if (script == null) return new Bundle();
        ScriptRunner.run(requireProviderContext(), script.id, script.name);
        Bundle result = new Bundle();
        result.putBoolean("started", true);
        return result;
    }
}
