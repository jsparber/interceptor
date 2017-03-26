package com.juliansparber.vpnMITM;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.util.Log;

import org.secuso.privacyfriendlynetmonitor.Assistant.RunStore;


/**
 * Created by jSparber on 3/25/17.
 */

public class AppInfo {
    private static final String TAG = AppInfo.class.getSimpleName();
    public Drawable icon;
    public String packageName;
    public String label;

    public AppInfo(String pkg) {
        final PackageManager pm = RunStore.getAppContext().getPackageManager();
        ApplicationInfo appInfo = null;
        try {
            appInfo = pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA);
            // Use package name if app label is empty
            if ((label = String.valueOf(pm.getApplicationLabel(appInfo))).isEmpty()) {
                label = pkg;
            }
            this.icon = pm.getApplicationIcon(appInfo);
            this.packageName = pkg;
        } catch (NullPointerException e) {
            this.label = "Unknown";
            this.packageName = "unknown";
            this.icon = null;
        } catch (PackageManager.NameNotFoundException e) {
            this.label = "Unknown";
            this.packageName = "unknown";
            this.icon = null;
            Log.e(TAG, pkg + " not found", e);
        }
    }
}
