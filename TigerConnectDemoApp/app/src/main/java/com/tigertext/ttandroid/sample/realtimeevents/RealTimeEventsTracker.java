package com.tigertext.ttandroid.sample.realtimeevents;

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.OnLifecycleEvent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.tigertext.ttandroid.api.TT;
import com.tigertext.ttandroid.sample.application.TigerConnectApplication;
import com.tigertext.ttandroid.sse.service.TTService;

import timber.log.Timber;

public class RealTimeEventsTracker implements LifecycleObserver {

    private boolean isForegrounded = false;
    private boolean isServiceBound = false;

    public RealTimeEventsTracker() {
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onForeground() {
        Timber.v("onForeground");
        isForegrounded = true;
        TT.getInstance().getAccountManager().setPresenceStatus(true);

        if (!TT.getInstance().getAccountManager().isLoggedIn()) return;

        // If the sse manager is already connected, set the online presence to available.
        if (TTService.isSSEConnected()) {
            Timber.v("onActivityStarted RealTimeEvents is already connected set presence to online");
            TT.getInstance().getAccountManager().setOnlinePresence(true);
        }

        startRealTimeEventsService();
        bindService();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    public void onBackground() {
        // Changing the online presence to be away since the app is now backgrounded.
        Timber.v("onBackground");
        isForegrounded = false;
        TT.getInstance().getAccountManager().setPresenceStatus(false);

        if (!TT.getInstance().getAccountManager().isLoggedIn()) return;

        unbindService();

        // If the sse manager is already connected, set the online presence to away.
        if (TTService.isSSEConnected()) {
            Timber.v("onActivityStopped about to set online presence to false");
            TT.getInstance().getAccountManager().setOnlinePresence(false);
        }

        stopRealTimeEventsService();
    }

    private void startRealTimeEventsService() {
        try {
            TT.getInstance().startService();
        } catch (IllegalStateException e) {
            // App *should* be in the foreground, but is crashing on occasion for some reason.
            // Suppress crash and keep track.
            // Will retry when the user starts another activity or an FCM is received.
            String errorMessage = "startRealTimeEventsService: Error starting service";
            Timber.e(e, errorMessage);
        }
    }

    private void stopRealTimeEventsService() {
        TT.getInstance().stopService();
    }

    private void bindService() {
        if (isServiceBound) {
            return;
        }
        boolean isSuccess;
        try {
            Context context = TigerConnectApplication.getApp();
            Intent intent = new Intent(context, TTService.class);
            isSuccess = context.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
            Timber.d("Binding to TTService returned %b", isSuccess);
        } catch (SecurityException e) {
            Timber.e(e, "Can't bind to TTService");
        }

    }

    private void unbindService() {
        if (isServiceBound) {
            TigerConnectApplication.getApp().unbindService(mServiceConnection);
            isServiceBound = false;
            Timber.d("Unbound service");
        }
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Timber.d("onServiceConnected");
            isServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Timber.d("onServiceDisconnected");
            isServiceBound = false;
        }
    };

    public void onLoginSyncComplete() {
        startRealTimeEventsService();
        if (isForegrounded) {
            bindService();
        }
    }

    public void onLogout() {
        unbindService();
        stopRealTimeEventsService();
    }

    public boolean isAppOpen() {
        return isForegrounded;
    }
}
