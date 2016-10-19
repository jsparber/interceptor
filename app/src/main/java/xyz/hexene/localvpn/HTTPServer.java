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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
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
    private Handler mHandler;

    /**
     * The port number we listen to
     */
    private final int mPort;


    /**
     * True if the server is running.
     */
    private boolean mIsRunning;

    /**
     * The {@link java.net.ServerSocket} that we listen to.
     */
    private ServerSocket mServerSocket;

    /**
     * WebServer constructor.
     */
    public HTTPServer(int port, Handler mHandler) {
        mPort = port;
        this.mHandler = mHandler;
    }

    /**
     * This method starts the web server listening to the specified port.
     */
    public void start() {
        mIsRunning = true;
        new Thread(this).start();
    }

    /**
     * This method stops the web server
     */
    public void stop() {
        try {
            mIsRunning = false;
            if (null != mServerSocket) {
                mServerSocket.close();
                mServerSocket = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing the server socket.", e);
        }
    }

    @Override
    public void run() {
        try {
            mServerSocket = new ServerSocket(mPort);
            while (mIsRunning) {
                Socket socket = mServerSocket.accept();
                handle(socket);
                socket.close();
            }
        } catch (SocketException e) {
            // The server was stopped; ignore.
        } catch (IOException e) {
            Log.e(TAG, "Web server error.", e);
            Log.e(TAG, e.toString());
        }
    }

    /**
     * Respond to a request from a client.
     *
     * @param socket The client socket.
     * @throws IOException
     */
    private void handle(Socket socket) throws IOException {
        BufferedReader reader = null;
        //PrintStream output = null;
        OutputStream output = null;
        Map<String, String> requestHeader = new HashMap<String, String>();
        sendLog("\nNew request:\n");
        try {
            String route = null;
            String host = null;
            // Read HTTP headers and parse out the route.
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
            output = socket.getOutputStream();
            URL url = new URL("http://" + requestHeader.get("Host") + "/" + route);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setInstanceFollowRedirects(false);
            HttpURLConnection.setFollowRedirects(false);

            //urlConnection.setInstanceFollowRedirects(false);
            for (Map.Entry<String,String> entry : requestHeader.entrySet()) {
                urlConnection.setRequestProperty(entry.getKey(), entry.getValue());
            }
            //urlConnection.connect();
            try {

                //Write header
                Map<String, List<String> > header = urlConnection.getHeaderFields();

                for (Map.Entry<String, List<String>> entry : header.entrySet()) {

                    String key = (entry.getKey() == null) ? "" : (entry.getKey() + ": ");
                    if (entry.getKey() != null && entry.getKey().equals("Transfer-Encoding") && !entry.getValue().get(0).equals("chunked"))
                        output.write(("Transfer-Encoding: chunked\r\n").getBytes());
                    else
                        output.write((key + entry.getValue().get(0) + "\r\n").getBytes());
                }

                if (header.get("Content-Length") == null && header.get("Transfer-Encoding") == null)
                    output.write(("Transfer-Encoding: chunked\r\n").getBytes());
                //Add a empty line to separate header from content

                output.write("\r\n".getBytes());

                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                    byte[] buffer = new byte[1024];
                    int len = in.read(buffer);
                    while (len != -1) {
                        if (header.get("Content-Length") == null)
                            output.write((Integer.toHexString(len).toUpperCase() + "\r\n").getBytes());
                        output.write(buffer, 0, len);
                        if (header.get("Content-Length") == null)
                            output.write("\r\n".getBytes());
                        len = in.read(buffer);
                }

                if (header.get("Content-Length") == null) {
                    output.write("0\r\n".getBytes());
                    output.write("\r\n".getBytes());
                }
            } finally {
                urlConnection.disconnect();
            }


            // Output stream that we send the response to
            //output = socket.getOutputStream();
            //output = new PrintStream(socket.getOutputStream());

            //byte[] bytes = "I intercepted your request\n".getBytes();

            // Send out the content.
            /*output.println("HTTP/1.0 200 OK");
            output.println("Content-Type: " + "text/html");
            output.println("Content-Length: " + bytes.length);
            output.println();
            */

            //output.write(bytes);
            output.flush();
        } finally {
            if (null != output) {
                output.close();
            }
            if (null != reader) {
                reader.close();
            }
        }
    }

    private void sendLog(String output) {
        Log.d(TAG, output);
        Message msg = Message.obtain();
        msg.obj = output;// Some Arbitrary object
        this.mHandler.sendMessage(msg);
    }
}
