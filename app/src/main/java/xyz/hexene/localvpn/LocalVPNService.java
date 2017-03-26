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

import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.juliansparber.vpnMITM.BufferServer;
import com.juliansparber.vpnMITM.R;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import eu.faircode.netguard.IPUtil;

public class LocalVPNService extends VpnService {
    private static final String TAG = LocalVPNService.class.getSimpleName();
    private static final String VPN_ADDRESS = "10.0.0.2"; // Only IPv4 support for now
    private static final String VPN_ROUTE = "0.0.0.0"; // Intercept everything
    private static final String REDIRECTION_ADDRESS = "127.0.0.1";
    private static String APP_TO_TEST = null;

    public static final String BROADCAST_VPN_STATE = "xyz.hexene.localvpn.VPN_STATE";

    private static boolean isRunning = false;

    private ParcelFileDescriptor vpnInterface = null;

    private PendingIntent pendingIntent;

    private ConcurrentLinkedQueue<Packet> deviceToNetworkUDPQueue;
    private ConcurrentLinkedQueue<Packet> deviceToNetworkTCPQueue;
    private ConcurrentLinkedQueue<ByteBuffer> networkToDeviceQueue;
    private ExecutorService executorService;

    private Selector udpSelector;
    private Selector tcpSelector;
    private static BufferServer proxyServer;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    public void startVPN() {
        isRunning = true;
        setupVPN();

        try {
            udpSelector = Selector.open();
            tcpSelector = Selector.open();
            deviceToNetworkUDPQueue = new ConcurrentLinkedQueue<>();
            deviceToNetworkTCPQueue = new ConcurrentLinkedQueue<>();
            networkToDeviceQueue = new ConcurrentLinkedQueue<>();

            proxyServer = new BufferServer(0, 20, this);

            executorService = Executors.newFixedThreadPool(7);
            executorService.submit(new UDPInput(networkToDeviceQueue, udpSelector));
            executorService.submit(new UDPOutput(deviceToNetworkUDPQueue, udpSelector, this));
            executorService.submit(new TCPInput(networkToDeviceQueue, tcpSelector));
            executorService.submit(new TCPOutput(deviceToNetworkTCPQueue, networkToDeviceQueue, tcpSelector, this));
            executorService.submit(proxyServer);
            executorService.submit(new VPNRunnable(vpnInterface.getFileDescriptor(),
                    deviceToNetworkUDPQueue, deviceToNetworkTCPQueue, networkToDeviceQueue));
            //executorService.submit(new VPNOutput(vpnInterface.getFileDescriptor(), networkToDeviceQueue));
            //executorService.submit(new VPNInput(vpnInterface.getFileDescriptor(), deviceToNetworkUDPQueue, deviceToNetworkTCPQueue));
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(BROADCAST_VPN_STATE).putExtra("running", true));
            Log.i(TAG, "Started");
        } catch (IOException e) {
            // TODO: Here and elsewhere, we should explicitly notify the user of any errors
            // and suggest that they stop the service, since we can't do it ourselves
            Log.e(TAG, "Error starting service", e);
            cleanup();
        }
    }

    private void setupVPN() {
        if (vpnInterface == null) {
            Builder builder = new Builder();
            builder.addAddress(VPN_ADDRESS, 32);
            //builder.addRoute(VPN_ROUTE, 0);
            builder.addDnsServer("8.8.8.8");
            builder = createRoute(builder);
            //builder.setBlocking(true);
            //change this to the app to test
            if (APP_TO_TEST != null && APP_TO_TEST != "") {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        builder.addAllowedApplication(APP_TO_TEST);
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            //        builder.addAllowedApplication("com.termux");
            //        builder.addAllowedApplication("org.mozilla.firefox");
                    builder.addDisallowedApplication("com.android.shell");
                    builder.addDisallowedApplication("com.android.system");
                    builder.addDisallowedApplication("com.qualcomm.atfwd");
                    builder.addDisallowedApplication("com.juliansparber.vpnMITM");
                }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            vpnInterface = builder.setSession(getString(R.string.app_name)).setConfigureIntent(pendingIntent).establish();
        }
    }

    private Builder createRoute(Builder builder) {
        // Exclude IP ranges
        List<IPUtil.CIDR> listExclude = new ArrayList<>();
        //listExclude.add(new IPUtil.CIDR("127.0.0.0", 8)); // localhost

        // USB tethering 192.168.42.x
        // Wi-Fi tethering 192.168.43.x
        //listExclude.add(new IPUtil.CIDR("192.168.42.0", 23));
        // Wi-Fi direct 192.168.49.x
        //listExclude.add(new IPUtil.CIDR("192.168.49.0", 24));

        //Exclude localnetwork
        // IPUtil.CIDR local = new IPUtil.CIDR(ia.getAddress(), ia.getNetworkPrefixLength());

        // Broadcast
        listExclude.add(new IPUtil.CIDR("224.0.0.0", 3));
        //listExclude.add(new IPUtil.CIDR("239.255.255.250", 32 ));

        Collections.sort(listExclude);

        try {
            InetAddress start = InetAddress.getByName("0.0.0.0");
            for (IPUtil.CIDR exclude : listExclude) {
                Log.i(TAG, "Exclude " + exclude.getStart().getHostAddress() + "..." + exclude.getEnd().getHostAddress());
                for (IPUtil.CIDR include : IPUtil.toCIDR(start, IPUtil.minus1(exclude.getStart())))
                    try {
                        builder.addRoute(include.address, include.prefix);
                    } catch (Throwable ex) {
                        Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                    }
                start = IPUtil.plus1(exclude.getEnd());
            }
            for (IPUtil.CIDR include : IPUtil.toCIDR("224.0.1.130", "239.255.255.249"))
                try {
                    builder.addRoute(include.address, include.prefix);
                } catch (Throwable ex) {
                    Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                }
            for (IPUtil.CIDR include : IPUtil.toCIDR("239.255.255.251", "255.255.255.255"))
                try {
                    builder.addRoute(include.address, include.prefix);
                } catch (Throwable ex) {
                    Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                }
            Log.d(TAG, "Brackepoint");
        } catch (UnknownHostException ex) {
            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
        }
        return builder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String cmd = intent.getStringExtra("cmd");
        String testApp = intent.getStringExtra("testApp");
        if (cmd != null && cmd.contains("stop")) {
            stopVPN();
            stopService(new Intent(this, LocalVPNService.class));
        }
        else if (cmd != null && cmd.contains("start")){
            APP_TO_TEST = testApp;
            startVPN();
        }

        return START_STICKY;
    }

    public void stopVPN() {
        onDestroy();
    }


    public static boolean isRunning() {
        return isRunning;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        executorService.shutdownNow();
        cleanup();
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(BROADCAST_VPN_STATE).putExtra("stopped", true));
        Log.i(TAG, "Stopped");
    }

    private void cleanup() {
        deviceToNetworkTCPQueue = null;
        deviceToNetworkUDPQueue = null;
        networkToDeviceQueue = null;
        //ByteBufferPool.clear();
        closeResources(udpSelector, tcpSelector, vpnInterface);
    }

    // TODO: Move this to a "utils" class for reuse
    private static void closeResources(Closeable... resources) {
        for (Closeable resource : resources) {
            try {
                resource.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    // form network to device
    private static class VPNOutput implements Runnable {
        private static final String TAG = VPNOutput.class.getSimpleName();

        private FileDescriptor vpnFileDescriptor;

        private ConcurrentLinkedQueue<ByteBuffer> networkToDeviceQueue;

        public VPNOutput(FileDescriptor vpnFileDescriptor,
                         ConcurrentLinkedQueue<ByteBuffer> networkToDeviceQueue) {
            this.vpnFileDescriptor = vpnFileDescriptor;
            this.networkToDeviceQueue = networkToDeviceQueue;
        }

        @Override
        public void run() {
            Log.i(TAG, "Started runnable for traffic from network to device");

            FileChannel vpnOutput = new FileOutputStream(vpnFileDescriptor).getChannel();

            try {
                boolean dataReceived;
                while (!Thread.interrupted()) {
                    ByteBuffer bufferFromNetwork = networkToDeviceQueue.poll();
                    if (bufferFromNetwork != null) {
                        bufferFromNetwork.flip();

                        while (bufferFromNetwork.hasRemaining())
                            try {
                                vpnOutput.write(bufferFromNetwork);
                            } catch (IOException e) {
                                e.printStackTrace();
                                bufferFromNetwork.clear();
                            }
                        dataReceived = true;

                        ByteBufferPool.release(bufferFromNetwork);
                    } else {
                        dataReceived = false;
                    }

                    // TODO: Sleep-looping is not very battery-friendly, consider blocking instead
                    // Confirm if throughput with ConcurrentQueue is really higher compared to BlockingQueue
                    if (!dataReceived)
                        Thread.sleep(10);
                }
            } catch (InterruptedException e) {
                Log.i(TAG, "Stopping");
            } finally {
                closeResources(vpnOutput);
            }
        }
    }

    //From device to network
    private static class VPNInput implements Runnable {
        private static final String TAG = VPNInput.class.getSimpleName();

        private FileDescriptor vpnFileDescriptor;

        private ConcurrentLinkedQueue<Packet> deviceToNetworkUDPQueue;
        private ConcurrentLinkedQueue<Packet> deviceToNetworkTCPQueue;

        public VPNInput(FileDescriptor vpnFileDescriptor,
                        ConcurrentLinkedQueue<Packet> deviceToNetworkUDPQueue,
                        ConcurrentLinkedQueue<Packet> deviceToNetworkTCPQueue) {
            this.vpnFileDescriptor = vpnFileDescriptor;
            this.deviceToNetworkUDPQueue = deviceToNetworkUDPQueue;
            this.deviceToNetworkTCPQueue = deviceToNetworkTCPQueue;
        }

        @Override
        public void run() {
            Log.i(TAG, "Started runnable for traffic from device to network");
            FileChannel vpnInput = new FileInputStream(vpnFileDescriptor).getChannel();

            try {
                ByteBuffer bufferToNetwork = null;
                boolean dataSent = true;
                while (!Thread.interrupted()) {
                    if (dataSent)
                        bufferToNetwork = ByteBufferPool.acquire();
                    else
                        bufferToNetwork.clear();

                    // TODO: Block when not connected
                    int readBytes = 0;
                    try {
                        readBytes = vpnInput.read(bufferToNetwork);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if (readBytes > 0) {
                        dataSent = true;
                        bufferToNetwork.flip();
                        Packet packet = null;
                        try {
                            packet = new Packet(bufferToNetwork);
                        } catch (UnknownHostException e) {
                            e.printStackTrace();
                        }
                        if (packet.isUDP()) {
                            deviceToNetworkUDPQueue.offer(packet);
                        } else if (packet.isTCP()) {
                            packet.ip4Header.originalDestinationAddress = packet.ip4Header.destinationAddress;
                            packet.tcpHeader.originalDestinationPort = packet.tcpHeader.destinationPort;

                            try {
                                packet.ip4Header.destinationAddress = InetAddress.getByName(REDIRECTION_ADDRESS);
                            } catch (UnknownHostException e) {
                                e.printStackTrace();
                            }
                            packet.tcpHeader.destinationPort = proxyServer.getPort();

                            deviceToNetworkTCPQueue.offer(packet);
                        } else {
                            Log.w(TAG, "Unknown packet type");
                            Log.w(TAG, packet.ip4Header.toString());
                            dataSent = false;
                        }
                    } else {
                        dataSent = false;
                    }

                    // TODO: Sleep-looping is not very battery-friendly, consider blocking instead
                    // Confirm if throughput with ConcurrentQueue is really higher compared to BlockingQueue
                    if (!dataSent)
                        Thread.sleep(10);
                }
            } catch (InterruptedException e) {
                Log.i(TAG, "Stopping");
            } finally {
                closeResources(vpnInput);
            }
        }
    }

    private static class VPNRunnable implements Runnable
    {
        private static final String TAG = VPNRunnable.class.getSimpleName();

        private FileDescriptor vpnFileDescriptor;

        private ConcurrentLinkedQueue<Packet> deviceToNetworkUDPQueue;
        private ConcurrentLinkedQueue<Packet> deviceToNetworkTCPQueue;
        private ConcurrentLinkedQueue<ByteBuffer> networkToDeviceQueue;

        public VPNRunnable(FileDescriptor vpnFileDescriptor,
                           ConcurrentLinkedQueue<Packet> deviceToNetworkUDPQueue,
                           ConcurrentLinkedQueue<Packet> deviceToNetworkTCPQueue,
                           ConcurrentLinkedQueue<ByteBuffer> networkToDeviceQueue)
        {
            this.vpnFileDescriptor = vpnFileDescriptor;
            this.deviceToNetworkUDPQueue = deviceToNetworkUDPQueue;
            this.deviceToNetworkTCPQueue = deviceToNetworkTCPQueue;
            this.networkToDeviceQueue = networkToDeviceQueue;
        }

        @Override
        public void run()
        {
            Log.i(TAG, "Started");

            FileChannel vpnInput = new FileInputStream(vpnFileDescriptor).getChannel();
            FileChannel vpnOutput = new FileOutputStream(vpnFileDescriptor).getChannel();

            try
            {
                ByteBuffer bufferToNetwork = null;
                boolean dataSent = true;
                boolean dataReceived;
                while (!Thread.interrupted())
                {
                    if (dataSent)
                        bufferToNetwork = ByteBufferPool.acquire();
                    else
                        bufferToNetwork.clear();

                    // TODO: Block when not connected
                    int readBytes = vpnInput.read(bufferToNetwork);
                    if (readBytes > 0)
                    {
                        dataSent = true;
                        bufferToNetwork.flip();
                        Packet packet = new Packet(bufferToNetwork);
                        if (packet.isUDP())
                        {
                            deviceToNetworkUDPQueue.offer(packet);
                        }
                        else if (packet.isTCP())
                        {
                            packet.ip4Header.originalDestinationAddress = packet.ip4Header.destinationAddress;
                            packet.tcpHeader.originalDestinationPort = packet.tcpHeader.destinationPort;

                            try {
                                packet.ip4Header.destinationAddress = InetAddress.getByName(VPN_ADDRESS);
                            } catch (UnknownHostException e) {
                                e.printStackTrace();
                            }

                            packet.tcpHeader.destinationPort = proxyServer.getPort();

                            deviceToNetworkTCPQueue.offer(packet);
                        }
                        else
                        {
                            Log.w(TAG, "Unknown packet type");
                            Log.w(TAG, packet.ip4Header.toString());
                            dataSent = false;
                        }
                    }
                    else
                    {
                        dataSent = false;
                    }

                    ByteBuffer bufferFromNetwork = networkToDeviceQueue.poll();
                    if (bufferFromNetwork != null)
                    {
                        bufferFromNetwork.flip();
                        try {
                            while (bufferFromNetwork.hasRemaining())
                                vpnOutput.write(bufferFromNetwork);
                        } catch (IOException e) {

                        }
                        dataReceived = true;

                        ByteBufferPool.release(bufferFromNetwork);
                    }
                    else
                    {
                        dataReceived = false;
                    }

                    // TODO: Sleep-looping is not very battery-friendly, consider blocking instead
                    // Confirm if throughput with ConcurrentQueue is really higher compared to BlockingQueue
                    if (!dataSent && !dataReceived)
                        Thread.sleep(10);
                }
            }
            catch (InterruptedException e)
            {
                Log.i(TAG, "Stopping");
            }
            catch (IOException e)
            {
                Log.w(TAG, e.toString(), e);
            }
            finally
            {
                closeResources(vpnInput, vpnOutput);
            }
        }
    }
}
