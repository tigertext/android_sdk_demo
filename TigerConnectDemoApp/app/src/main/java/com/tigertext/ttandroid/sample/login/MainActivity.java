package com.tigertext.ttandroid.sample.login;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.tigertext.ttandroid.api.TT;
import com.tigertext.ttandroid.sample.R;
import com.tigertext.ttandroid.sample.application.TigerConnectApplication;
import com.tigertext.ttandroid.sample.inbox.InboxFragment;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupInitialView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Initiate our Real Time Events Service on Resume of this Main Activity
        TigerConnectApplication.getApp().initRealTimeEventsService();
    }

    private void setupInitialView() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.isStateSaved()) return;
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        Fragment fragment;
        String tag;

        if (TT.getInstance().getAccountManager().isLoggedIn()) {
            fragment = new InboxFragment();
            tag = "Inbox Fragment";
        } else {
            fragment = new LoginFragment();
            tag = "Login Fragment";
        }

        if (!isFinishing()) {
            fragmentTransaction.addToBackStack(null);
            fragmentTransaction.replace(R.id.fragment_container, fragment, tag).addToBackStack(null).commit();
        }
    }
}
