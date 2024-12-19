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
package com.android.nfc.cardemulation.util;

import android.content.Context;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.UiccCardInfo;
import android.util.Log;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public class TelephonyUtils extends SubscriptionManager.OnSubscriptionsChangedListener{
    private final String TAG = "TelephonyUtils";
    public static final int SUBSCRIPTION_STATE_UNKNOWN = -1;
    public static final int SUBSCRIPTION_STATE_ACTIVATE = 1;
    public static final int SUBSCRIPTION_STATE_INACTIVATE = 2;

    public static final int SUBSCRIPTION_ID_UNKNOWN = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    public static final int SUBSCRIPTION_ID_UICC = 0x100000;
    public static final int PORT_IS_NOT_SET = 0xFF;

    public static final int SIM_TYPE_UNKNOWN = 0;
    public static final int SIM_TYPE_UICC = 1;
    public static final int SIM_TYPE_EUICC_1 = 2;
    public static final int SIM_TYPE_EUICC_2 = 3;

    public static final int MEP_MODE_NONE = 0;
    public static final int MEP_MODE_A1 = 1;
    public static final int MEP_MODE_A2 = 2;
    public static final int MEP_MODE_B = 3;

    private TelephonyManager mTelephonyManager;
    private SubscriptionManager mSubscriptionManager;

    private boolean mIsSubscriptionsChangedListenerRegistered = false;

    public static Predicate<SubscriptionInfo> SUBSCRIPTION_ACTIVE_CONDITION_FOR_UICC =
            subscriptionInfo -> !subscriptionInfo.isEmbedded()
                    && subscriptionInfo.areUiccApplicationsEnabled();
    public static Predicate<SubscriptionInfo> SUBSCRIPTION_ACTIVE_CONDITION_FOR_EUICC =
            subscriptionInfo -> subscriptionInfo.isEmbedded();

    int mMepMode ;   // TODO - How to set MEP mode. Port value is depending on MEP Mode
    public interface Callback {
        void onActiveSubscriptionsUpdated(List<SubscriptionInfo> activeSubscriptionList);
    }
    Callback mCallback;
    private static class Singleton {
        private static TelephonyUtils sInstance = null;
    }

    public static TelephonyUtils getInstance(Context context) {
        if (TelephonyUtils.Singleton.sInstance == null) {
            TelephonyUtils.Singleton.sInstance = new TelephonyUtils(context);
        }
        return TelephonyUtils.Singleton.sInstance;
    }

    public TelephonyUtils(Context context) {
        mTelephonyManager = context.getSystemService(TelephonyManager.class);
        mSubscriptionManager = SubscriptionManager.from(context);
        mMepMode = MEP_MODE_B;  //TODO - How to check MEP mode from eSIM
    }

    public void registerSubscriptionChangedCallback(Callback callback) {
        mCallback = callback;
        mSubscriptionManager.addOnSubscriptionsChangedListener(
                Executors.newSingleThreadExecutor(), this);
    }

    public Optional<SubscriptionInfo> getActiveSubscriptionInfoById(int subscriptionId) {
        Log.d(TAG, "getActiveSubscriptionInfoById: " + subscriptionId);
        if (isUiccSubscription(subscriptionId)) {
            Log.d(TAG, "Uicc Subscription");
            return findFirstActiveSubscriptionInfo(subscriptionInfo ->
                    !subscriptionInfo.isEmbedded()
                            && subscriptionInfo.areUiccApplicationsEnabled());
        }
        else {
            Log.d(TAG, "Embedded Uicc Subscription");
            return Optional.ofNullable(
                    mSubscriptionManager.getActiveSubscriptionInfo(subscriptionId));
        }
    }
    public boolean isUiccSubscription(int subscriptionId) {
        return subscriptionId == SUBSCRIPTION_ID_UICC;
    }

    public boolean isEuiccSubscription(int subscriptionId) {
        return (subscriptionId != SUBSCRIPTION_ID_UICC) &&
                (subscriptionId != SUBSCRIPTION_ID_UNKNOWN);
    }

    public List<SubscriptionInfo> getActiveSubscriptions() {
        List<SubscriptionInfo> list = mSubscriptionManager.getActiveSubscriptionInfoList();
        return (list != null) ? list : Collections.emptyList();
    }

    @Override
    public void onSubscriptionsChanged() {
        Log.d(TAG, "onSubscriptionsChanged");
        if (!mIsSubscriptionsChangedListenerRegistered) {
            Log.d(TAG, "Skip when receive the event with registering");
            mIsSubscriptionsChangedListenerRegistered = true;
            return;
        }

        mCallback.onActiveSubscriptionsUpdated(
                mSubscriptionManager.getActiveSubscriptionInfoList());
    }

    public String updateSwpStatusForEuicc(int simType) {
        return transmitApduToActiveSubscription(0x80, 0x7C, 0x02,
                getPort(simType), 0x00, "");
    }

    private String transmitApduToActiveSubscription(
            int cla, int ins, int p1, int p2, int p3, String data) {
        AtomicReference<String > response = new AtomicReference<>();
        findFirstActiveSubscriptionInfo(SUBSCRIPTION_ACTIVE_CONDITION_FOR_EUICC)
                .ifPresentOrElse(
                        subscriptionInfo -> {
                            String result = mTelephonyManager
                                    .createForSubscriptionId(subscriptionInfo.getSubscriptionId())
                                    .iccTransmitApduBasicChannel(cla, ins, p1, p2, p3, data);
                            response.set(result);
                        } ,
                        ()-> {
                            Log.d(TAG, "Send APDU fail because active subscription is not exist");
                            response.set("FFFF");
                        }
                );
        return response.get();
    }

    private Optional<SubscriptionInfo> findFirstActiveSubscriptionInfo(
            Predicate<SubscriptionInfo> condition) {
        return getActiveSubscriptions().stream().filter(condition)
                .findFirst();
    }

    // TODO - port value updated according to MEP Mode
    // Currently, Nfc could not know MEP mode.
    private int getPort(int simType) {
        int port = PORT_IS_NOT_SET;
        if (mMepMode != MEP_MODE_NONE) {
            if (simType == SIM_TYPE_EUICC_1) {
                port = (mMepMode == MEP_MODE_B) ? 0x00 : 0x01;
            } else if (simType == SIM_TYPE_EUICC_2) {
                port = (mMepMode == MEP_MODE_B) ? 0x01 : 0x02;
            }
        }
        return port;
    }
}
