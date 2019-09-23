package com.tigertext.ttandroid.sample.login;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.tigertext.ttandroid.User;
import com.tigertext.ttandroid.account.ValidationResponse;
import com.tigertext.ttandroid.account.listener.LoginListener;
import com.tigertext.ttandroid.account.listener.LogoutListener;
import com.tigertext.ttandroid.api.TT;
import com.tigertext.ttandroid.org.Organization;
import com.tigertext.ttandroid.sample.R;
import com.tigertext.ttandroid.sample.application.TigerConnectApplication;
import com.tigertext.ttandroid.sample.inbox.InboxFragment;
import com.tigertext.ttandroid.sample.utils.SharedPrefs;

import java.lang.ref.WeakReference;

import timber.log.Timber;

public class LoginFragment extends Fragment {
    private EditText emailEditText;
    private EditText passwordEditText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.login_fragment, container, false);
        emailEditText = rootView.findViewById(R.id.email_edit_text);
        passwordEditText = rootView.findViewById(R.id.password_edit_text);
        Button loginButton = rootView.findViewById(R.id.login_button);
        loginButton.setOnClickListener(v ->
                onLoginClicked(emailEditText.getText().toString(),
                    passwordEditText.getText().toString()));
        return rootView;
    }

    private void goToInboxFragment() {
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.setCustomAnimations(R.anim.in, R.anim.out, R.anim.pop_in, R.anim.pop_out);
        ft.replace(R.id.fragment_container, new InboxFragment(), "Inbox Fragment").addToBackStack(null).commit();
    }

    private void onLoginClicked(String email, String password) {
        Timber.d("Login Clicked");
        if (TT.getInstance().getAccountManager().isLoggedIn()) {
            logout(email, password, this);
            return;
        }

        // This is how you log in with a user in our SDK
        TT.getInstance().getAccountManager().login(email, password, new LoginResultCallback(this));
    }

    private void logout(String email, String password, LoginFragment loginFragment) {
        TT.getInstance().getAccountManager().logout(new LogoutListener() {
            @Override
            public void onLoggedOut() {
                //Handle logout
                Timber.d("onLoggedOut");
                TT.getInstance().getAccountManager().login(email, password, new LoginResultCallback(loginFragment));
            }

            @Override
            public void onLogoutError(Throwable throwable) {
                //Handle logout error
                Timber.e(throwable, "Logout Error");
                Toast.makeText(getContext(), "Logout Failed", Toast.LENGTH_LONG).show();
            }
        });

        TigerConnectApplication.getApp().terminateRealTimeEventsService();
    }

    private class LoginResultCallback implements LoginListener {
        private WeakReference<LoginFragment> weakLoginFragment;

        LoginResultCallback(LoginFragment fragment) {
            weakLoginFragment = new WeakReference<>(fragment);
        }

        @Override
        public void onLoggedIn(User user, ValidationResponse validationResponse) {
            Timber.d("onLoggedIn");
            Fragment loginFragment = weakLoginFragment.get();
            if (loginFragment == null) return;
            //Handle logged in user
            Toast.makeText(weakLoginFragment.get().getContext(), "Log In Successful!", Toast.LENGTH_LONG).show();

            /**
             * After logging in, make sure to sync with our SDK
             * Note that this may take a while, so ideally it would be nice to put some sort of
             * loading screen here as the SDK syncs
             */
            syncWithSDK(weakLoginFragment.get());
        }

        @Override
        public void onLoginError(Throwable throwable) {
            Timber.e(throwable, "Login Error");
            Fragment loginFragment = weakLoginFragment.get();
            if (loginFragment == null) return;
            //Handle error
            Toast.makeText(weakLoginFragment.get().getContext(), "Log In Failed", Toast.LENGTH_LONG).show();
        }

        private void syncWithSDK(final LoginFragment fragment) {
            TT.getInstance().sync(new TT.SyncListener() {
                @Override
                public void onSyncComplete() {
                    Timber.d("onSyncComplete");
                    // sync successful, continue using the sdk, at this point you can get all the conversations for this user (example available in the next sections)
                    fragment.goToInboxFragment();
                }

                @Override
                public void onSyncFailed(Throwable throwable) {
                    Timber.e(throwable, "onSyncFailed");
                    //otherwise you will want to prompt the user to try and re-sync again
                }
            });
        }
    }
}
