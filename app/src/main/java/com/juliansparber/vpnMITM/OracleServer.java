package com.juliansparber.vpnMITM;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import xyz.hexene.localvpn.LocalVPN;
import xyz.hexene.localvpn.LoggerOutput;

public class OracleServer implements Runnable {

    public static final String BROADCAST_HTTP_LOG = "com.juliansparber.vpnMITM.ORACEL_SERVER";
    private static final String TAG = "OracelServer";
    /**
     * The {@link ServerSocket} that we listen to.
     */
    private ServerSocket mServerSocket;
    private InetAddress originalDestinationAddress;
    private int originalDestinationPort;
    private boolean sslConnection;

    public OracleServer(InetAddress destAddr, int destPort, boolean sslConnection) {
        this.originalDestinationAddress = destAddr;
        this.originalDestinationPort = destPort;
        this.sslConnection = sslConnection;
        try {
            mServerSocket = new ServerSocket(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public SSLSocket createSSLSocket(Context myContext, Socket s) {
        SSLSocket res = null;
        AssetManager mngr = myContext.getAssets();
        InputStream keyfile = null;
        try {
            keyfile = mngr.open("keystore.bks");
        } catch (IOException e) {
            e.printStackTrace();
        }

        char keystorepass[] = "password".toCharArray();
        char keypassword[] = "password".toCharArray();

        try{
            String keyStoreType = KeyStore.getDefaultType();
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);

            keyStore.load(keyfile, keystorepass);

            String keyalg=KeyManagerFactory.getDefaultAlgorithm();
            KeyManagerFactory kmf=KeyManagerFactory.getInstance(keyalg);

            kmf.init(keyStore, keypassword);
            KeyManager[] km = kmf.getKeyManagers();

            SSLSocketFactory ssf = null;
            try {
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(keyStore);
                SSLContext ctx = SSLContext.getInstance("TLS");
                ctx.init(km, trustManagerFactory.getTrustManagers(), null);
                //ssf = (SSLSocketFactory) SSLSocketFactory.getDefault();
                ssf = ctx.getSocketFactory();
            } catch (Exception e) {
                throw new IOException(e.getMessage());
            }
            res = (SSLSocket) ssf.createSocket(s, s.getInetAddress().getHostName(), s.getPort(), false);
            res.setEnabledProtocols(res.getSupportedProtocols());
            res.setEnabledCipherSuites(res.getSupportedCipherSuites());
            res.setUseClientMode(false);
            return res;

        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    /**
     * This method starts the server listening to a random port.
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

    @Override
    public void run() {
        try {
            Socket socket = mServerSocket.accept();
            handle(socket);
        } catch (SSLHandshakeException e) {
            sendLog("SSL handshake error (that's a good thing)");
            if (e.getCause().getMessage().contains("ALERT_UNKNOWN_CA")) {
                sendLog("This app does not accept invalid certificats");
            }

            if (e.getCause().getMessage().contains("ALERT_CERTIFICATE_UNKNOWN")) {
                sendLog("This app does not accept unknown certificats");
            }
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

        //clientSocket -> (middleSocket -- middelServerSocket) -> serverSocket
        serverSocket = new Socket(this.originalDestinationAddress, this.originalDestinationPort);

        if (this.sslConnection)
            clientSocket = createSSLSocket(LocalVPN.getAppContext(), clientSocket);

        if (this.sslConnection) {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            serverSocket = factory.createSocket(serverSocket, this.originalDestinationAddress.getHostAddress(), this.originalDestinationPort, false);
        }

        outputClient = clientSocket.getOutputStream();
        inputClient = clientSocket.getInputStream();

        outputServer = serverSocket.getOutputStream();
        inputServer = serverSocket.getInputStream();

        //Create threads for the pipes
        //from phone to middle
        Thread oneWay = pipe(inputClient, outputServer);
        //form middle to phone
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
        //i don't know whitch buffer size to use;
        //2000 seams good enough as buffer size
        final byte[] buffer = new byte[2000];
        Thread runner = new Thread(new Runnable() {
            public void run() {
                Log.d(TAG, "Start pipe");
                int len = 0;
                try {
                    len = in.read(buffer);
                } catch (IOException e) {
                }
                try {
                    while (out != null && in != null && len != -1) {
                        logTraffic(buffer, len);
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

    private void logTraffic(byte[] buffer, int len) {
        String output = new String(buffer, 0, len);
        sendLog(output);
    }

    private void sendLog(String output) {
        Log.d(TAG, output);
        LoggerOutput.println(output);
    }
}
