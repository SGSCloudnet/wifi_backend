package org.fitchfamily.android.wifi_backend.backend;

/*
 *  WiFi Backend for Unified Network Location
 *  Copyright (C) 2014,2015  Tod Fitch
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;

import org.fitchfamily.android.wifi_backend.BuildConfig;
import org.fitchfamily.android.wifi_backend.Configuration;

public class WifiReceiver extends BroadcastReceiver {
    private static final String TAG = "WiFiReceiver";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private boolean scanStarted = false;
    private WifiManager wifi;
    private WifiReceivedCallback callback;

    public WifiReceiver(Context ctx, WifiReceivedCallback aCallback) {
        if (DEBUG) {
            Log.i(TAG, "WifiReceiver() constructor");
        }

        wifi = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
        callback = aCallback;
    }

    public void onReceive(Context c, Intent intent) {
        if (!isScanStarted())
            return;
        setScanStarted(false);
        List<ScanResult> configs = wifi.getScanResults();

        if (DEBUG) {
            Log.i(TAG, "Got " + configs.size() + " wifi access points");
        }

        if (configs.size() > 0) {

            List<Bundle> foundBssids = new ArrayList<Bundle>(configs.size());

            for (ScanResult config : configs) {
                // some strange devices use a dot instead of :
                final String canonicalBSSID = config.BSSID.toUpperCase(Locale.US).replace(".",":");
                // ignore APs that have _nomap suffix on SSID
                if (config.SSID.endsWith("_nomap")) {
                    if (DEBUG) {
                        Log.i(TAG, "Ignoring AP '" + config.SSID + "' BSSID: " + canonicalBSSID);
                    }
                } else {
                    Bundle extras = new Bundle();
                    extras.putString(Configuration.EXTRA_MAC_ADDRESS, canonicalBSSID);
                    extras.putInt(Configuration.EXTRA_SIGNAL_LEVEL, config.level);
                    foundBssids.add(extras);
                }
            }

            callback.process(foundBssids);
        }

    }

    public boolean isScanStarted() {
        return scanStarted;
    }

    public void setScanStarted(boolean scanStarted) {
        this.scanStarted = scanStarted;
    }

    public interface WifiReceivedCallback {

        void process(List<Bundle> foundBssids);

    }

    public void startScan() {
        setScanStarted(true);
        boolean scanAlwaysAvailable = false;
        try {
            scanAlwaysAvailable = wifi.isScanAlwaysAvailable();
        } catch (NoSuchMethodError e) {
            scanAlwaysAvailable = false;
        }
        if (!wifi.isWifiEnabled() && !scanAlwaysAvailable) {
            Log.i(TAG, "Wifi is disabled and we can't scan either. Not doing anything.");
        }
        wifi.startScan();
    }
}
