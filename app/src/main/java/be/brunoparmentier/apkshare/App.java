package be.brunoparmentier.apkshare;

import android.graphics.drawable.Drawable;

public final class App {
    private String name;
    private Drawable icon;
    private String apkName;
    private long apkSize;

    public App(String name, Drawable icon, String apkName, long apkSize) {
        this.name = name;
        this.icon = icon;
        this.apkName = apkName;
        this.apkSize = apkSize;
    }

    public String getName() {
        return name;
    }

    public Drawable getIcon() {
        return icon;
    }

    public String getApkName() {
        return apkName;
    }

    public long getApkSize() {
        return apkSize;
    }
}
