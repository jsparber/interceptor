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

public class SSLServer implements Runnable {

    public static final String BROADCAST_HTTP_LOG = "com.juliansparber.vpnMITM.SSL_SERVER";
    private static final String TAG = "SSLServer";
    public int elementToRemove;
    /**
     * The {@link ServerSocket} that we listen to.
     */
    private ServerSocket mServerSocket;
    private InetAddress originalDestinationAddress;
    private int originalDestinationPort;

    public SSLServer(InetAddress destAddr, int destPort, int elementToRemove) {
        this.originalDestinationAddress = destAddr;
        this.originalDestinationPort = destPort;
        this.elementToRemove = elementToRemove;
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
        Socket socket = null;
        try {
            socket = mServerSocket.accept();
        } catch (IOException e) {
            e.printStackTrace();
        }
        handle(socket);
        Log.d(TAG, "Channel closed");
        stop();
    }

    /**
     * Respond to a request from a client.
     *
     * @param clientSocket The client socket.
     * @throws IOException
     */
    private void handle(Socket clientSocket) {
        OutputStream outputClient = null;
        InputStream inputClient = null;
        OutputStream outputServer = null;
        InputStream inputServer = null;
        Socket serverSocket = null;

        //clientSocket -> (middleSocket -- middelServerSocket) -> serverSocket
        try {
            serverSocket = new Socket(this.originalDestinationAddress, this.originalDestinationPort);

            clientSocket = createSSLSocket(LocalVPN.getAppContext(), clientSocket);

            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            serverSocket = factory.createSocket(serverSocket, this.originalDestinationAddress.getHostAddress(), this.originalDestinationPort, false);
            outputClient = clientSocket.getOutputStream();
            inputClient = clientSocket.getInputStream();

            outputServer = serverSocket.getOutputStream();
            inputServer = serverSocket.getInputStream();

        } catch (SSLHandshakeException e) {
            String[] msg = examinateSSLHandshakeError(e);
            sendLog(msg[0] + ": " + msg[1]);
            Messenger.showAlert(msg[0], msg[1], 0);
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG, "ERROR");
            Log.e(TAG, "Web server error.", e);
            Log.e(TAG, e.toString());
        }


        //should wait for user action
        TrafficBlocker blocker = new TrafficBlocker();
        String [] msg = examinateSSLHandshakeError(null);

        sendLog(msg[0] + ": " + msg[1]);
        Messenger.showAlert(msg[0], msg[1], elementToRemove);
        //wait for user interaction

        /*synchronized (SharedProxyInfo.blocker) {
            SharedProxyInfo.blocker.put(elementToRemove, blocker);
        }
        */
        //blocker.doWait();

        //Create threads for the pipes
        //from phone to middle
        /*Thread oneWay = BufferServer.pipe(clientSocket, inputClient, outputServer);
        //form middle to phone
        Thread otherWay = BufferServer.pipe(clientSocket, inputServer, outputClient);


        oneWay.start();
        otherWay.start();

        //wait for the pipes to finish
        try {
            oneWay.join();
            Log.d(TAG, "oneWay has joined");
            otherWay.join();
            Log.d(TAG, "otherWay has joined");
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            // serverSocket.close();
            // clientSocket.close();
        }
        */
    }

    private void dropTraffic(InputStream in) {
        int len = 0;
        final byte[] buffer = new byte[2000];
        try {
            len = in.read(buffer);
        } catch (IOException e) {
        }
        try {
            while (in != null && len != -1) {
                len = in.read(buffer);
            }

        } catch (IOException e) {
        } catch (ArrayIndexOutOfBoundsException e) {
            //output conection is closed
        } finally {
            try {
                in.close();
            } catch (IOException e) {
            }
        }
    }

    private void logTraffic(byte[] buffer, int len) {
        String output = new String(buffer, 0, len);
        sendLog(output);
    }

    private void sendLog(String output) {
        Log.d(TAG, output);
        Messenger.println(output);
    }

    private static String[] examinateSSLHandshakeError(SSLHandshakeException e) {
        String title = "maybe Good news";
        String body = "random SSL handshake error";
        if (e == null) {
            title = "Bad news";
            body = "It's a SSL connection which accepts invalid certificates";
        }
        else if(e.getCause() == null) {
            title = "Unknown Error";
            body = e.getMessage();
        }
        else {
            if (e.getCause().getMessage().contains("ALERT_UNKNOWN_CA")) {
                title = "Good news";
                body = "This app does not accept invalid certificates";
            } else if (e.getCause().getMessage().contains("ALERT_CERTIFICATE_UNKNOWN")) {
                title = "Good news";
                body = "This app does not accept unknown certificates";
            }
            else {
                title ="Unknown Error";
                body = e.getMessage();
            }
        }
        return new String[] {title, body};
    }

}
