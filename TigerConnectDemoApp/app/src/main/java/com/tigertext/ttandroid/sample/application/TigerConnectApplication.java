package com.tigertext.ttandroid.sample.application;

import android.arch.lifecycle.ProcessLifecycleOwner;
import android.content.IntentFilter;
import android.support.multidex.MultiDexApplication;
import android.support.v4.content.LocalBroadcastManager;

import com.tigertext.ttandroid.api.TT;
import com.tigertext.ttandroid.gcm.TTGcm;
import com.tigertext.ttandroid.org.Organization;
import com.tigertext.ttandroid.sample.fcm.FcmBroadcastReceiver;
import com.tigertext.ttandroid.sample.fcm.SSEBroadcastReceiver;
import com.tigertext.ttandroid.sample.notification.TCNotificationManager;
import com.tigertext.ttandroid.sample.realtimeevents.RealTimeEventsTracker;
import com.tigertext.ttandroid.sample.utils.SharedPrefs;
import com.tigertext.ttandroid.sse.PushNotificationHandler;

import timber.log.Timber;

public class TigerConnectApplication extends MultiDexApplication {

    private static TigerConnectApplication app;
    private RealTimeEventsTracker realTimeEventsTracker;

    @Override
    public void onCreate() {
        super.onCreate();
        app = this;

        // Plant Timber Logging Tree to show logs in Logcat
        Timber.plant(new Timber.DebugTree());

        // Initialize our TT SDK to be able to use all of our Managers
        TT.init(getApplicationContext(), "com.tigertext.ttandroid.sample");

        // Initialize our Organization ID to be the default Contacts Organization
        SharedPrefs.getInstance().putString(SharedPrefs.ORGANIZATION_ID, Organization.CONSUMER_ORG_ID);

        setupRealTimeEventsTracker();

        setupNotificationReceiver();
    }

    /**
     * Set up tracking for real time events, to know when the user is active on the app
     */
    private void setupRealTimeEventsTracker() {
        realTimeEventsTracker = new RealTimeEventsTracker();
        ProcessLifecycleOwner.get().getLifecycle().addObserver(realTimeEventsTracker);
    }

    /**
     *  Register receivers for FCM and SSE Broadcast events, as well as NotificationManager
     *  to publish notifications to the user
     */
    private void setupNotificationReceiver() {
        // Register for FCM Broadcast Events
        IntentFilter intentFilter = new IntentFilter(TTGcm.ACTION_DISPLAY_ALERT);
        LocalBroadcastManager.getInstance(this.getApplicationContext()).registerReceiver(new FcmBroadcastReceiver(), intentFilter);

        // Register for SSE Broadcast Events
        intentFilter = new IntentFilter(PushNotificationHandler.ACTION_DISPLAY_ALERT);
        LocalBroadcastManager.getInstance(this.getApplicationContext()).registerReceiver(new SSEBroadcastReceiver(), intentFilter);

        // Initiate our Notification Manager for notification banners like new messages, etc.
        TCNotificationManager.INSTANCE.init(this);
    }

    public static TigerConnectApplication getApp() {
        return app;
    }

    public void initRealTimeEventsService() {
        realTimeEventsTracker.onLoginSyncComplete();
    }

    public void terminateRealTimeEventsService() {
        realTimeEventsTracker.onLogout();
    }

    public boolean isAppOpen() {
        return realTimeEventsTracker.isAppOpen();
    }
}
