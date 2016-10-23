package xyz.hexene.localvpn;

import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

public class HTTPServer implements Runnable {

    public static final String BROADCAST_HTTP_LOG = "xyz.hexene.localvpn.HTTP_SERVER";
    private static final String TAG = "HTTPServer";
    public InetAddress originalDestinationAddress = null;
    public int originalDestinationPort = 0;
    private HashMap<Integer, Integer> proxyPorts = null;
    private int elementToremove;

    /**
     * The {@link java.net.ServerSocket} that we listen to.
     */
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

        sendLog("\nNew request:\n");

        outputClient = clientSocket.getOutputStream();
        inputClient = clientSocket.getInputStream();
        outputServer = serverSocket.getOutputStream();
        inputServer = serverSocket.getInputStream();

        //Create threads for the pipes
        Thread oneWay = pipe(inputClient, outputServer);
        Thread otherWay = pipe(inputServer, outputClient);

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

    //maybe should use pipedInputStream and pipedOutputStream
    private Thread pipe(final InputStream in, final OutputStream out) {
        final byte[] buffer = new byte[16384];
        Thread runner = new Thread(new Runnable() {
            public void run() {
                Log.d(TAG, "Start pipe");
                int len = 0;
                try {
                    len = in.read(buffer);
                } catch (IOException e) {
                }
                try {
                    while (out != null && in != null) {
                        logTraffic(buffer);
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
        LoggerOutput.println(output);
    }
}
