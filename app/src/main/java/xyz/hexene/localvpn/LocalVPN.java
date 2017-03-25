/*
** Copyright 2015, Mohamed Naufal
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package xyz.hexene.localvpn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.juliansparber.vpnMITM.BufferServer;
import com.juliansparber.vpnMITM.Messenger;
import com.juliansparber.vpnMITM.R;
import com.juliansparber.vpnMITM.UserAlertDialog;

import org.secuso.privacyfriendlynetmonitor.Assistant.RunStore;


public class LocalVPN extends AppCompatActivity {
    private static final int VPN_REQUEST_CODE = 0x0F;
    private static final String TAG = LocalVPN.class.getSimpleName();

    private boolean waitingForVPNStart;
    private String appToTest;
    private String appToTestName;
    private static Context context;

    static private BufferServer server = null;

    private BroadcastReceiver vpnStateReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (LocalVPNService.BROADCAST_VPN_STATE.equals(intent.getAction())) {
                if (intent.getBooleanExtra("running", false)) {
                    waitingForVPNStart = false;
                    changeButton();
                }
                if (intent.getBooleanExtra("stopped", false)) {
                    changeButton();
                }
            }
        }
    };
    final private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (Messenger.LOG_TEXT == msg.what) {
                updateLog((String) msg.obj);
            }
            else if (Messenger.ALERT_DIALOG == msg.what) {
                showWarningToUser(msg);
            }
            else if(Messenger.ACTION == msg.what) {
                if (msg.obj.equals("stopVPN"));
                stopVPN();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.context = getApplicationContext();
        RunStore.setAppContext(this.context);
        RunStore.setContext(this);
        setContentView(R.layout.activity_local_vpn);
        waitingForVPNStart = false;
        Intent intent = getIntent();
        appToTest = intent.getStringExtra("appToTest");
        appToTestName = intent.getStringExtra("appToTestName");

        LocalBroadcastManager.getInstance(this).registerReceiver(vpnStateReceiver,
                new IntentFilter(LocalVPNService.BROADCAST_VPN_STATE));

        final TextView logOutput = (TextView) findViewById(R.id.logOutput);
        new Messenger(mHandler);
    }
    public static Context getAppContext() {
        return context;
    }

    public void buttonOnClick(View v) {
        if (!LocalVPNService.isRunning() && !waitingForVPNStart) {
            startVPN();

            //           BufferServer server = null;
            //server = new BufferServer(8080, InetAddress.getByName("109.68.230.138"), 80);
//            server.start();
            //should be a service instate of a thread
            //server = new BufferServer(8080, mHandler);
            //server.start();
        } else {
            stopVPN();
            //server.stop();
        }
    }

    private void startVPN() {
        //startConnectionLogger();
        //Messenger.clear();
        if (appToTest != null) {
            Messenger.println("Start test for " + appToTestName + "...");
            Messenger.println("Go to " + appToTestName);
        }
        else
            Messenger.println("Start test for all apps...");
        Intent vpnIntent = VpnService.prepare(this);
        if (vpnIntent != null)
            startActivityForResult(vpnIntent, VPN_REQUEST_CODE);
        else
            onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null);
    }

    private void stopVPN() {
        //stopConnectionLogger();
        if (LocalVPNService.isRunning()) {
            Messenger.clear();
            final TextView logOutput = (TextView) findViewById(R.id.logOutput);
            logOutput.setText("");
            Intent stopIntent = new Intent(this, LocalVPNService.class);
            stopIntent.putExtra("cmd", "stop");
            startService(stopIntent);
            //stopService(new Intent(this, LocalVPNService.class));
            changeButton();
            Messenger.println("VPN  stopped");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            waitingForVPNStart = true;

            Intent startIntent = new Intent(this, LocalVPNService.class);
            //startIntent.putExtra("testApp", "com.termux");
            Log.d(TAG, "AppToTest " + appToTest);
            startIntent.putExtra("testApp", appToTest);
            startIntent.putExtra("cmd", "start");
            startService(startIntent);
            changeButton();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        changeButton();
    }

    private void updateLog(String log) {
        final TextView logOutput = (TextView) findViewById(R.id.logOutput);
        logOutput.setText(logOutput.getText() + log);
    }

    //Show warning msg to user
    private void showWarningToUser (Message msg) {
        Intent intent = new Intent(this, UserAlertDialog.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        intent.putExtra(UserAlertDialog.PAYLOAD, (String [])msg.obj);
        startActivity(intent);
    }

    private void changeButton() {
        final Button vpnButton = (Button) findViewById(R.id.vpn);
        if (LocalVPNService.isRunning()) {
            vpnButton.setEnabled(true);
            vpnButton.setText("Stop VPN");
        } else if (waitingForVPNStart) {
            vpnButton.setEnabled(false);
            vpnButton.setText("Starting VPN...");
        } else {
            vpnButton.setEnabled(true);
            vpnButton.setText("Start VPN");
        }
    }
}
