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

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
// TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        //setContentView(R.layout.activity_user_alert_dialog);
        getWindow().setLayout(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        /*final Dialog dialog = new Dialog(this); // Context, this, etc.
        dialog.setContentView(R.layout.activity_user_alert_dialog);
        dialog.setTitle("Tile of the dialog");
        dialog.show();
        */
        //this.finish();
        new AlertDialog.Builder(this)
                .setTitle("Delete entry")
                .setMessage("Are you sure you want to delete this entry?")
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // continue with delete
                        closeUserAlertDialog();
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void closeUserAlertDialog() {
       this.finish();
        this.overridePendingTransition(0, 0);
    }
}
