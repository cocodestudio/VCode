package com.cocode.vcode.ide;

import android.app.Application;
import android.content.Intent;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;

import com.cocode.vcode.ide.core.diagnostic.util.KnownElements;
import com.cocode.vcode.ide.core.language.html.HtmlTagCache;
import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.data.repository.SettingsRepository;
import com.cocode.vcode.ide.ui.debug.DebugActivity;

import java.io.PrintWriter;
import java.io.StringWriter;

public class VCodeApplication extends Application {
    private static VCodeApplication instance;

    public static VCodeApplication getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        KnownElements.init(this);
        HtmlTagCache.load(this);

        // Setup custom crash handler
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e("VCodeApplication", "Uncaught exception", throwable);
            handleUncaughtException(throwable);
        });

        // Force the theme to load the exact millisecond the app launches
        SettingsRepository repo = new SettingsRepository(this);
        AppSettings settings = repo.loadSettings();

        int mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if (settings.getTheme() == AppSettings.Theme.DARK) mode = AppCompatDelegate.MODE_NIGHT_YES;
        else if (settings.getTheme() == AppSettings.Theme.LIGHT)
            mode = AppCompatDelegate.MODE_NIGHT_NO;

        AppCompatDelegate.setDefaultNightMode(mode);
    }

    private void handleUncaughtException(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        String stackTrace = sw.toString();

        Intent intent = new Intent(this, DebugActivity.class);
        intent.putExtra(DebugActivity.EXTRA_CRASH_LOG, stackTrace);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);

        // Kill the current process
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(1);
    }
}