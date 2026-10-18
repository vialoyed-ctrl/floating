package dev.floatplus.lsp.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import dev.floatplus.lsp.config.ScriptStore;

public final class ScriptLibraryActivity extends Activity {
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
        hint.setText("新建或编辑脚本时，直接粘贴完整的 shell 脚本内容。脚本保存在模块私有目录，触发时通过 su 以 root 运行。单个脚本最大 1 MB；仅运行你信任的脚本。");
        hint.setTextSize(14);
        hint.setPadding(0, dp(8), 0, dp(8));
        page.addView(hint);
        Button add = new Button(this);
        add.setText("粘贴新脚本");
        add.setOnClickListener(v -> editScript(null));
        page.addView(add, new LinearLayout.LayoutParams(-1, -2));
        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(page);
        refresh();
    }

    private void editScript(ScriptStore.Script existing) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(4), dp(20), dp(4));

        TextView nameLabel = new TextView(this);
        nameLabel.setText("脚本名称");
        form.addView(nameLabel);
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint("例如：清理缓存");
        if (existing != null) name.setText(existing.name);
        form.addView(name, new LinearLayout.LayoutParams(-1, -2));

        TextView contentLabel = new TextView(this);
        contentLabel.setText("脚本内容（可直接粘贴）");
        contentLabel.setPadding(0, dp(12), 0, 0);
        form.addView(contentLabel);
        EditText contents = new EditText(this);
        contents.setGravity(Gravity.TOP | Gravity.START);
        contents.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        contents.setSingleLine(false);
        contents.setHorizontallyScrolling(false);
        contents.setTypeface(android.graphics.Typeface.MONOSPACE);
        contents.setTextSize(13);
        contents.setHint("#!/system/bin/sh\n\n# 在这里粘贴脚本");
        contents.setMinHeight(dp(240));
        contents.setMaxHeight(dp(380));
        contents.setPadding(dp(10), dp(10), dp(10), dp(10));
        if (existing != null) {
            try { contents.setText(ScriptStore.read(this, existing.id)); }
            catch (Exception error) {
                Toast.makeText(this, message(error, "无法读取脚本"), Toast.LENGTH_LONG).show();
                return;
            }
        }
        form.addView(contents, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "粘贴脚本" : "编辑脚本")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                if (existing == null) ScriptStore.create(this, name.getText().toString(), contents.getText().toString());
                else ScriptStore.update(this, existing.id, name.getText().toString(), contents.getText().toString());
                Toast.makeText(this, "脚本已保存", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                refresh();
            } catch (Exception error) {
                Toast.makeText(this, message(error, "保存失败"), Toast.LENGTH_LONG).show();
            }
        }));
        dialog.show();
    }

    private String message(Exception error, String fallback) {
        return error.getMessage() == null || error.getMessage().isEmpty() ? fallback : error.getMessage();
    }

    private void refresh() {
        list.removeAllViews();
        java.util.List<ScriptStore.Script> scripts = ScriptStore.list(this);
        if (scripts.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有脚本，点击上方按钮粘贴一份脚本。");
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(36), 0, dp(36));
            list.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        for (ScriptStore.Script script : scripts) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(6), 0, dp(6));
            TextView name = new TextView(this);
            name.setText(script.name);
            name.setTextSize(15);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            Button edit = new Button(this);
            edit.setText("编辑");
            edit.setOnClickListener(v -> editScript(script));
            row.addView(edit, new LinearLayout.LayoutParams(dp(76), dp(48)));
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
