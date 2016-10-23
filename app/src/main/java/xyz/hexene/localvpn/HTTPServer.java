/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.hexene.localvpn;

import android.os.Handler;
import android.os.Message;
import android.support.v4.content.res.TypedArrayUtils;
import android.text.TextUtils;
import android.util.Log;
import android.util.StringBuilderPrinter;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of a very basic HTTP server. The contents are loaded from the assets folder. This
 * server handles one request at a time. It only supports GET method.
 * https://developer.android.com/samples/PermissionRequest/src/com.example.android.permissionrequest/SimpleWebServer.html
 */
public class HTTPServer implements Runnable {

    public static final String BROADCAST_HTTP_LOG = "xyz.hexene.localvpn.HTTP_SERVER";
    private static final String TAG = "HTTPServer";
    public InetAddress originalDestinationAddress = null;
    public int originalDestinationPort = 0;
    private HashMap<Integer, Integer> proxyPorts = null;
    private int elementToremove;

    /**
     * The port number we listen to
     */
    private final int mPort;

    /**
     * The {@link java.net.ServerSocket} that we listen to.
     */
    private ServerSocket mServerSocket;

    /**
     * WebServer constructor.
     */
    public HTTPServer(int port, InetAddress orgDestAddr, int origPort, HashMap<Integer, Integer> proxyPorts, int sourcePort) {
        mPort = port;
        this.proxyPorts = proxyPorts;
        this.elementToremove = sourcePort;
        this.originalDestinationAddress = orgDestAddr;
        this.originalDestinationPort = origPort;

        try {
            mServerSocket = new ServerSocket(mPort);
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
        BufferedReader reader = null;
        OutputStream outputClient = null;
        InputStream inputClient = null;
        OutputStream outputServer = null;
        InputStream inputServer = null;
        Socket serverSocket = null;

        serverSocket = new Socket(this.originalDestinationAddress, this.originalDestinationPort);
        //Map<String, String> requestHeader = new HashMap<String, String>();

        sendLog("\nNew request:\n");


        outputClient = clientSocket.getOutputStream();
        inputClient = clientSocket.getInputStream();
        outputServer = serverSocket.getOutputStream();
        inputServer = serverSocket.getInputStream();

        Thread oneWay = pipe(inputClient, outputServer);
        Thread otherWay = pipe(inputServer, outputClient);


        sendLog("\nRequest headers: \n");
        oneWay.start();


        sendLog("\nResposne headers: \n");

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

            /*
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line;
            while (!TextUtils.isEmpty(line = reader.readLine())) {
                if (line.startsWith("GET /")) {
                    int start = line.indexOf('/') + 1;
                    int end = line.indexOf(' ', start);
                    route = line.substring(start, end);
                }
                else {
                    String[] part = line.split(": ");
                    if (part.length == 2)
                        requestHeader.put(part[0], part[1]);
                }

                Log.d(TAG, line);
                sendLog("   " + line + "\n");
            }

            for (Map.Entry<String,String> entry : requestHeader.entrySet()) {
                urlConnection.setRequestProperty(entry.getKey(), entry.getValue());
            }
                for (Map.Entry<String, List<String>> entry : header.entrySet()) {

                    String key = (entry.getKey() == null) ? "" : (entry.getKey() + ": ");
                    if (entry.getKey() != null && entry.getKey().equals("Transfer-Encoding") && !entry.getValue().get(0).equals("chunked"))
                        output.write(("Transfer-Encoding: chunked\r\n").getBytes());
                    else
                        output.write((key + entry.getValue().get(0) + "\r\n").getBytes());
                }

    }
    */


    //maby should use pipedInputStream and pipedOutputStream
    private Thread pipe(final InputStream in, final OutputStream out) {
        final byte[] buffer = new byte[16384];
        Thread runner = new Thread(new Runnable() {
            public void run() {
                Log.d(TAG, "Start pipe");
                int len = 0;
                try {
                    len = in.read(buffer);
                } catch (IOException e) {
                    //e.printStackTrace();
                }
                try {
                    while (out != null && in != null) {
                        logTraffic(buffer);
                        out.write(buffer, 0, len);
                        len = in.read(buffer);
                    }

                } catch (IOException e) {
                    //e.printStackTrace();
                } catch (ArrayIndexOutOfBoundsException e) {
                    //output conection is closed
                    //e.printStackTrace();
                } finally {
                    try {
                        in.close();
                        out.close();
                    } catch (IOException e) {
                        //e.printStackTrace();
                    }

                }
            }
        });
        return runner;
    }

    private void logTraffic(byte[] buffer) {
        BufferedReader bfReader = null;
        String line = null;
        bfReader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(buffer)));
        try {
            String temp = null;
            while ((line = bfReader.readLine()) != null) {
                if (!TextUtils.isEmpty(line))
                    sendLog(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void sendLog(String output) {
        Log.d(TAG, output);
        //Message msg = Message.obtain();
        LoggerOutput.println(output);
        //msg.obj = output;// Some Arbitrary object
        //this.mHandler.sendMessage(msg);
    }
}
