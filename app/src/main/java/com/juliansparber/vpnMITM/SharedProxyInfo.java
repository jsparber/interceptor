package com.juliansparber.vpnMITM;

import java.util.HashMap;

/**
 * Created by jSparber on 3/18/17.
 */

public class SharedProxyInfo {
    //TO-DO remove elements form when they are not anymore needed
    //PORT, proxy object
    private static HashMap<Integer, String> portRedirection = new HashMap<>();
    //IP:PORT -> [true|false]
    private static HashMap<String, Boolean> allowedConnections = new HashMap<String, Boolean>();
    //private static HashMap<String, Boolean> allowedApps = new HashMap<String, Boolean>();

    public static Boolean getAllowedConnections (String ipAndPort) {
        synchronized (allowedConnections) {
            return allowedConnections.get(ipAndPort);
        }
    }

    public static void putAllowedConnections (String ipAndPort) {
        synchronized (allowedConnections) {
            allowedConnections.put(ipAndPort, true);
        }
    }

    public static String getPortRedirection (int port) {
        synchronized (portRedirection) {
            return portRedirection.get(port);
        }
    }

    public static void putPortRedirection (int port, String ipAndPort) {
        synchronized (portRedirection) {
            portRedirection.put(port, ipAndPort);
        }
    }

}
