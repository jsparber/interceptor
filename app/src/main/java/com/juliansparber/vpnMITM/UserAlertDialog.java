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

import xyz.hexene.localvpn.LocalVPN;


public class UserAlertDialog extends Activity{
    public static final String PAYLOAD = "payload";

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
            Log.d("TAG", "Launched from history");
            Intent intent = new Intent(this, LocalVPN.class);
            startActivity(intent);
            finish();
        }
        else {
            String[] payload = getIntent().getStringArrayExtra(PAYLOAD);
            if (payload.length > 2) {
                new AlertDialog.Builder(this)
                        .setTitle(payload[0])
                        .setMessage(payload[1] + getText(R.string.userQuestion))
                        .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                closeUserAlertDialog();
                            }
                        })
                        .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                closeUserAlertDialog();
                            }
                        })
                        .setIcon(new AppInfo(payload[2]).icon)
                        .show();
            }
        }
    }

    private void closeUserAlertDialog() {
        this.finish();
        this.overridePendingTransition(0, 0);
    }
}
