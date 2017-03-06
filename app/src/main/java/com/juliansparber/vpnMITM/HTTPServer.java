package com.juliansparber.vpnMITM;

import android.content.Context;
import android.content.res.AssetManager;
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
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLProtocolException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import xyz.hexene.localvpn.LocalVPN;
import xyz.hexene.localvpn.LoggerOutput;

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
        //mServerSocket = createSSLServer(LocalVPN.getAppContext(), port);
        try {
            mServerSocket = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //for non ssl traffic
        //mServerSocket = new ServerSo
    }

    public SSLServerSocket createSSLServer(Context myContext, int port) {
        SSLServerSocket res = null;
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

            SSLServerSocketFactory ssf = null;
            try {
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(keyStore);
                SSLContext ctx = SSLContext.getInstance("TLS");
                ctx.init(km, trustManagerFactory.getTrustManagers(), null);
                ssf = ctx.getServerSocketFactory();
            } catch (Exception e) {
                throw new IOException(e.getMessage());
            }
            res = (SSLServerSocket)ssf.createServerSocket(port);

            res.setEnabledProtocols(res.getSupportedProtocols());
            res.setEnabledCipherSuites(res.getSupportedCipherSuites());
            return res;

        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
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
        Socket socket = null;
        try {
            socket = mServerSocket.accept();
            //socket.setEnabledCipherSuites(socket.getSupportedCipherSuites());
            handle(socket, false);

        } catch (SSLHandshakeException e) {
            sendLog("SSL handshake error (that's a good thing) \nor it was no ssl traffic and thats bad!");
            try {
                handle(socket, false);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            Log.d(TAG, e.toString());
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
    private void handle(Socket clientSocket, Boolean sslConnection) throws IOException {
        OutputStream outputClient = null;
        InputStream inputClient = null;
        OutputStream outputServer = null;
        InputStream inputServer = null;
        Socket serverSocket = null;

        if (this.originalDestinationAddress == null || this.originalDestinationPort == 0) {
            //this.originalDestinationAddress = InetAddress.getByName("109.68.230.138");
            //this.originalDestinationPort = 80;
        }

        //serverSocket = new Socket(this.originalDestinationAddress, this.originalDestinationPort);

        sslConnection = true;
        OracleServer middleServer = new OracleServer(this.originalDestinationAddress, this.originalDestinationPort, sslConnection);
        middleServer.start();
        serverSocket = new Socket("127.0.0.1", middleServer.getPort());

        if (sslConnection) {
            clientSocket = createSSLSocket(LocalVPN.getAppContext(), clientSocket);
        }

        /*if (sslConnection) {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            //serverSocket = factory.createSocket(serverSocket, this.originalDestinationAddress.getHostAddress(), this.originalDestinationPort, false);
            serverSocket = factory.createSocket(serverSocket, "127.0.0.1", middleServer.getPort(), false);
        }
        */
        sendLog("\nNew request:\n");

        outputClient = clientSocket.getOutputStream();
        inputClient = clientSocket.getInputStream();

        outputServer = serverSocket.getOutputStream();
        inputServer = serverSocket.getInputStream();

        //Create threads for the pipes
        //from phone to middle
        Thread oneWay = pipe(inputClient, outputServer, "onwWay");
        //form middle to phone
        Thread otherWay = pipe(inputServer, outputClient, " seoundway");

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
    private Thread pipe(final InputStream in, final OutputStream out, final String threadTag) {
        final byte[] buffer = new byte[16384];
        Thread runner = new Thread(new Runnable() {
            public void run() {
                Log.d(TAG, "Start pipe");
                int len = 0;
                int i = 0;
                try {
                    len = in.read(buffer);
                } catch (IOException e) {
                }
                try {
                    while (out != null && in != null) {
                        i++;
                        logTraffic(buffer, threadTag+i);
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

    private void logTraffic(byte[] buffer, String threadTag) {
        BufferedReader bfReader = null;
        String line = null;
        bfReader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(buffer)));
        try {
            String temp = null;
            while ((line = bfReader.readLine()) != null) {
                if (!TextUtils.isEmpty(line))
                    sendLog("HTTPSSERVER_LINES" + threadTag + line);
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
