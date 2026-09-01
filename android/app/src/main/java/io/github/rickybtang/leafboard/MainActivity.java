package io.github.rickybtang.leafboard;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.WindowManager;

import io.github.rickybtang.leafboard.storage.CardRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean syncing = new AtomicBoolean(false);
    private final DateTimeFormatter updateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private DashboardView dashboard;
    private SettingsStore settings;
    private CardRepository repository;

    private final Runnable scheduledSync = new Runnable() {
        @Override
        public void run() {
            syncNow();
            handler.postDelayed(this, settings.intervalMinutes() * 60_000L);
        }
    };

    private final Runnable quietClock = new Runnable() {
        @Override
        public void run() {
            applyQuietMode();
            handler.postDelayed(this, 60_000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        settings = new SettingsStore(this);
        repository = new CardRepository(this);
        dashboard = new DashboardView(this);
        dashboard.setActions(
                this::syncNow,
                () -> startActivity(new Intent(this, SettingsActivity.class)),
                () -> moveTaskToBack(true)
        );
        dashboard.setCards(repository.loadCards());
        setContentView(dashboard);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacksAndMessages(null);
        applyQuietMode();
        syncNow();
        handler.postDelayed(scheduledSync, settings.intervalMinutes() * 60_000L);
        handler.postDelayed(quietClock, 60_000L);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void syncNow() {
        if (!syncing.compareAndSet(false, true)) return;
        dashboard.setSyncStatus("正在同步…", "");
        executor.execute(() -> {
            int imported = repository.importInbox();
            CardRepository.SyncResult remote = repository.syncRemote(settings);
            String message = imported > 0 ? "USB 导入 " + imported + " 张 · " + remote.message : remote.message;
            String updated = "检查于 " + updateFormatter.format(LocalDateTime.now());
            handler.post(() -> {
                dashboard.setCards(repository.loadCards());
                dashboard.setSyncStatus(message, updated);
                syncing.set(false);
            });
        });
    }

    private void applyQuietMode() {
        boolean quiet = settings.isQuietNow();
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.screenBrightness = quiet ? 0f : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(attributes);
        dashboard.setQuietMode(quiet);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            dashboard.nextPage();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_PAGE_UP || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            dashboard.previousPage();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }
}
