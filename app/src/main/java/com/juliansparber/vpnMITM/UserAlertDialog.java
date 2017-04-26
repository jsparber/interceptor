package com.juliansparber.vpnMITM;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;

import java.util.ArrayList;


public class UserAlertDialog extends Activity {
    public static final String PAYLOAD = "payload";

    private ArrayList<Intent> intentCache = new ArrayList<>();
    private AlertDialog currentAlert;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
            Intent intent = new Intent(this, InterceptorActivity.class);
            startActivity(intent);
            finish();
        } else {
            final String[] payload = getIntent().getStringArrayExtra(PAYLOAD);
            if (payload.length > 3) {
                creareAlert(payload);
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        /*if (currentAlert != null) {
            currentAlert.dismiss();
            //closeUserAlertDialog(null);
        }
        */
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    protected void onNewIntent(Intent intent) {
        intentCache.add(intent);
    }

    private void creareAlert(final String[] payload) {
        currentAlert = new AlertDialog.Builder(this)
                .setTitle(payload[0])
                .setMessage(payload[1] + getText(R.string.userQuestion))
                .setPositiveButton(R.string.permanentAllow, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        SharedProxyInfo.putAllowedConnections(payload[3], true);
                        closeUserAlertDialog(payload, true);
                    }
                })
                .setNeutralButton(R.string.notNow, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        closeUserAlertDialog(payload, true);
                    }
                })
                .setNegativeButton(R.string.permanentDisallow, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        SharedProxyInfo.putAllowedConnections(payload[3], false);
                        closeUserAlertDialog(payload, true);
                    }
                })
                .setIcon(new AppInfo(payload[2]).icon)
                .show();
        currentAlert.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                closeUserAlertDialog(payload, false);
            }
        });
    }

    private void closeUserAlertDialog(String[] currentPayload, boolean removeAnimation) {
        if (!intentCache.isEmpty()) {
            final String[] payload = intentCache.get(0).getStringArrayExtra(PAYLOAD);
            intentCache.remove(0);
            if (payload.length > 3) {
                creareAlert(payload);
            }
        }
        else {
            //should never finish the activity
            this.finish();
            if (removeAnimation) {
                this.overridePendingTransition(0,0);
            }
        }
    }
}
