/*
    Privacy Friendly Net Monitor (Net Monitor)
    - Copyright (2015 - 2017) Felix Tsala Schiller

    ###################################################################

    This file is part of Net Monitor.

    Net Monitor is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Net Monitor is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Net Monitor.  If not, see <http://www.gnu.org/licenses/>.

    Diese Datei ist Teil von Net Monitor.

    Net Monitor ist Freie Software: Sie können es unter den Bedingungen
    der GNU General Public License, wie von der Free Software Foundation,
    Version 3 der Lizenz oder (nach Ihrer Wahl) jeder späteren
    veröffentlichten Version, weiterverbreiten und/oder modifizieren.

    Net Monitor wird in der Hoffnung, dass es nützlich sein wird, aber
    OHNE JEDE GEWÄHRLEISTUNG, bereitgestellt; sogar ohne die implizite
    Gewährleistung der MARKTFÄHIGKEIT oder EIGNUNG FÜR EINEN BESTIMMTEN ZWECK.
    Siehe die GNU General Public License für weitere Details.

    Sie sollten eine Kopie der GNU General Public License zusammen mit diesem
    Programm erhalten haben. Wenn nicht, siehe <http://www.gnu.org/licenses/>.

    ###################################################################

    This app has been created in affiliation with SecUSo-Department of Technische Universität
    Darmstadt.

    Privacy Friendly Net Monitor is based on TLSMetric by Felix Tsala Schiller
    https://bitbucket.org/schillef/tlsmetric/overview.
 */
package org.secuso.privacyfriendlynetmonitor.ConnectionAnalysis;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.compat.BuildConfig;
import android.util.Log;

import org.secuso.privacyfriendlynetmonitor.Assistant.KnownPorts;
import org.secuso.privacyfriendlynetmonitor.Assistant.RunStore;
import org.secuso.privacyfriendlynetmonitor.Assistant.TLType;
import org.secuso.privacyfriendlynetmonitor.Assistant.ToolBox;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;


/**
 * Collector class collects data from the services and processes it for usage with the UI.
 * It handles asynchronous calls to DNS and SSL-Labs services and holds run-time caches for
 * compiled information.
 */
public class Collector {

    private static final String TAG = Collector.class.getSimpleName();
    //application caches
    private static HashMap<Integer, PackageInfo> sCachePackage = new HashMap<>();
    private static HashMap<Integer, Drawable> sCacheIcon = new HashMap<>();
    private static HashMap<Integer, String> sCacheLabel = new HashMap<>();
    private static HashMap<String, String> sCacheDNS = new HashMap<>();

    //ReportDetail information
    public static ArrayList<String[]> sDetailReportList = new ArrayList<>();
    public static Report sDetailReport;

    //Data processing maps
    private static ArrayList<Report> sReportList = new ArrayList<>();
    private static HashMap<Integer, List<Report>> mUidReportMap = new HashMap<>();
    private static HashMap<Integer, List<Report>> mFilteredUidReportMap = new HashMap<>();

    //Pushed the newest available information as deep copy.
    public static HashMap<Integer, List<Report>> provideSimpleReports(){
        updateReports();
        mFilteredUidReportMap = filterReports();
        mFilteredUidReportMap = sortMapByLabels();
        return mFilteredUidReportMap;
    }

    public static HashMap<String, String>  provideConnectionLookup() {
        HashMap<String, String> output = new HashMap<>();
        updateReports();
        mFilteredUidReportMap = filterReports();
        mFilteredUidReportMap = sortMapByLabels();
        Set<Integer> keys = mFilteredUidReportMap.keySet();
        for (int key : keys) {
            ArrayList<Report> appReports = (ArrayList<Report>)mFilteredUidReportMap.get(key);
            for (Report r : appReports) {
                String appName = r.packageName;
                //Do not list own traffic
                try {
                    //if (!appName.equals(LocalVPN.getAppContext().getPackageName())) {
                        String address = r.remoteAdd.getHostAddress() + ":" + r.remotePort + ":" + r.localPort;
                    if (output.get(address) == null)
                        output.put(address, appName);
                    else
                        output.put(address + "b", appName);
                    //}
                } catch (NullPointerException e) {
                    Log.e(TAG, "App name should not be emty", e);
                }
            }
        }
        return output;
    }

    //Sort the filtered report map by app labels and app type (system/third-party)
    private static LinkedHashMap<Integer, List<Report>> sortMapByLabels() {
        LinkedHashMap<Integer, List<Report>> sortedMap = new LinkedHashMap<>();
        ArrayList<ArrayList<Report>> reportsApp = new ArrayList<>();
        ArrayList<ArrayList<Report>> reportsSysApp = new ArrayList<>();
        //Sort in sub lists by app-type (System/User)
        Set<Integer> keys = mFilteredUidReportMap.keySet();
        for (int key : keys) {
            ArrayList<Report> appReports = (ArrayList<Report>)mFilteredUidReportMap.get(key);
            if (appReports.get(0).uid > 10000){ reportsApp.add(appReports); }
            else { reportsSysApp.add(appReports); }
        }
        //sort the sub list and append to liked HashMap
        sortListByName(reportsApp);
        sortListByName(reportsSysApp);
        reportsApp.addAll(reportsSysApp);

        //Add to original filter map
        for (int i=0; i< reportsApp.size(); i++){
            sortedMap.put(reportsApp.get(i).get(0).uid, reportsApp.get(i));
        }
        return sortedMap;
    }

    //Do da bubble sort!
    private static void sortListByName(ArrayList<ArrayList<Report>> list) {
        for (int j = list.size(); j > 1; j--) {
            for (int i = 0; i < j - 1; i++) {
                if (getLabel(list.get(i).get(0).uid).compareTo(getLabel(list.get(i+1).get(0).uid)) > 0){
                    ArrayList<Report> tmpList = list.get(i);
                    list.set(i,list.get(i+1));
                    list.set(i+1,tmpList);
                }
            }
        }
    }

    //Generate an overview List, with only one report per remote address per app
    private static HashMap<Integer, List<Report>> filterReports() {
        HashMap<Integer, List<Report>> filteredReportsByApp = new HashMap<>();
        HashSet<String> filterMap = new HashSet<>();
        String address;
        ArrayList<Report> list;
        ArrayList<Report> filteredList;
        for (int key : mUidReportMap.keySet()) {
            filteredReportsByApp.put(key, new ArrayList<Report>());
            list = (ArrayList<Report>) mUidReportMap.get(key);
            filteredList = (ArrayList<Report>) filteredReportsByApp.get(key);
            filterMap.clear();
            for (int i = 0; i < list.size(); i++) {
                address = list.get(i).remoteAdd.getHostAddress();
                if (!filterMap.contains(address)) {
                    filteredList.add(list.get(i));
                    filterMap.add(address);
                }
            }
        }
        return filteredReportsByApp;
    }

    //gets momentual settings from pref manager
    static void updateSettings() {
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(RunStore.getContext());
    }

    //Sequence to collect reports from detector
    private static void updateReports(){
        //update reports
        pull();
        //process reports (passive mode)
        fillPackageInformation();
        //should add here
        //sorting
        sortReportsToMap();
    }


    //Sorts the reports by app package name to a HashMap
    private static void sortReportsToMap() {
        mUidReportMap = new HashMap<>();
        for (int i = 0; i < sReportList.size(); i++) {
            Report r = sReportList.get(i);
            if (!mUidReportMap.containsKey(r.uid)) {
                mUidReportMap.put(r.uid, new ArrayList<Report>());
            }
            mUidReportMap.get(r.uid).add(r);
        }
    }


    //pull records from detector and make a deep copy for frontend - usage
    private static void pull() {
        ArrayList<Report> reportList = new ArrayList<>();
        Set<Integer> keySet = Detector.sReportMap.keySet();
        for (int i : keySet) {
            reportList.add(Detector.sReportMap.get(i));
        }
        sReportList = deepCopyReportList(reportList);
    }

    //Make an async reverse DNS request
    /*public static void resolveHosts() {
        for (int i = 0; i < sReportList.size(); i++){
            Report r = sReportList.get(i);
            if (!hasHostName(r.remoteAdd.getHostAddress())) {
                    String hostName = r.remoteAdd.getHostName();
                    sCacheDNS.put(r.remoteAdd.getHostAddress(), hostName);
            }
        }
    }
    */

    // fill reports with app data from Package Information Cache
    private static void fillPackageInformation() {
        for (int i = 0; i < sReportList.size(); i++) {
            Report r = sReportList.get(i);
            if(!sCachePackage.containsKey(r.uid)) {
                updatePackageCache();
            }
            if(sCachePackage.containsKey(r.uid)){
                PackageInfo pi = sCachePackage.get(r.uid);
                r.appName = pi.applicationInfo.packageName;
                r.packageName = pi.packageName;
            } else {
                r.appName = "Unknown App";
                r.appName = "app.unknown";
            }
        }
    }



    //Make a deep copy of the report list
    private static ArrayList<Report> deepCopyReportList(ArrayList<Report> reportList) {
        ArrayList<Report> cloneList = new ArrayList<>();
        try {
            for (int i = 0; i < reportList.size(); i++) {
                ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                ObjectOutputStream out = new ObjectOutputStream(byteOut);
                out.writeObject(reportList.get(i));
                out.flush();
                ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(byteOut.toByteArray()));
                cloneList.add(Report.class.cast(in.readObject()));
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return cloneList;
    }

    //Updates the PkgInfo hash map with new entries.
    private static void updatePackageCache() {
        sCachePackage = new HashMap<>();
        ArrayList<PackageInfo> infoList = (ArrayList<PackageInfo>) getPackages(RunStore.getContext());
        for (PackageInfo i : infoList) {
            if (i != null) {
                sCachePackage.put(i.applicationInfo.uid, i);
            }
        }
        addSysPackage();
    }

    //Generate a system user dummy for UID 0
    private static void addSysPackage() {
        // Add root
        PackageInfo root = new PackageInfo();
        root.packageName = "com.android.system";
        root.versionCode = BuildConfig.VERSION_CODE;
        root.versionName = BuildConfig.VERSION_NAME;
        root.applicationInfo = new ApplicationInfo();
        root.applicationInfo.name = "System";
        root.applicationInfo.uid = 0;
        root.applicationInfo.icon = 0;
        sCachePackage.put(root.applicationInfo.uid, root);
    }

    //Get a list with all currently installed packages
    private static List<PackageInfo> getPackages(Context context) {
        synchronized (context.getApplicationContext()) {
                PackageManager pm = context.getPackageManager();
            return new ArrayList<>(pm.getInstalledPackages(0));
        }
    }

    private static Drawable getDefaultIcon(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return getIconNew(android.R.drawable.sym_def_app_icon);
        } else {
            return getIconOld(android.R.drawable.sym_def_app_icon);
    }
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    private static Drawable getIconOld(int id) {
        return RunStore.getContext().getResources().getDrawable(id);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static Drawable getIconNew(int id) {
        return RunStore.getContext().getDrawable(id);
    }

    //Provides App names for activities
    public static String getLabel(int uid){
        if(!sCacheLabel.containsKey(uid)){
            if(sCachePackage.containsKey(uid)){
                sCacheLabel.put(uid, (String) sCachePackage.get(uid).applicationInfo.
                        loadLabel(RunStore.getContext().getPackageManager()));
            }
            else {
                //check: what is that?!
                //return RunStore.getContext().getString(R.string.unknown_app);
            }
        }
        return sCacheLabel.get(uid);
    }

    //Provides full app package name
    public static String getPackage(int uid) {
        if(sCachePackage.containsKey(uid)) {
            return sCachePackage.get(uid).packageName;
        } else{
            //check
            //return RunStore.getContext().getString(R.string.unknown_package);
            return null;
        }
    }

    //Provides resolved hostname if available
    public static String getDnsHostName(String hostAdd) {
        if (sCacheDNS.containsKey(hostAdd)){
            return sCacheDNS.get(hostAdd);
        } else { return hostAdd; }
    }

    //Test if hostname of connection is resolved
    public static Boolean hasHostName(String hostAdd) {
        return sCacheDNS.containsKey(hostAdd);
    }



    //provide a detail report of a connection
    public static void provideDetail(int uid, byte[] remoteAddHex) {
        ArrayList<Report> filterList = filterReportsByAdd(uid, remoteAddHex);
        sDetailReport = filterList.get(0);
        buildDetailStrings(filterList);
    }

    //filter a report list so that each remote ip is unique
    private static ArrayList<Report> filterReportsByAdd(int uid, byte[] remoteAddHex){
        List<Report> reportList = mUidReportMap.get(uid);
        ArrayList<Report> filterList = new ArrayList<>();
        for (int i = 0; i < reportList.size(); i++){
            if (Arrays.equals(reportList.get(i).remoteAddHex, remoteAddHex)){
                filterList.add(reportList.get(i));
            }
        }
        return filterList;
    }

    //Build report detail information for ReportDetailActivity list adapter
    private static void buildDetailStrings(ArrayList<Report> filterList) {
        ArrayList<String[]> l = new ArrayList<>();
        Report r = filterList.get(0);
        PackageInfo info = sCachePackage.get(r.uid);

        //App info
        l.add(new String[]{"User ID", "" + r.uid});
        l.add(new String[]{"App Version", "" + info.versionName});
        if(r.uid > 10000){
            l.add(new String[]{"Installed On", "" + new Date(info.firstInstallTime).toString()});
        } else {
            l.add(new String[]{"Installed On", "System App"});
        }
        l.add(new String[]{"", ""});

        //Connection info
        if(r.type == TLType.tcp6 || r.type == TLType.udp6) {
            l.add(new String[]{"Remote Address", r.remoteAdd.getHostAddress()
                    + "\n(IPv6 translated)"});
        } else {
            l.add(new String[]{"Remote Address", r.remoteAdd.getHostAddress()});
        }
        l.add(new String[]{"Remote HEX", ToolBox.printHexBinary(r.remoteAddHex)});
        if(hasHostName(r.remoteAdd.getHostAddress())){
            l.add(new String[]{"Remote Host", getDnsHostName(r.remoteAdd.getHostAddress())});
        }else {
            l.add(new String[]{"Remote Host", "name not resolved"});
        }
        if(r.type == TLType.tcp6 || r.type == TLType.udp6) {
            l.add(new String[]{"Local Address", r.localAdd.getHostAddress()
            + "\n(IPv6 translated)"});
        }else {
            l.add(new String[]{"Local Address", r.localAdd.getHostAddress()});
        }
        l.add(new String[]{"Local HEX", ToolBox.printHexBinary(r.localAddHex)});
        l.add(new String[]{"", ""});
        l.add(new String[]{"Service Port", "" + r.remotePort});
        l.add(new String[]{"Payload Protocol", "" + KnownPorts.resolvePort(r.remotePort)});
        l.add(new String[]{"Transport Protocol", "" + r.type});
        l.add(new String[]{"Last Seen", r.timestamp.toString()});
        l.add(new String[]{"", ""});

        //List open sockets
        l.add(new String[]{"Simultaneous Connections", "" + filterList.size()});
        for (int i = 0; i < filterList.size(); i++){
            Report r2 = filterList.get(i);
            l.add(new String[]{"(" + (i + 1) + ")src port > dst port",
                    r2.localPort + " > " + r2.remotePort});
            l.add(new String[]{"    last socket-state ", getTransportState(r.state)});
        }
        l.add(new String[]{"", ""});

        sDetailReportList = l;
    }


    //Resolves the socket state of an identified connection
    private static String getTransportState(byte[] state) {
        String status;
        String stateHex = ToolBox.printHexBinary(state);
        switch (stateHex) {
            case "01":
                status = "ESTABLISHED";
                break;
            case "02":
                status = "SYN_SENT";
                break;
            case "03":
                status = "SYN_RECV";
                break;
            case "04":
                status = "FIN_WAIT1";
                break;
            case "05":
                status = "FIN_WAIT2";
                break;
            case "06":
                status = "TIME_WAIT";
                break;
            case "07":
                status = "CLOSE";
                break;
            case "08":
                status = "CLOSE_WAIT";
                break;
            case "09":
                status = "LAST_ACK";
                break;
            case "0A":
                status = "LISTEN";
                break;
            case "0B":
                status = "CLOSING";
                break;
            case "0C":
                status = "NEW_SYN_RECV";
                break;
            default:
                status = "UNKNOWN";
                break;
        }
        return status;
    }
}
