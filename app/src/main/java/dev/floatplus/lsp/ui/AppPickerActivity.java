package dev.floatplus.lsp.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class AppPickerActivity extends Activity {
    public static final String EXTRA_CONFIG_KEY = "config_key";
    public static final String EXTRA_COMPONENT = "component";

    private final List<ResolveInfo> allApps = new ArrayList<>();
    private final List<ResolveInfo> shownApps = new ArrayList<>();
    private AppAdapter adapter;
    private String configKey;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        configKey = getIntent().getStringExtra(EXTRA_CONFIG_KEY);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(8));
        TextView title = new TextView(this);
        title.setText("选择应用");
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("搜索应用");
        root.addView(search);

        ListView list = new ListView(this);
        adapter = new AppAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        allApps.addAll(getPackageManager().queryIntentActivities(query, PackageManager.MATCH_ALL));
        Collections.sort(allApps, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                a.loadLabel(getPackageManager()).toString(), b.loadLabel(getPackageManager()).toString()));
        shownApps.addAll(allApps);

        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter(s.toString()); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        list.setOnItemClickListener((parent, view, position, id) -> {
            ResolveInfo item = shownApps.get(position);
            ComponentName component = new ComponentName(item.activityInfo.packageName, item.activityInfo.name);
            Intent result = new Intent();
            result.putExtra(EXTRA_CONFIG_KEY, configKey);
            result.putExtra(EXTRA_COMPONENT, component.flattenToString());
            setResult(RESULT_OK, result);
            finish();
        });
        setContentView(root);
    }

    private void filter(String query) {
        String needle = query.toLowerCase(Locale.ROOT).trim();
        shownApps.clear();
        for (ResolveInfo item : allApps) {
            String label = item.loadLabel(getPackageManager()).toString().toLowerCase(Locale.ROOT);
            if (needle.isEmpty() || label.contains(needle)) shownApps.add(item);
        }
        adapter.notifyDataSetChanged();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private final class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return shownApps.size(); }
        @Override public Object getItem(int position) { return shownApps.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row = convertView instanceof LinearLayout ? (LinearLayout) convertView : new LinearLayout(AppPickerActivity.this);
            if (convertView == null) {
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setPadding(dp(8), dp(8), dp(8), dp(8));
                ImageView icon = new ImageView(AppPickerActivity.this);
                row.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
                TextView label = new TextView(AppPickerActivity.this);
                label.setTextSize(16);
                label.setPadding(dp(12), 0, 0, 0);
                row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            }
            ResolveInfo item = shownApps.get(position);
            ((ImageView) row.getChildAt(0)).setImageDrawable(item.loadIcon(getPackageManager()));
            ((TextView) row.getChildAt(1)).setText(item.loadLabel(getPackageManager()));
            return row;
        }
    }
}
