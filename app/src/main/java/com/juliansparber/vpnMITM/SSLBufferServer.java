package com.juliansparber.vpnMITM;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;
import java.util.Arrays;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import xyz.hexene.localvpn.LocalVPN;
import xyz.hexene.localvpn.LocalVPNService;

import static com.juliansparber.vpnMITM.BufferServer.sendLog;

public class SSLBufferServer implements Runnable {

    private static final String TAG = SSLBufferServer.class.getSimpleName();
    private final ServerSocket mServerSocket;
    private final String destHost;
    private final int destPort;
    private final String packageName;
    private LocalVPNService vpnService;


    public SSLBufferServer(String destHost, int destPort, String packageName, LocalVPNService vpnService) throws IOException {
        this.mServerSocket = new ServerSocket(0);
        this.destHost = destHost;
        this.destPort = destPort;
        this.vpnService = vpnService;
        this.packageName = packageName;
    }

    /**
     * @param myContext
     * @param s
     * @return
     */
    private SSLSocket createSSLSocket(Context myContext, Socket s) {
        SSLSocket res = null;
        InputStream keyfile = null;
        AssetManager mngr = myContext.getAssets();

        try {
            keyfile = mngr.open("keystore.bks");
        } catch (IOException e) {
            e.printStackTrace();
        }

        char keystorepass[] = "password".toCharArray();
        char keypassword[] = "password".toCharArray();

        try {
            String keyStoreType = KeyStore.getDefaultType();
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);

            keyStore.load(keyfile, keystorepass);

            String keyalg = KeyManagerFactory.getDefaultAlgorithm();
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(keyalg);

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

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public int getPort() {
        return this.mServerSocket.getLocalPort();
    }

    public Thread start() {
        Thread t = new Thread(this);
        t.start();
        return t;
    }

    public void stop() {
        try {
            mServerSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.stop();
    }

    @Override
    public void run() { // run the service
        try {
            handle(mServerSocket.accept());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void handle(Socket clientSocket) {
        OutputStream outputClient = null;
        InputStream inputClient = null;
        OutputStream outputServer = null;
        InputStream inputServer = null;
        Socket serverSocket = null;
        Boolean sslHandshakeError = false;


        String app = "unknown";
        if (packageName != null) {
            app = new AppInfo(packageName).label;
        }
        //clientSocket -> (middleSocket -- middelServerSocket) -> serverSocket
        try {
            serverSocket = new Socket(destHost, destPort);
            vpnService.protect(serverSocket);

            clientSocket = createSSLSocket(LocalVPN.getAppContext(), clientSocket);

            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            serverSocket = factory.createSocket(serverSocket, destHost, destPort, false);
            vpnService.protect(serverSocket);

            outputClient = clientSocket.getOutputStream();
            inputClient = clientSocket.getInputStream();

            outputServer = serverSocket.getOutputStream();
            inputServer = serverSocket.getInputStream();

        } catch (SSLHandshakeException e) {
            String[] msg = Arrays.copyOf(examinateSSLHandshakeError(e, app), 4);
            sendLog(msg[0] + ": " + msg[1]);
            msg[2] = packageName;
            msg[3] = destHost + ":" + destPort;
            Messenger.showAlert(msg);
            sslHandshakeError = true;
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            sslHandshakeError = true;
        }

        if (!sslHandshakeError) {
            //Alert the user that there was no handshake error
            String[] msg = Arrays.copyOf(examinateSSLHandshakeError(null, app), 4);

            sendLog(msg[0] + ": " + msg[1]);
            msg[2] = packageName;
            msg[3] = destHost + ":" + destPort;
            Messenger.showAlert(msg);
            sslHandshakeError = true;
        }

        //actually useless code
        if (!sslHandshakeError) {
            //Create threads for the pipes
            Thread oneWay = BufferServer.pipe("Pipe:SSL->network", clientSocket, serverSocket, inputClient, outputServer);
            Thread otherWay = BufferServer.pipe("Pipe:network->SSL", clientSocket, serverSocket, inputServer, outputClient);

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
            }
        } else {
            try {
                serverSocket.close();
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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

    private String[] examinateSSLHandshakeError(SSLHandshakeException e, String app) {
        String title = "maybe Good news";
        String body = "random SSL handshake error";
        if (e == null) {
            title = "Bad news";
            body = app + " uses a SSL connection which accepts invalid certificates";
        } else if (e.getCause() == null) {
            title = "Unknown Error";
            body = app + ": " + e.getMessage();
        } else {
            if (e.getCause().getMessage().contains("ALERT_UNKNOWN_CA")) {
                title = "Good news";
                body = app + " does not accept invalid certificates.";
            } else if (e.getCause().getMessage().contains("ALERT_CERTIFICATE_UNKNOWN")) {
                title = "Good news";
                body = app + " does not accept unknown certificates.";
            } else if (e.getCause().getMessage().contains("WRONG_VERSION_NUMBER")) {
                title = "Unknown Error";
                body = app + " has a wrong ssl version.";

            } else if (e.getCause().getMessage().contains("HTTP_REQUEST")) {
                title = "Bad news";
                body = app + " does use plane http without encryption.";
            } else {
                title = "Unknown Error";
                body = app + ": " + e.getMessage();
            }
        }
        body += "\nConnection to " + destHost + ":" + destPort;
        return new String[]{title, body};
    }


}
