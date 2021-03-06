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

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import org.androidannotations.annotations.AfterInject;
import org.androidannotations.annotations.EService;
import org.androidannotations.annotations.SystemService;
import org.fitchfamily.android.wifi_backend.BuildConfig;
import org.fitchfamily.android.wifi_backend.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@EService
public class gpsMonitor extends Service implements LocationListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private final static String TAG = "WiFiBackendGpsMon";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @SystemService
    protected LocationManager locationManager;

    @SystemService
    protected WifiManager wifi;

    private long sampleTime;
    private float sampleDistance;

    @Override
    public IBinder onBind(Intent intent) {
        return new Binder();
    }

    @AfterInject
    protected void init() {
        if (DEBUG) {
            Log.i(TAG, "service started");
        }

        sampleTime = Configuration.with(this).minimumGpsTimeInMilliseconds();
        sampleDistance = Configuration.with(this).minimumGpsDistanceInMeters();

        try {
            locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER,
                    sampleTime,
                    sampleDistance,
                    gpsMonitor.this);
        } catch (SecurityException ex) {
            if(DEBUG) {
                Log.w(TAG, "init()", ex);
            }
        }

        Configuration.with(this).register(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Configuration.with(this).unregister(this);

        try {
            locationManager.removeUpdates(gpsMonitor.this);
        } catch (SecurityException ex) {
            // ignore
        }

        if (DEBUG) {
            Log.i(TAG, "service destroyed");
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(TextUtils.equals(key, Configuration.PREF_MIN_GPS_TIME) ||
                TextUtils.equals(key, Configuration.PREF_MIN_GPS_DISTANCE)) {

            updateSamplingConf(
                    Configuration.with(gpsMonitor.this).minimumGpsTimeInMilliseconds(),
                    Configuration.with(gpsMonitor.this).minimumGpsDistanceInMeters()
            );
        }
    }

    private void updateSamplingConf(final long sampleTime, final float sampleDistance) {
        if (DEBUG) {
            Log.i(TAG, "updateSamplingConf(" + sampleTime + ", " + sampleDistance + ")");
        }

        // We are in a call back so we can't change the sampling configuration
        // in the caller's thread context. Send a message to the processing thread
        // for it to deal with the issue.
        executor.submit(new Runnable() {
            @Override
            public void run() {
                if ((gpsMonitor.this.sampleTime != sampleTime) ||
                        (gpsMonitor.this.sampleDistance != sampleDistance)) {

                    gpsMonitor.this.sampleTime = sampleTime;
                    gpsMonitor.this.sampleDistance = sampleDistance;

                    if (DEBUG) {
                        Log.i(TAG, "Changing GPS sampling configuration: " +
                                gpsMonitor.this.sampleTime + " ms, " + gpsMonitor.this.sampleDistance + " meters");
                    }

                    try {
                        locationManager.removeUpdates(gpsMonitor.this);
                        locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER,
                                gpsMonitor.this.sampleTime,
                                gpsMonitor.this.sampleDistance,
                                gpsMonitor.this);
                    } catch (SecurityException ex) {
                        if(DEBUG) {
                            Log.w(TAG, "updateSamplingConf()", ex);
                        }
                    }
                }
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_STICKY;
    }

    @Override
    public void onLocationChanged(final android.location.Location location) {

        if (location.getProvider().equals("gps")) {
            if (location.getAccuracy() <= Configuration.with(this).minimumGpsAccuracyInMeters()) {
                BackendService.instanceGpsLocationUpdated(location);
             } else {
                if (DEBUG) {
                    Log.i(TAG, "Ignoring inaccurate GPS location ("+location.getAccuracy()+" meters).");
                }
            }
        } else {
            if (DEBUG) {
                Log.i(TAG, "Ignoring position from \""+location.getProvider()+"\"");
            }
        }
    }

    @Override
    public void onProviderDisabled(String arg0) {
        if (DEBUG) {
            Log.i(TAG, "Provider Disabled.");
        }
    }

    @Override
    public void onProviderEnabled(String arg0) {
        if (DEBUG) {
            Log.i(TAG, "Provider Enabled.");
        }
    }

    @Override
    public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
        if (DEBUG) {
            Log.i(TAG, "Status Changed.");
        }
    }
}
