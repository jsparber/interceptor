package com.juliansparber.vpnMITM;

import android.util.Log;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.concurrent.locks.Lock;

public class HTTPServer implements Runnable {

    public static final String BROADCAST_HTTP_LOG = "xyz.hexene.localvpn.HTTP_SERVER";
    private static final String TAG = "HTTPServer";
    public InetAddress originalDestinationAddress = null;
    public int originalDestinationPort = 0;
    private HashMap<Integer, Integer> proxyPorts = null;
    private int elementToremove;
    public static TrafficBlocker blocker = new TrafficBlocker();
    /**
     * The {@link java.net.ServerSocket} that we listen to.
     */
    //private SSLServerSocket mServerSocket;
    private ServerSocket mServerSocket;

    /**
     * WebServer constructor.
     */
    public HTTPServer(int port, InetAddress orgDestAddr, int origPort, HashMap<Integer, Integer> proxyPorts, int sourcePort) {
        this.proxyPorts = proxyPorts;
        this.elementToremove = sourcePort;
        this.originalDestinationAddress = orgDestAddr;
        this.originalDestinationPort = origPort;

        try {
            mServerSocket = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * This method starts the web server listening to the specified port.
     */
    public Thread start() {
        Thread thread = new Thread(this);
        thread.start();
        return thread;
    }


    /**
     * This method stops the web server
     */
    public void stop() {
        try {
            if (proxyPorts != null)
                proxyPorts.remove(elementToremove);
            if (null != mServerSocket) {
                mServerSocket.close();
                mServerSocket = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing the server socket.", e);
        }
    }

    public int getPort() {
        return this.mServerSocket.getLocalPort();
    }

    @Override
    public void run() {
        try {
            Socket socket = mServerSocket.accept();
            handle(socket);
        } catch (IOException e) {
            Log.d(TAG, "ERROR");
            Log.e(TAG, "Web server error.", e);
            Log.e(TAG, e.toString());
        }
        Log.d(TAG, "Channel closed");
        stop();
    }

    /**
     * Respond to a request from a client.
     *
     * @param clientSocket The client socket.
     * @throws IOException
     */
    private void handle(Socket clientSocket) throws IOException {
        OutputStream outputClient = null;
        InputStream inputClient = null;
        OutputStream outputServer = null;
        InputStream inputServer = null;
        Socket serverSocket = null;
        Boolean sslConnection = false;

        outputClient = clientSocket.getOutputStream();
        inputClient = clientSocket.getInputStream();

        sslConnection = false;

        //Recive first bytes of a msg to check if it is ssl traffic
        int len = 0;
        //First bytes from a ssl msg
        //SSL 3.0 or TLS 1.0, 1.1 and 1.2
        /*
        b[0] == 0x16 (message type "SSL handshake")
        b[1] should be 0x03 (currently newest major version, but who knows in future?)
        b[5] must be 0x01 (handshake protocol message "HelloClient")
        */

        final byte[] buffer = new byte[6];
        //while (len != -1) {
            len = inputClient.read(buffer, 0, 6);
         //   new String (buffer);
        //}
        if (len != -1) {
            //if (Arrays.equals(firstSSLBytes, buffer)) {
            if (buffer[0] == 0x16 && buffer[5] == 0x01) {
                Log.d(TAG, "It is ssl traffic");
                sslConnection = true;
           }
        }


        //Create real server with an ssl socket or a pure socket based on the first bytes of the msg
        OracleServer middleServer = new OracleServer(this.originalDestinationAddress, this.originalDestinationPort, sslConnection);
        middleServer.start();
        serverSocket = new Socket("127.0.0.1", middleServer.getPort());

        sendLog("\nNew request:\n");

        if (!sslConnection) {
            sendLog("It's not a ssl connection witch is quite bad");
            Messenger.showAlert("Bad news", "This app does not use SSL encryption. Should the traffic be blocked?", blocker);
        }
        //wait for user interaction
        blocker.doWait();
        if (!blocker.blockTraffic) {
            outputServer = serverSocket.getOutputStream();
            inputServer = serverSocket.getInputStream();

            //Create threads for the pipes
            //from phone to middle
            Thread oneWay = pipe(inputClient, outputServer, buffer, len);
            //form middle to phone
            Thread otherWay = pipe(inputServer, outputClient, null, -1);

            oneWay.start();
            otherWay.start();

            //wait for the pipes to finish
            try {
                oneWay.join();
                otherWay.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                serverSocket.close();
                clientSocket.close();
            }
        }
        else {
            serverSocket.close();
            clientSocket.close();
        }
    }

    private void restartConnection() {

    }

    //maybe should use pipedInputStream and pipedOutputStream
    private Thread pipe(final InputStream in, final OutputStream out, final byte[] preBuffer, final int preBufferLen) {
        //Buffer size 16384
        final byte[] buffer = new byte[2000];
        Thread runner = new Thread(new Runnable() {
            public void run() {
                Log.d(TAG, "Start pipe");
                int len = 0;
                //write the first byte to the oracleServer
                if (preBuffer != null && preBufferLen != -1) {
                    System.arraycopy(preBuffer,0, buffer, 0, preBufferLen);
                    len = preBufferLen;
                }
                else {
                    try {
                        len = in.read(buffer);
                    } catch (IOException e) {
                    }
                }
                try {
                    while (out != null && in != null && len != -1) {
                        out.write(buffer, 0, len);
                        len = in.read(buffer);
                    }

                } catch (IOException e) {
                } catch (ArrayIndexOutOfBoundsException e) {
                    //output conection is closed
                } finally {
                    try {
                        in.close();
                        out.close();
                    } catch (IOException e) {
                    }
                }
            }
        });
        return runner;
    }

    private void sendLog(String output) {
        Log.d(TAG, output);
        Messenger.println(output);
    }
}
