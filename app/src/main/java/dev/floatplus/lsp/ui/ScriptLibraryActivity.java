package dev.floatplus.lsp.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import dev.floatplus.lsp.config.ScriptStore;

public final class ScriptLibraryActivity extends Activity {
    private static final int IMPORT_SCRIPT = 53;
    private LinearLayout list;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(18), dp(18), dp(12));
        TextView title = new TextView(this);
        title.setText("本地脚本库");
        title.setTextSize(24);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        page.addView(title);
        TextView hint = new TextView(this);
        hint.setText("导入手机中的 .sh 文件。模块会保存副本；执行时将通过 su 请求 root 权限。每个文件最大 1 MB。仅导入并运行你信任的脚本。 ");
        hint.setTextSize(14);
        hint.setPadding(0, dp(8), 0, dp(8));
        page.addView(hint);
        Button add = new Button(this);
        add.setText("从手机选择 .sh 脚本");
        add.setOnClickListener(v -> chooseScript());
        page.addView(add, new LinearLayout.LayoutParams(-1, -2));
        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(page);
        refresh();
    }

    private void chooseScript() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/x-sh", "text/x-shellscript", "text/plain", "application/octet-stream"});
        startActivityForResult(intent, IMPORT_SCRIPT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != IMPORT_SCRIPT || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            ScriptStore.Script script = ScriptStore.importFromUri(this, uri);
            Toast.makeText(this, "已导入 " + script.name, Toast.LENGTH_SHORT).show();
            refresh();
        } catch (Exception error) {
            Toast.makeText(this, error.getMessage() == null ? "导入失败" : error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void refresh() {
        list.removeAllViews();
        if (ScriptStore.list(this).isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有导入脚本");
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(36), 0, dp(36));
            list.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        for (ScriptStore.Script script : ScriptStore.list(this)) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(6), 0, dp(6));
            TextView name = new TextView(this);
            name.setText(script.name);
            name.setTextSize(15);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            Button remove = new Button(this);
            remove.setText("删除");
            remove.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("删除脚本")
                    .setMessage("删除“" + script.name + "”？所有模式中指向此脚本的入口也会清空。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除", (dialog, which) -> {
                        ScriptStore.delete(this, script.id);
                        refresh();
                    }).show());
            row.addView(remove, new LinearLayout.LayoutParams(dp(76), dp(48)));
            list.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
