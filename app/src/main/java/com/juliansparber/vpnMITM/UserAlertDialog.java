package com.juliansparber.vpnMITM;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import android.app.Activity;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.ViewGroup;
import android.view.Window;

import be.brunoparmentier.apkshare.AppListActivity;
import xyz.hexene.localvpn.LocalVPN;

public class UserAlertDialog extends Activity{
    public static final String TITLE_TO_SHOW = "title";
    public static final String BODY_TO_SHOW = "body";
    public static final String BLOCKER_PORT = "blocker_port";

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

        int flags = getIntent().getFlags();
        if ((flags & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            Log.d("TAG", "Lanched from history");
            Intent intent = new Intent(this, LocalVPN.class);
            startActivity(intent);
            finish();
        }
        else {

            new AlertDialog.Builder(this)
                    .setTitle(getIntent().getStringExtra(TITLE_TO_SHOW))
                    .setMessage(getIntent().getStringExtra(BODY_TO_SHOW))
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            closeUserAlertDialog();
                            int elementToRemove = getIntent().getIntExtra(BLOCKER_PORT, 0);
                            try {
                                SharedProxyInfo.blocker.get(elementToRemove).doNotify(true);
                            } catch (NullPointerException e) {

                            }
                        }
                    })
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            closeUserAlertDialog();
                            int elementToRemove = getIntent().getIntExtra(BLOCKER_PORT, 0);
                            try {
                                SharedProxyInfo.blocker.get(elementToRemove).doNotify(false);
                            } catch (NullPointerException e) {

                            }
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        }
    }

    private void closeUserAlertDialog() {
        this.finish();
        this.overridePendingTransition(0, 0);
    }
}
