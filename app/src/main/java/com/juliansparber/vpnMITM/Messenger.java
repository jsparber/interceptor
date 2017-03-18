package com.juliansparber.vpnMITM;

import android.os.Handler;
import android.os.Message;


/**
 * Created by jSparber on 10/23/16.
 */



public class Messenger {
    private static Handler mHandler = null;
    private static String outputLog = "";

    public static final int LOG_TEXT = 0;
    public static final int WARNING_DIALOG = 1;
    public static final int ALERT_DIALOG = 2;
    public static final int ACTION = 3;

    public Messenger(Handler handler) {
        mHandler = handler;
        if (mHandler != null) {
            Message msg = Message.obtain();
            msg.obj = outputLog;
            mHandler.sendMessage(msg);
        }
    }

    public static void println(String stringToSend) {
        if (mHandler != null) {
            Message msg = Message.obtain();
            msg.what = LOG_TEXT;
            msg.obj = stringToSend + "\n";
            outputLog += msg.obj;
            mHandler.sendMessage(msg);
        }
    }

    public static void showAlert(String title, String body, TrafficBlocker blocker) {
        if (mHandler != null) {
            Message msg = Message.obtain();
            msg.what = ALERT_DIALOG;
            msg.obj = new String[]{title, body};
            mHandler.sendMessage(msg);
        }
    }

    public static void clear() {
        outputLog = "";
    }

    public static void doAction(String action) {
        if (mHandler != null) {
            Message msg = Message.obtain();
            msg.what = ACTION;
            msg.obj = action;
            mHandler.sendMessage(msg);
        }
    }
}
