package com.juliansparber.vpnMITM;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.secuso.privacyfriendlynetmonitor.Assistant.RunStore;

import xyz.hexene.localvpn.LocalVPNService;

public class startActivity extends AppCompatActivity {
    private static final String TAG = startActivity.class.getSimpleName();
    private static final int VPN_REQUEST_CODE = 0x0F;
    private boolean waitingForVPNStart;
    private final Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Messenger.LOG_TEXT:
                    updateLog((String)msg.obj);
                    break;
                case Messenger.ALERT_DIALOG:
                    showWarningToUser(msg);
                    break;
                case Messenger.WARNING_DIALOG:
                    break;
                case Messenger.VPN_STATUS:
                    if (msg.obj == "running") {
                        waitingForVPNStart = false;
                    }
                    else if (msg.obj == "stopped");
                    updateActivityForServiceChanges();
                    break;
            }
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        new Messenger(mHandler);
        RunStore.setAppContext(this.getApplicationContext());
        RunStore.setContext(this);

        final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!LocalVPNService.isRunning()) {
                    Snackbar.make(view, R.string.start_notification, Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                    startInterceptor();
                } else {
                    Snackbar.make(view, R.string.stop_notification, Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                    stopInterceptor();
                }
            }
        });
    }

    private void updateActivityForServiceChanges() {
        final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        if (LocalVPNService.isRunning()) {
            fab.setImageResource(android.R.drawable.ic_media_pause);
        }
        else if (waitingForVPNStart){
            //do nothing
        }
        else {
            fab.setImageResource(android.R.drawable.ic_media_play);
        }
        updateLog(Messenger.getOutputLog());
    }

    private void startInterceptor() {
        Intent vpnIntent = VpnService.prepare(this);
        if (vpnIntent != null)
            startActivityForResult(vpnIntent, VPN_REQUEST_CODE);
        else
            onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null);
    }

    private void stopInterceptor() {
        if (LocalVPNService.isRunning()) {
            Intent stopIntent = new Intent(this, LocalVPNService.class);
            stopIntent.putExtra("cmd", "stop");
            startService(stopIntent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateActivityForServiceChanges();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            waitingForVPNStart = true;
            Intent startIntent = new Intent(this, LocalVPNService.class);
            startIntent.putExtra("cmd", "start");
            startService(startIntent);
        }
    }

    //Show warning msg to user
    private void showWarningToUser (Message msg) {
        Intent intent = new Intent(this, UserAlertDialog.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        intent.putExtra(UserAlertDialog.PAYLOAD, (String [])msg.obj);
        startActivity(intent);
    }

    private void updateLog(String log) {
        final TextView logOutput = (TextView) findViewById(R.id.logOutput);
        logOutput.setText(log);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.about:
                //Toast.makeText(this, "Selected options menu item: About", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, AboutActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
