package dev.floatplus.lsp.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import dev.floatplus.lsp.config.ConfigStore;
import dev.floatplus.lsp.config.ConfigProvider;
import dev.floatplus.lsp.config.ScriptStore;
import dev.floatplus.lsp.appearance.AssistiveTouchDrawable;
import dev.floatplus.lsp.util.FloatingBallRestarter;

public final class SettingsActivity extends Activity {
    private static final int PICK_REQUEST = 100;
    private LinearLayout content;
    private ImageView ballPreview;
    private TextView scriptStatusView;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        content.setPadding(pad, pad, pad, pad);
        scroll.addView(content);
        setContentView(scroll);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ConfigProvider.warm(this);
        if (scriptStatusView != null) {
            String result = ConfigStore.getLastScriptResult(this);
            scriptStatusView.setText(result.isEmpty() ? "暂无脚本运行记录" : result);
        }
    }

    private void buildUi() {
        TextView title = text("悬浮球增强", 26, true);
        content.addView(title, matchWrap());
        TextView intro = text("为系统悬浮球配置应用启动手势和菜单入口。未配置的手势继续使用系统原动作。", 15, false);
        intro.setPadding(0, dp(8), 0, dp(16));
        content.addView(intro, matchWrap());

        section("配置模式");
        addProfileControls();

        section("悬浮球外观");
        addBallStyleControl();
        addPercentSlider("球体大小", ConfigStore.BALL_SIZE_PERCENT,
                ConfigStore.MIN_SIZE_PERCENT, ConfigStore.MAX_SIZE_PERCENT,
                ConfigStore.DEFAULT_SIZE_PERCENT, true);
        addPercentSlider("iOS 闲置透明度", ConfigStore.BALL_OPACITY_PERCENT, 15, 100, 40, false);
        TextView appearanceHint = text("大小会同步调整触控区域。透明度适用于两种 iOS 风格；修改后点击下方按钮重启悬浮球即可应用设置，ColorOS 原有动效保持不变。", 13, false);
        appearanceHint.setPadding(0, 0, 0, dp(4));
        content.addView(appearanceHint, matchWrap());
        addRestartControl();

        section("手势操作模式");
        addAppRow("单击", ConfigStore.SINGLE);
        addAppRow("双击", ConfigStore.DOUBLE);
        addAppRow("长按", ConfigStore.LONG);

        section("点击菜单模式");
        TextView hint = text("应用入口会显示在系统原有菜单之后；空白项自动隐藏。", 14, false);
        hint.setPadding(0, 0, 0, dp(8));
        content.addView(hint, matchWrap());
        for (int i = 1; i <= 5; i++) {
            addAppRow("菜单入口 " + i, ConfigStore.menuKey(i));
        }

        section("脚本");
        Button manageScripts = new Button(this);
        manageScripts.setText("管理本地脚本");
        manageScripts.setOnClickListener(v -> startActivity(new Intent(this, ScriptLibraryActivity.class)));
        content.addView(manageScripts, matchWrap());
        TextView scriptHint = text("手势和菜单入口均可选择脚本；运行时通过 su 获取 root。脚本执行结果会显示在这里。", 13, false);
        scriptHint.setPadding(0, dp(4), 0, dp(4));
        content.addView(scriptHint, matchWrap());
        scriptStatusView = text("暂无脚本运行记录", 13, false);
        scriptStatusView.setTextColor(Color.DKGRAY);
        content.addView(scriptStatusView, matchWrap());

        section("启用说明");
        content.addView(text("安装 APK 后，在 LSPosed 中启用“悬浮球增强”，确认作用域包含 com.coloros.floatassistant，再重启悬浮球进程或设备。", 14, false), matchWrap());
    }

    private void addProfileControls() {
        java.util.List<ConfigStore.Profile> profiles = ConfigStore.getProfiles(this);
        Spinner spinner = new Spinner(this);
        ArrayAdapter<ConfigStore.Profile> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, profiles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        String activeId = ConfigStore.getActiveProfileId(this);
        int selected = 0;
        for (int i = 0; i < profiles.size(); i++) if (profiles.get(i).id.equals(activeId)) selected = i;
        spinner.setSelection(selected);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                ConfigStore.Profile profile = profiles.get(position);
                if (profile.id.equals(ConfigStore.getActiveProfileId(SettingsActivity.this))) return;
                ConfigStore.setActiveProfile(SettingsActivity.this, profile.id);
                buildUiRefresh();
                restartAfterProfileChange();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        content.addView(spinner, matchWrap());

        LinearLayout actions = new LinearLayout(this);
        Button add = new Button(this);
        add.setText("新增");
        add.setOnClickListener(v -> promptProfileName("新建模式", "模式" + (profiles.size() + 1), name -> {
            ConfigStore.addProfile(this, name);
            buildUiRefresh();
            restartAfterProfileChange();
        }));
        actions.addView(add, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button rename = new Button(this);
        rename.setText("改名");
        rename.setOnClickListener(v -> {
            ConfigStore.Profile active = findProfile(profiles, activeId);
            promptProfileName("重命名模式", active == null ? "模式" : active.name,
                    name -> { ConfigStore.renameProfile(this, activeId, name); buildUiRefresh(); });
        });
        actions.addView(rename, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button delete = new Button(this);
        delete.setText("删除");
        delete.setOnClickListener(v -> {
            if (profiles.size() <= 1) {
                Toast.makeText(this, "至少保留一个模式", Toast.LENGTH_SHORT).show();
                return;
            }
            ConfigStore.Profile active = findProfile(profiles, activeId);
            new AlertDialog.Builder(this).setTitle("删除模式")
                    .setMessage("删除“" + (active == null ? "模式" : active.name) + "”？该模式的配置无法恢复。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除", (dialog, which) -> {
                        ConfigStore.deleteProfile(this, activeId);
                        buildUiRefresh();
                        restartAfterProfileChange();
                    }).show();
        });
        actions.addView(delete, new LinearLayout.LayoutParams(0, dp(48), 1));
        content.addView(actions, matchWrap());
        TextView hint = text("切换模式会自动重启悬浮球，让外观、手势和菜单配置生效。", 13, false);
        content.addView(hint, matchWrap());
    }

    private ConfigStore.Profile findProfile(java.util.List<ConfigStore.Profile> profiles, String id) {
        for (ConfigStore.Profile profile : profiles) if (profile.id.equals(id)) return profile;
        return null;
    }

    private interface NameCallback { void onName(String name); }

    private void promptProfileName(String title, String initial, NameCallback callback) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(initial);
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this).setTitle(title).setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> callback.onName(input.getText().toString()))
                .show();
    }

    private void restartAfterProfileChange() {
        Toast.makeText(this, "模式已切换，正在重启悬浮球…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try { FloatingBallRestarter.restart(); }
            catch (Exception error) {
                Log.e("FloatBallPlus", "Could not apply selected profile", error);
                runOnUiThread(() -> Toast.makeText(this,
                        "模式已保存；重启失败，请点击“重启悬浮球并应用设置”重试。", Toast.LENGTH_LONG).show());
            }
        }, "FloatBallPlus-profile-restart").start();
    }

    private void addRestartControl() {
        Button restart = new Button(this);
        restart.setText("重启悬浮球并应用设置");
        content.addView(restart, matchWrap());
        TextView status = text("首次使用时，请在手机上允许“悬浮球增强”的 root 授权。", 13, false);
        status.setTextColor(Color.DKGRAY);
        content.addView(status, matchWrap());
        restart.setOnClickListener(view -> {
            restart.setEnabled(false);
            status.setText("正在重启悬浮球…");
            new Thread(() -> {
                String result;
                try {
                    ConfigProvider.warm(this);
                    FloatingBallRestarter.restart();
                    Log.i("FloatBallPlus", "Floating ball restarted from settings");
                    result = "悬浮球已重启，当前设置已应用。";
                } catch (Exception error) {
                    if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                    Log.e("FloatBallPlus", "Could not restart floating ball from settings", error);
                    result = "重启失败，请确认已在 root 管理器中授予“悬浮球增强”root 权限，再重试。";
                }
                String message = result;
                runOnUiThread(() -> {
                    restart.setEnabled(true);
                    status.setText(message);
                });
            }, "FloatBallPlus-restart").start();
        });
    }

    private void addBallStyleControl() {
        TextView label = text("外观", 16, false);
        LinearLayout previewRow = new LinearLayout(this);
        previewRow.setGravity(Gravity.CENTER_VERTICAL);
        previewRow.addView(label, new LinearLayout.LayoutParams(0, dp(56), 1));
        ballPreview = new ImageView(this);
        ballPreview.setContentDescription("iOS 悬浮球外观预览");
        previewRow.addView(ballPreview, new LinearLayout.LayoutParams(dp(48), dp(48)));
        content.addView(previewRow, matchWrap());

        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton ios = new RadioButton(this);
        ios.setId(View.generateViewId());
        ios.setText("iOS 原版");
        RadioButton inverted = new RadioButton(this);
        inverted.setId(View.generateViewId());
        inverted.setText("iOS 反色");
        RadioButton original = new RadioButton(this);
        original.setId(View.generateViewId());
        original.setText("系统原版");
        group.addView(ios, new RadioGroup.LayoutParams(0, RadioGroup.LayoutParams.WRAP_CONTENT, 1));
        group.addView(inverted, new RadioGroup.LayoutParams(0, RadioGroup.LayoutParams.WRAP_CONTENT, 1));
        group.addView(original, new RadioGroup.LayoutParams(0, RadioGroup.LayoutParams.WRAP_CONTENT, 1));

        String current = ConfigStore.get(this, ConfigStore.BALL_STYLE);
        updateBallPreview(current);
        group.check(ConfigStore.STYLE_ORIGINAL.equals(current) ? original.getId()
                : ConfigStore.STYLE_IOS_INVERTED.equals(current) ? inverted.getId() : ios.getId());
        group.setOnCheckedChangeListener((radioGroup, checkedId) -> {
            String selectedStyle = checkedId == original.getId() ? ConfigStore.STYLE_ORIGINAL
                    : checkedId == inverted.getId() ? ConfigStore.STYLE_IOS_INVERTED : ConfigStore.STYLE_IOS;
            ConfigStore.put(this, ConfigStore.BALL_STYLE, selectedStyle);
            updateBallPreview(selectedStyle);
        });
        content.addView(group, matchWrap());
    }

    private void updateBallPreview(String style) {
        ballPreview.setBackground(new AssistiveTouchDrawable(ConfigStore.getInt(this,
                ConfigStore.BALL_OPACITY_PERCENT, ConfigStore.DEFAULT_OPACITY_PERCENT),
                ConfigStore.STYLE_IOS_INVERTED.equals(style)));
        ballPreview.setVisibility(ConfigStore.isIosStyle(style) ? View.VISIBLE : View.INVISIBLE);
    }

    private void addPercentSlider(String label, String key, int min, int max, int defaultValue, boolean sizeSlider) {
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(label, 16, false);
        heading.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView value = text("", 14, false);
        value.setTextColor(Color.DKGRAY);
        heading.addView(value, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        content.addView(heading, matchWrap());

        SeekBar slider = new SeekBar(this);
        slider.setMax(max - min);
        int initialValue = Math.max(min, Math.min(max, ConfigStore.getInt(this, key, defaultValue)));
        slider.setProgress(initialValue - min);
        updateSliderLabel(value, initialValue, sizeSlider);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int selected = min + progress;
                updateSliderLabel(value, selected, sizeSlider);
                if (fromUser) ConfigStore.putInt(SettingsActivity.this, key, selected);
                if (ConfigStore.BALL_OPACITY_PERCENT.equals(key) && ballPreview != null) {
                    ballPreview.getBackground().setAlpha(Math.round(255 * selected / 100f));
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        content.addView(slider, matchWrap());
    }

    private void updateSliderLabel(TextView value, int percent, boolean sizeSlider) {
        if (sizeSlider) {
            value.setText(percent + "%  ·  " + Math.round(62f * percent / 100f) + "dp");
        } else {
            value.setText(percent + "%");
        }
    }

    private void section(String label) {
        TextView heading = text(label, 19, true);
        heading.setPadding(0, dp(20), 0, dp(8));
        content.addView(heading, matchWrap());
    }

    private void addAppRow(String label, String key) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(5), 0, dp(5));

        TextView name = text(label, 16, false);
        row.addView(name, new LinearLayout.LayoutParams(dp(76), LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView current = text(displayName(ConfigStore.get(this, key), key), 14, false);
        current.setTextColor(Color.DKGRAY);
        row.addView(current, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button pick = new Button(this);
        pick.setText("应用");
        pick.setOnClickListener(v -> {
            Intent intent = new Intent(this, AppPickerActivity.class);
            intent.putExtra(AppPickerActivity.EXTRA_CONFIG_KEY, key);
            startActivityForResult(intent, PICK_REQUEST);
        });
        row.addView(pick, new LinearLayout.LayoutParams(dp(66), dp(48)));

        Button script = new Button(this);
        script.setText("脚本");
        script.setOnClickListener(v -> showScriptPicker(key));
        row.addView(script, new LinearLayout.LayoutParams(dp(66), dp(48)));

        Button clear = new Button(this);
        clear.setText(key.startsWith("menu_app_") ? "清空" : "系统");
        clear.setOnClickListener(v -> {
            ConfigStore.put(this, key, "");
            current.setText(key.startsWith("menu_app_") ? "未配置" : "跟随系统");
        });
        row.addView(clear, new LinearLayout.LayoutParams(dp(66), dp(48)));

        content.addView(row, matchWrap());
    }

    private void showScriptPicker(String key) {
        java.util.List<ScriptStore.Script> scripts = ScriptStore.list(this);
        if (scripts.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("还没有脚本")
                    .setMessage("先在脚本库里粘贴或新建脚本，再把它分配给此入口。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("管理脚本", (dialog, which) ->
                            startActivity(new Intent(this, ScriptLibraryActivity.class))).show();
            return;
        }
        String[] names = new String[scripts.size()];
        for (int i = 0; i < scripts.size(); i++) names[i] = scripts.get(i).name;
        new AlertDialog.Builder(this).setTitle("选择本地脚本")
                .setItems(names, (dialog, which) -> {
                    ConfigStore.put(this, key, ConfigStore.scriptAction(scripts.get(which).id));
                    buildUiRefresh();
                })
                .setNeutralButton("管理脚本", (dialog, which) ->
                        startActivity(new Intent(this, ScriptLibraryActivity.class)))
                .show();
    }

    private String displayName(String component, String key) {
        if (component == null || component.isEmpty()) return key.startsWith("menu_app_") ? "未配置" : "跟随系统";
        if (ConfigStore.isScriptAction(component)) {
            String id = ConfigStore.actionValue(component);
            ScriptStore.Script script = ScriptStore.find(this, id);
            return script == null ? "脚本不可用" : script.name;
        }
        try {
            android.content.ComponentName cn = android.content.ComponentName.unflattenFromString(
                    ConfigStore.actionValue(component));
            if (cn == null) return component;
            return getPackageManager().getActivityInfo(cn, 0).loadLabel(getPackageManager()).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return "应用不可用";
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_REQUEST || resultCode != RESULT_OK || data == null) return;
        String key = data.getStringExtra(AppPickerActivity.EXTRA_CONFIG_KEY);
        String component = data.getStringExtra(AppPickerActivity.EXTRA_COMPONENT);
        ConfigStore.put(this, key, ConfigStore.appAction(component));
        buildUiRefresh();
    }

    private void buildUiRefresh() {
        content.removeAllViews();
        buildUi();
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
