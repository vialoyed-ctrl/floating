package dev.floatplus.lsp.config;

import android.content.ContentProvider;
import android.content.Context;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;

public final class ConfigProvider extends ContentProvider {
    public static final String AUTHORITY = "dev.floatplus.lsp.config";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY + "/all");
    public static final String[] COLUMNS = {
            ConfigStore.SINGLE, ConfigStore.DOUBLE, ConfigStore.LONG,
            ConfigStore.BALL_SIZE_PERCENT, ConfigStore.BALL_STYLE, ConfigStore.BALL_OPACITY_PERCENT,
            ConfigStore.menuKey(1), ConfigStore.menuKey(2), ConfigStore.menuKey(3),
            ConfigStore.menuKey(4), ConfigStore.menuKey(5)
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
            values[i] = ConfigStore.get(requireProviderContext(), columns[i]);
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
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("Read only"); }
}
