/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.nfc.cardemulation;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.util.Log;

import com.android.nfc.R;
import com.android.nfc.cardemulation.util.TelephonyUtils;

import java.util.List;
import java.util.stream.Collectors;

public class PreferredSubscriptionService implements TelephonyUtils.Callback {
    static final String TAG = "PreferredSubscriptionService";
    static final String PREF_SUBSCRIPTION = "SubscriptionPref";
    static final String PREF_PREFERRED_SUB_ID = "pref_sub_id";
    private SharedPreferences mSubscriptionPrefs = null;;

    Context mContext;
    Callback mCallback;

    int mDefaultSubscriptionId = TelephonyUtils.SUBSCRIPTION_ID_UNKNOWN;
    private final ContentResolver mContentResolver;
    boolean mIsEuiccCapable;
    boolean mIsUiccCapable;
    TelephonyUtils mTelephonyUtils;
    int mActiveSubscriptoinState = TelephonyUtils.SUBSCRIPTION_STATE_UNKNOWN;
    List<SubscriptionInfo> mActiveSubscriptions = null;

    public interface Callback {
        void onPreferredSubscriptionChanged(int subscriptionId, boolean isActive);
    }

    public PreferredSubscriptionService(Context context, Callback callback) {
        mContext = context;
        mContentResolver = mContext.getContentResolver();
        mCallback = callback;

        mIsUiccCapable = context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_NFC_OFF_HOST_CARD_EMULATION_UICC);
        mIsEuiccCapable = mContext.getResources().getBoolean(R.bool.enable_euicc_support);

        mTelephonyUtils = TelephonyUtils.getInstance(context);
        mSubscriptionPrefs =  mContext.getSharedPreferences(
                PREF_SUBSCRIPTION, Context.MODE_PRIVATE);

        // Initialize default subscription to UICC if there is no preference
        if (mIsUiccCapable || mIsEuiccCapable) {
            mDefaultSubscriptionId = getPreferredSubscriptionId();
            if (mDefaultSubscriptionId == TelephonyUtils.SUBSCRIPTION_ID_UNKNOWN) {
                Log.d(TAG, "Set preferred subscription to UICC, only update");
                setPreferredSubscriptionId(TelephonyUtils.SUBSCRIPTION_ID_UICC, false);
            }
        }
    }

    public void initialize() {
        if (mIsUiccCapable || mIsEuiccCapable) {
            onDefaultSubscriptionChanged();
            mTelephonyUtils.registerSubscriptionChangedCallback(this);
        }
    }

    public int getPreferredSubscriptionId() {
        Log.d(TAG, "getPreferredSubscriptionId: " + mDefaultSubscriptionId);
        return mSubscriptionPrefs.getInt(
                PREF_PREFERRED_SUB_ID, TelephonyUtils.SUBSCRIPTION_ID_UNKNOWN);
        }

    public void setPreferredSubscriptionId(int subscriptionId, boolean force) {
        Log.d(TAG, "setPreferredSubscriptionId: " + subscriptionId);
        if (mDefaultSubscriptionId != subscriptionId) {
            mDefaultSubscriptionId = subscriptionId;
            mSubscriptionPrefs.edit().putInt(PREF_PREFERRED_SUB_ID, subscriptionId).commit();
            if (force) {
                onDefaultSubscriptionChanged();
    }
        }
    }

    public void onDefaultSubscriptionChanged() {
        Log.d(TAG, "onDefaultSubscriptionChanged: ");
        boolean isSubscriptionActivated = isSubscriptionActivated(mDefaultSubscriptionId);
        mActiveSubscriptoinState = isSubscriptionActivated ?
                TelephonyUtils.SUBSCRIPTION_STATE_ACTIVATE
                : TelephonyUtils.SUBSCRIPTION_STATE_INACTIVATE;
        mCallback.onPreferredSubscriptionChanged(mDefaultSubscriptionId, isSubscriptionActivated);
    }

    @Override
    public void onActiveSubscriptionsUpdated(List<SubscriptionInfo> activeSubscriptionList) {
        boolean isActivationStateChanged = checkSubscriptionStateChanged(activeSubscriptionList);
        if (isActivationStateChanged) {
            mCallback.onPreferredSubscriptionChanged(mDefaultSubscriptionId,
                    mActiveSubscriptoinState == TelephonyUtils.SUBSCRIPTION_STATE_ACTIVATE);
        }
        else {
            Log.i(TAG, "Active Subscription is not changed");
        }
    }

    private boolean isSubscriptionActivated(int subscriptionId) {
        if (mActiveSubscriptions == null) {
            Log.d(TAG, "get active subscriptions is list because it's null");
            mActiveSubscriptions = mTelephonyUtils.getActiveSubscriptions().stream().filter(
                    TelephonyUtils.SUBSCRIPTION_ACTIVE_CONDITION_FOR_UICC.or(
                            TelephonyUtils.SUBSCRIPTION_ACTIVE_CONDITION_FOR_EUICC))
                    .collect(Collectors.toList());
        }
        boolean isEuiccSubscription = mTelephonyUtils.isEuiccSubscription(subscriptionId);
        return mActiveSubscriptions.stream().anyMatch(subscriptionInfo ->
                subscriptionInfo.isEmbedded() == isEuiccSubscription);
    }

    private boolean checkSubscriptionStateChanged(List<SubscriptionInfo> activeSubscriptionList) {
        // filtered subscriptions
        mActiveSubscriptions = activeSubscriptionList.stream().filter(
                TelephonyUtils.SUBSCRIPTION_ACTIVE_CONDITION_FOR_UICC.or(
                        TelephonyUtils.SUBSCRIPTION_ACTIVE_CONDITION_FOR_EUICC))
                .collect(Collectors.toList());
        int previousActiveSubscriptionState = mActiveSubscriptoinState;
        int currentActiveSubscriptionState = isSubscriptionActivated(mDefaultSubscriptionId) ?
                TelephonyUtils.SUBSCRIPTION_STATE_ACTIVATE :
                TelephonyUtils.SUBSCRIPTION_STATE_INACTIVATE;
        if (previousActiveSubscriptionState != currentActiveSubscriptionState) {
            Log.d(TAG, "onSubscriptionsChanged - state changed: " +
                    previousActiveSubscriptionState + " to " + currentActiveSubscriptionState);
            mActiveSubscriptoinState = currentActiveSubscriptionState;
            return true;
        }

        return false;
    }
}
