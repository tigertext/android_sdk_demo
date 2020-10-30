package com.tigertext.ttandroid.sample.utils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.net.ConnectivityManagerCompat;
import androidx.fragment.app.Fragment;

import com.tigertext.ttandroid.Group;
import com.tigertext.ttandroid.Role;
import com.tigertext.ttandroid.User;
import com.tigertext.ttandroid.exceptions.TTNetworkException;
import com.tigertext.ttandroid.group.Participant;
import com.tigertext.ttandroid.org.Organization;
import com.tigertext.ttandroid.org.OrganizationSetting;
import com.tigertext.ttandroid.settings.SettingType;
import com.tigertext.ttandroid.web.NetworkUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

/**
 * Created by srinivas.rasamsetti on 4/21/15.
 * Port util methods from 5.1 as required
 */
public class TTUtils {

    private static final int DEFAULT_WAKE_LOCK_TIME = 5000;
    private static final String SCREEN_LOCK_TAG = "TigerConnect:screenLockTag";
    private static final float DARKER_PROPORTION = 0.8F;

    // ====================Check if device is connected to Internet ==================
    public static boolean isNetworkAvailable(Context context) {
        // Use same logic as SDK
        return NetworkUtils.isUserOnline(context);
    }

    /**
     * This method removes self user
     *
     * @param set
     * @return
     */
    public static Set<Participant> getUniqueMembersSet(@NonNull Set<Participant> set, Group group, @Nullable String excludeId) {
        Map<String, Participant> membersMap = new HashMap<>();
        Map<String, String> proxyToUserMap = group.getProxyToUserMap();
        Map<String, Set<String>> userToProxyMap = group.getUserToProxyMap();

        for (Participant participant : set) {
            membersMap.put(participant.getId(), participant);
        }

        Set<Participant> uniqueMembersSet = new HashSet<>();

        for (Participant participant : set) {
            if (participant instanceof Role) {
                Role role = (Role) participant;
                String userId = proxyToUserMap.get(role.getId());
                if (!TextUtils.isEmpty(userId)) {
                    Participant user = membersMap.get(userId);
                    if (user instanceof User) {
                        role.getOwners().clear();
                        role.getOwners().add((User) user);
                    }
                } else {
                    role.getOwners().clear();
                }
                uniqueMembersSet.add(role);
            } else if (participant instanceof User) {
                User user = (User) participant;
                Set<String> userProxyIds = userToProxyMap.get(user.getId());
                if ((userProxyIds == null || userProxyIds.contains(user.getId())) && !user.getId().equals(excludeId)) {
                    uniqueMembersSet.add(user);
                }
            }
        }
        return uniqueMembersSet;
    }

    /**
     * @param group      The group to count
     * @param excludedId Optional id to exclude from the count, can be used to include/exclude the current user
     * @return The approximate count of unique members, from what we can tell with the group data
     */
    public static int getUniqueMembersCount(@NonNull Group group, @Nullable String excludedId) {
        int numberOfUsersInGroup = group.getMemberCount();

        Map<String, Set<String>> userToProxyMap = group.getUserToProxyMap();
        for (Map.Entry<String, Set<String>> userProxyEntry : userToProxyMap.entrySet()) {
            String userId = userProxyEntry.getKey();
            if (userId.equals(excludedId) || !userProxyEntry.getValue().contains(userId)) {
                numberOfUsersInGroup--;
            }
        }
        return numberOfUsersInGroup;
    }

    /**
     * Deletes All Files in a Directory.
     * or the Given File
     *
     * @param file
     */
    static void deleteFile(final File file) {
        if (file == null)
            return;

        Timber.v("about to delete File Deleted : %s", file.getAbsolutePath());
        //Check to see if the File is a Directory,
        //If yes, call this function recursively for all the files in the directory
        if (file.isDirectory()) {
            for (File c : file.listFiles())
                deleteFile(c);
        }

        //Base Case for Recursive Call
        //The Last call will be the Empty Directory itself
        if (!file.delete()) {
            Timber.d("File Not Deleted : %s", file.getName());
        } else {
            Timber.v("File Deleted : %s", file.getAbsolutePath());
        }
    }

    /**
     * Returns Sim Country Iso from the Telephony Manager.
     *
     * @param context
     * @return the sim country iso or null if no TelephonyManager is found.
     */
    public static String getCountryIdFromSim(Context context) {
        try {
            TelephonyManager manager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (manager == null) {
                Timber.d("getCountryIdFromSim - No TelephonyManager found");
                return null;
            }

            String simCountryIso = manager.getSimCountryIso();
            if (TextUtils.isEmpty(simCountryIso)) {
                Timber.d("getCountryIdFromSim - sim country iso is null or empty");
                return null;
            }
            return simCountryIso.toUpperCase();
        } catch (Exception e) {
            Timber.d(e, "getCountryIdFromSim");
            return null;
        }
    }

    public static boolean isFragmentAlive(Fragment fragment) {
        return fragment != null && fragment.isAdded() && isActivityAlive(fragment.getActivity());
    }

    public static class PasswordChecker {

        private static final int MINIMUM_PASSWORD_LENGTH = 8;

        public boolean mHasCharLength;
        public boolean mHasCapital;
        public boolean mHasNumber;
        public boolean mHasSymbol;
        public boolean mIsValid;

        private PasswordChecker() {
        }

        public static PasswordChecker checkPassword(String password) {
            PasswordChecker passwordChecker = new PasswordChecker();

            if (TextUtils.isEmpty(password)) {
                return passwordChecker;
            }

            int inputLength = password.length();
            passwordChecker.mHasCharLength = inputLength >= MINIMUM_PASSWORD_LENGTH;

            for (int i = 0; i < inputLength; i++) {
                char c = password.charAt(i);

                passwordChecker.mHasCapital = passwordChecker.mHasCapital || (c >= 'A' && c <= 'Z');          // has a capital letter
                passwordChecker.mHasNumber = passwordChecker.mHasNumber || (c >= '0' && c <= '9');            // has a number

                if (passwordChecker.mHasCapital && passwordChecker.mHasNumber) {
                    break;
                }
            }


            Pattern p = Pattern.compile("[^a-z0-9 ]", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(password);
            passwordChecker.mHasSymbol = m.find();

            passwordChecker.mIsValid = passwordChecker.mHasCharLength && passwordChecker.mHasCapital && passwordChecker.mHasSymbol && passwordChecker.mHasNumber;

            return passwordChecker;
        }

    }


    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public static boolean isActivityContextDestroyed(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && context instanceof Activity && ((Activity) context).isDestroyed();
    }

    public static String readFile(String filePath) {
        File file = new File(filePath);
        String text;
        StringBuilder stringBuilder = new StringBuilder();
        if (file.exists()) {
            BufferedReader bufferedReader = null;
            try {
                bufferedReader = new BufferedReader(new FileReader(file));
                while ((text = bufferedReader.readLine()) != null) {
                    stringBuilder.append(text);
                }
            } catch (IOException e) {
                Timber.d(e, "readFile");
            } finally {
                if (bufferedReader != null) {
                    try {
                        bufferedReader.close();
                    } catch (IOException ioe) {
                        Timber.d(ioe, "readFile");
                    }
                }
            }
        }
        return stringBuilder.toString();
    }

    public static void showKeyboard(Context context, View view) {
        InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
    }

    public static void hideKeyboard(Context context, IBinder windowToken) {
        InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(windowToken, InputMethodManager.HIDE_NOT_ALWAYS);
    }

    public static boolean isKitkatOrHigher() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }

    public static boolean isMarshmallowOrHigher() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    public static void removeAllCookies() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().removeAllCookies(null);
        } else {
            CookieManager.getInstance().removeAllCookie();
        }
    }

    /**
     * This method uses HSV in order to
     * make a color a darker without losing its
     * original base color...
     *
     * @param color
     * @return darkerColor
     */
    public static int darkenColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= DARKER_PROPORTION; // value component
        return Color.HSVToColor(hsv);
    }

    public static boolean isActivityAlive(Activity activity) {
        return activity != null && !activity.isFinishing() && (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !activity.isDestroyed());
    }

    public static boolean isNoResponseError(Throwable throwable) {
        return throwable instanceof TTNetworkException && ((TTNetworkException) throwable).getStatus() == TTNetworkException.NO_RESPONSE;
    }

    public static int getNetworkErrorResponse(Throwable throwable) {
        if (throwable instanceof TTNetworkException) {
            return ((TTNetworkException) throwable).getStatus();
        }
        return TTNetworkException.UNKNOWN_ERROR;
    }

    public static boolean getBooleanOrgSetting(@NonNull Organization organization, @NonNull SettingType settingType) {
        return getBooleanOrgSetting(organization, settingType, false);
    }

    public static boolean getBooleanOrgSetting(@NonNull Organization organization, @NonNull SettingType settingType, boolean defaultValue) {
        OrganizationSetting setting = organization.getSettings().get(settingType);
        if (setting != null) {
            return (Boolean) setting.getValue();
        } else {
            return defaultValue;
        }
    }

    public static boolean shouldLimitLargeDataUsage(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean isMeteredConnection = ConnectivityManagerCompat.isActiveNetworkMetered(connectivityManager);
        // Restrict to N and up because compat version for older devices defaults to enabled for some reason...
        boolean isAtLeastN = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
        boolean isDataSaverOn = isAtLeastN && ConnectivityManagerCompat.getRestrictBackgroundStatus(connectivityManager) != ConnectivityManagerCompat.RESTRICT_BACKGROUND_STATUS_DISABLED;
        Timber.v("isMeteredConnection: %b, isDataSaverOn: %b", isMeteredConnection, isDataSaverOn);
        return isMeteredConnection || isDataSaverOn;
    }

    public static void runOrPostToHandler(Handler handler, Runnable runnable) {
        if (Looper.myLooper() == handler.getLooper()) {
            runnable.run();
        } else {
            handler.post(runnable);
        }
    }

    /**
     * Checks to see if the device is currently in a locked state, requiring some user interaction before getting into the device.
     *
     * @return True if the device requires a pin or user action before allowing full access to the system.
     */
    public static boolean isDeviceLocked(@NonNull Context context) {
        KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        return keyguardManager != null && keyguardManager.inKeyguardRestrictedInputMode();
    }

    public static boolean toBoolean(@Nullable Boolean aBoolean) {
        return toBoolean(aBoolean, false);
    }

    public static boolean toBoolean(@Nullable Boolean aBoolean, boolean defaultIfNull) {
        return aBoolean != null ? aBoolean : defaultIfNull;
    }

}