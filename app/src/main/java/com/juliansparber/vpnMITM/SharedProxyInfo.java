package com.juliansparber.vpnMITM;

import java.util.HashMap;

/**
 * Created by jSparber on 3/18/17.
 */

public class SharedProxyInfo {
    //PORT, proxy object
    public static HashMap<Integer, Integer> portRedirection = new HashMap<Integer, Integer>();
    public static HashMap<Integer, TrafficBlocker> blocker = new HashMap<Integer, TrafficBlocker>();
    public SharedProxyInfo() {
    }

    public static void addNewProxyServer() {

    }
}
