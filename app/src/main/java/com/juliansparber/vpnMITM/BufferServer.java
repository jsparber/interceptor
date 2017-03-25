package com.juliansparber.vpnMITM;

import android.util.Log;


import org.secuso.privacyfriendlynetmonitor.ConnectionAnalysis.Collector;
import org.secuso.privacyfriendlynetmonitor.ConnectionAnalysis.Detector;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import xyz.hexene.localvpn.LocalVPNService;
import xyz.hexene.localvpn.Packet;
import xyz.hexene.localvpn.TCB;

public class BufferServer implements Runnable {

    private static final String TAG = BufferServer.class.getSimpleName();
    private final ExecutorService pool;
    protected final ServerSocket mServerSocket;
    private LocalVPNService vpnService;

    /**
     * WebServer constructor.
     */
    public BufferServer(int port, int poolSize, LocalVPNService localVPNService) throws IOException {
        this.mServerSocket = new ServerSocket(port);
        this.pool = Executors.newFixedThreadPool(poolSize);
        this.vpnService = localVPNService;
    }

    public int getPort() {
        return this.mServerSocket.getLocalPort();
    }

    @Override
    public void run() { // run the service
        try {
            for (;;) {
                pool.execute(new Handler(mServerSocket.accept()));
            }
        } catch (IOException ex) {
            pool.shutdown();
            try {
                mServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
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

    protected void handle(Socket clientSocket) throws IOException {
        OutputStream outputClient = null;
        InputStream inputClient = null;
        OutputStream outputServer = null;
        InputStream inputServer = null;
        Socket serverSocket = null;
        Boolean allowTraffic = false;

        outputClient = clientSocket.getOutputStream();
        inputClient = clientSocket.getInputStream();


        //Read first bytes of message
        int len = 0;
        final byte[] buffer = new byte[100];
        len = inputClient.read(buffer);

        String[] ipAndPort = SharedProxyInfo.getPortRedirection(clientSocket.getPort()).split(":");
        String originalHost = ipAndPort[0];
        int originalPort = Integer.parseInt(ipAndPort[1]);
        int sourcePort = Integer.parseInt(ipAndPort[3]);
        if(SharedProxyInfo.getAllowedConnections(originalHost + ":" + originalPort) != null)
            allowTraffic = true;

        if (!allowTraffic) {
            Detector.updateReportMap();
            HashMap<String, String> connLookup = Collector.provideConnectionLookup();
            String packageName = connLookup.get(originalHost + ":" + originalPort + ":" + sourcePort);

            //Create a ssl socket and try to perform handshake
            try {
                SSLBufferServer middleServer = new SSLBufferServer(originalHost, originalPort, packageName, this.vpnService);
                middleServer.start();
                //Connect to the SSLServer
                serverSocket = new Socket("127.0.0.1", middleServer.getPort());
                Log.d(TAG, "Protect server socket: " + vpnService.protect(serverSocket));
            } catch (IOException e) {
                Log.d(TAG, e.toString());
            }
        }
        else {
            serverSocket = new Socket(originalHost, originalPort);
            Log.d(TAG, "Protect server socket: " + vpnService.protect(serverSocket));
        }

        /*sendLog("New request:\n" +
                "First bytes:\n" +
                new String(buffer) + "\n");
                */

        outputServer = serverSocket.getOutputStream();
        inputServer = serverSocket.getInputStream();

        //Create threads for the pipes
        //from phone to middle
        Thread oneWay = pipe("Pipe:phone->network", clientSocket, serverSocket, inputClient, outputServer, buffer, len);
        //form middle to phone
        Thread otherWay = pipe("Pipe:network->phone", clientSocket, serverSocket, inputServer, outputClient);

        oneWay.start();
        otherWay.start();

        //wait for the pipes to finish
        try {
            otherWay.join();
            oneWay.join();
            //Log.d(TAG+this.getPort(), "oneWay has joined");
            // Log.d(TAG+this.getPort(), "otherWay has joined");
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            //Should close sockets only if the client requests it
            serverSocket.close();
            clientSocket.close();
        }
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


    protected static Thread pipe(String name, Socket clientSocket, Socket serverSocket, InputStream inputClient, OutputStream outputServer) {
        return pipe(name, clientSocket, serverSocket, inputClient, outputServer, null, -1);
    }

    //maybe should use pipedInputStream and pipedOutputStream
    protected static Thread pipe(final String name, final Socket clientSocket, final Socket serverSocket, final InputStream in, final OutputStream out, final byte[] preBuffer, final int preBufferLen) {
        //Buffer size 16384
        final Thread runner = new Thread(new Runnable() {
            public void run() {
                //Log.d(TAG, name + "Start pipe");
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

                while (!error) {
                    int len = 0;
                    try {
                        while (len != -1) {
                            len = in.read(buffer);
                            //if there is data to write, write it to the OutputStream
                            if (len != -1) {
                                //sendLog(new String(buffer));
                                out.write(buffer, 0, len);
                            }
                            else {
                                //Log.d(TAG, name + "Should I close the socket?");
                                error = true;
                            }
                        }
                    } catch (SocketException e) {
                        Log.d(TAG,name + " Socket Exception");
                        Log.d(TAG, clientSocket.toString());
                        error = true;
                    } catch (NullPointerException e) {
                        //close all connections actually should never happen
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
                        //output confection is closed
                        e.printStackTrace();
                        error = true;
                    }
                }
                try {
                    in.close();
                    out.close();
                    serverSocket.close();
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        runner.setName(name);
        return runner;
    }


    protected static void sendLog(String output) {
        Log.d(TAG, output);
        Messenger.println(output);
    }

}
