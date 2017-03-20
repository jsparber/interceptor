package com.juliansparber.vpnMITM;

import java.util.HashMap;

/**
 * Created by jSparber on 3/18/17.
 */

public class SharedProxyInfo {
    //PORT, proxy object
    public static HashMap<Integer, String> portRedirection = new HashMap<>();
    public static HashMap<Integer, TrafficBlocker> blocker = new HashMap<Integer, TrafficBlocker>();
    public SharedProxyInfo() {
    }
}
