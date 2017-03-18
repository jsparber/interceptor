package com.juliansparber.vpnMITM;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;

import android.app.Activity;
import android.support.annotation.RequiresApi;
import android.view.ViewGroup;
import android.view.Window;

public class UserAlertDialog extends Activity{
    public static final String TITLE_TO_SHOW = "title";
    public static final String BODY_TO_SHOW = "body";

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
// TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        //requestWindowFeature(Window.FEATURE_NO_TITLE);
        //setContentView(R.layout.activity_user_alert_dialog);
        getWindow().setLayout(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        new AlertDialog.Builder(this)
                .setTitle(getIntent().getStringExtra(TITLE_TO_SHOW))
                .setMessage(getIntent().getStringExtra(BODY_TO_SHOW))
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // continue with delete
                        //Messenger.doAction("stopVPN");
                        closeUserAlertDialog();
                        HTTPServer.blocker.doNotify(true);
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                        closeUserAlertDialog();
                        HTTPServer.blocker.doNotify(false);
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void closeUserAlertDialog() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        this.finish();
        this.overridePendingTransition(0, 0);
    }
}
