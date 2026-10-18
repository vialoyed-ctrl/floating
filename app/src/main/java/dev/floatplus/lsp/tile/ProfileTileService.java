package dev.floatplus.lsp.tile;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import android.widget.Toast;

import dev.floatplus.lsp.config.ConfigStore;
import dev.floatplus.lsp.util.FloatingBallRestarter;

/** Shared behavior for quick settings tiles that activate a fixed profile. */
public abstract class ProfileTileService extends TileService {
    private static final String TAG = "FloatBallPlus";

    protected abstract String profileId();
    protected abstract String fallbackLabel();

    @Override public void onStartListening() {
        super.onStartListening();
        refreshTile(null);
    }

    @Override public void onClick() {
        super.onClick();
        String id = profileId();
        if (!containsProfile(id)) {
            refreshTile("此模式已删除");
            return;
        }
        if (id.equals(ConfigStore.getActiveProfileId(this))) {
            refreshTile("当前模式");
            return;
        }
        if (!ConfigStore.setActiveProfile(this, id)) {
            refreshTile("切换失败");
            return;
        }

        refreshTile("正在应用…");
        new Thread(() -> {
            try {
                FloatingBallRestarter.restart();
                refreshTileOnMainThread("已启用");
            } catch (Exception error) {
                Log.e(TAG, "Could not activate profile " + id + " from quick settings", error);
                refreshTileOnMainThread("重启失败");
                android.os.Handler main = new android.os.Handler(getMainLooper());
                main.post(() -> Toast.makeText(getApplicationContext(),
                        "模式已切换，但悬浮球重启失败；请在设置页重试。", Toast.LENGTH_LONG).show());
            }
        }, "FloatBallPlus-tile-" + id).start();
    }

    private boolean containsProfile(String id) {
        for (ConfigStore.Profile profile : ConfigStore.getProfiles(this)) {
            if (profile.id.equals(id)) return true;
        }
        return false;
    }

    private void refreshTileOnMainThread(String status) {
        new android.os.Handler(getMainLooper()).post(() -> refreshTile(status));
    }

    private void refreshTile(String status) {
        Tile tile = getQsTile();
        if (tile == null) return;
        ConfigStore.Profile selected = null;
        for (ConfigStore.Profile profile : ConfigStore.getProfiles(this)) {
            if (profile.id.equals(profileId())) { selected = profile; break; }
        }
        if (selected == null) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                tile.setLabel(fallbackLabel());
                tile.setSubtitle(status == null ? "此模式已删除" : status);
            }
        } else {
            boolean active = selected.id.equals(ConfigStore.getActiveProfileId(this));
            tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                tile.setLabel(selected.name);
                tile.setSubtitle(status != null ? status : active ? "当前模式" : "点按切换");
            }
        }
        tile.updateTile();
    }
}
