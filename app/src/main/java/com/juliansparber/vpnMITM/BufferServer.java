package com.juliansparber.vpnMITM;

import android.util.Log;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLHandshakeException;

import xyz.hexene.localvpn.TCB;

public class BufferServer implements Runnable {

    public static final String BROADCAST_HTTP_LOG = "com.juliansparber.vpnMITM.BUFFER_SERVER";
    private static final String TAG = "BufferServer";
    private final ExecutorService pool;
    /**
     * The {@link java.net.ServerSocket} that we listen to.
     */
    //private SSLServerSocket mServerSocket;
    private ServerSocket mServerSocket;

    /**
     * WebServer constructor.
     */
    public BufferServer(int port, int poolSize) throws IOException {
        mServerSocket = new ServerSocket(port);
        pool = Executors.newFixedThreadPool(poolSize);
        start();
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

    /*@Override
    public void run() {
        try {
            Socket socket = mServerSocket.accept();
            start();
            handle(socket);
        } catch (IOException e) {
            Log.d(TAG, "ERROR");
            Log.e(TAG, "Web server error.", e);
            Log.e(TAG, e.toString());
        }
        Log.d(TAG, "Channel closed");
        stop();
    }
    */

    @Override
    public void run() { // run the service
        try {
            for (;;) {
                pool.execute(new Handler(mServerSocket.accept()));
            }
        } catch (IOException ex) {
            pool.shutdown();
        }
    }

    class Handler implements Runnable {
        private final Socket socket;

        Handler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                handle(this.socket);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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

        //Read first bytes of message
        int len = 0;
        final byte[] buffer = new byte[100];
        len = inputClient.read(buffer);
        //Log.d(TAG, "PORT: " + this.getPort());

        /*
        //Create a ssl socket and try to perform handshake
        SSLServer middleServer = new SSLServer(this.originalDestinationAddress, this.originalDestinationPort, elementToRemove);
        middleServer.start();
        //Connect to the SSLServer
        serverSocket = new Socket("127.0.0.1", middleServer.getPort());
        */

        //When SSL handshake fails because it is not ssl traffic create a normal socket and
        // inject the first bytes which are read from inputStream for the SSL handshake
        //
        int port = ((InetSocketAddress) clientSocket.getRemoteSocketAddress()).getPort();
        String tmp = SharedProxyInfo.portRedirection.get(clientSocket.getPort());
        if (tmp != null) {
            serverSocket = new Socket(tmp.split(":")[0].substring(1), Integer.parseInt(tmp.split(":")[1]));
        }

        sendLog("New request:\n" +
                "First bytes:\n" +
                new String(buffer) + "\n");


        /*TrafficBlocker blocker = new TrafficBlocker();
        if (!sslConnection) {
            sendLog("It's not a ssl connection witch is quite bad");
            Messenger.showAlert("Bad news", "This app does not use SSL encryption. Should the traffic be blocked?", elementToRemove);
            //wait for user interaction

            synchronized (SharedProxyInfo.blocker) {
                SharedProxyInfo.blocker.put(elementToRemove, blocker);
            }
            blocker.doWait();

        }
        */

        outputServer = serverSocket.getOutputStream();
        inputServer = serverSocket.getInputStream();

        //Create threads for the pipes
        //from phone to middle
        Thread oneWay = pipe(clientSocket, inputClient, outputServer, buffer, len);
        //form middle to phone
        Thread otherWay = pipe(clientSocket, inputServer, outputClient);

        oneWay.start();
        otherWay.start();

        //wait for the pipes to finish
        try {
            oneWay.join();
            //Log.d(TAG+this.getPort(), "oneWay has joined");
            otherWay.join();
           // Log.d(TAG+this.getPort(), "otherWay has joined");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }// finally {
        //Should close sockets only if the client requests it
        //   serverSocket.close();
        //   clientSocket.close();
        //}
            /*
            outputClient.write(("HTTP/1.1 403 Forbidden\n" +
                    "Content-Length: 1\n" +
                    "Connection: close\n\n\r").getBytes());
            dropTraffic(inputClient);
            outputClient.close();
            serverSocket.close();
            clientSocket.close();
            */
    }


    public Thread pipe(Socket clientSocket, InputStream inputClient, OutputStream outputServer) {
        return pipe(clientSocket, inputClient, outputServer, null, -1);
    }

    //maybe should use pipedInputStream and pipedOutputStream
    public Thread pipe(final Socket clientSocket, final InputStream in, final OutputStream out, final byte[] preBuffer, final int preBufferLen) {
        //Buffer size 16384
        final Thread runner = new Thread(new Runnable() {
            public void run() {
                Log.d(TAG, "Start pipe");
                byte[] buffer = new byte[2000];
                boolean error = false;
                //write cached first Bytes to the outputStream
                if (preBuffer != null && preBufferLen != -1) {
                    try {
                        out.write(preBuffer, 0, preBufferLen);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                int len = 0;
//                while (!clientSocket.isClosed() && clientSocket.isConnected() && !error) {
                try {
                    while (len != -1) {
                        len = in.read(buffer);
                        //if there is data to write, write it to the OutputStream
                        if (len != -1)
                            out.write(buffer, 0, len);
                    }
                } catch (SocketException e) {
                    Log.d(TAG,"Socket Exeption");
                    Log.d(TAG, clientSocket.toString());
                } catch (NullPointerException e) {
                    //close all conections actually should never happen
                    if (in == null)
                        Log.d(TAG, "Server has closed socked");
                    else if (out == null)
                        Log.d(TAG, "Client has closed socked");
                    else
                        e.printStackTrace();
                    error = true;
                } catch (IOException e) {
                    e.printStackTrace();
                    error = true;
                } catch (ArrayIndexOutOfBoundsException e) {
                    //output conection is closed
                    e.printStackTrace();
                    error = true;
                }
                //               }
            }
        });
        return runner;
    }


    private void sendLog(String output) {
        Log.d(TAG, output);
        Messenger.println(output);
    }

}
