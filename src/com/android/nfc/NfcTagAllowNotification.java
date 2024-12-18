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

package com.android.nfc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.nfc.NfcAdapter;

import java.util.List;

/**
 * This class handles the Notification Manager for the tag app allowlist info
 */

public class NfcTagAllowNotification {
    private static final String NFC_NOTIFICATION_CHANNEL = "nfc_tag_notification_channel";
    private NotificationChannel mNotificationChannel;
    public static final int NOTIFICATION_ID_NFC = -1000003;
    Context mContext;
    List<String> mAppNames;

    /**
     * Constructor
     *
     * @param ctx The context to use to obtain access to the resources
     * @param appName The tag application name
     */
    public NfcTagAllowNotification(Context ctx, List<String> appNames) {
        mContext = ctx;
        mAppNames = appNames;
    }

    /**
     * Start the notification.
     */
    public void startNotification() {
        if (mAppNames.size() == 0) return;

        Notification.Builder builder = new Notification.Builder(mContext, NFC_NOTIFICATION_CHANNEL);
        String formatString;
        if (mAppNames.size() == 1) {
            formatString = mContext.getString(R.string.tag_app_alert_message);
            builder.setContentText(String.format(formatString, mAppNames.get(0)));
        } else if (mAppNames.size() > 1) {
            StringBuilder sb = new StringBuilder();
            formatString = mContext.getString(R.string.tag_app_alert_message_multiple);
            mAppNames.forEach(name -> sb.append(String.format("\n%s", name)));
            String message = String.format(formatString, sb.toString());
            builder.setContentText(message)
                    .setStyle(new Notification.BigTextStyle().bigText(message));
        }
        Intent infoIntent = new Intent().setAction(NfcAdapter.ACTION_CHANGE_TAG_INTENT_PREFERENCE);
        PendingIntent pIntent = PendingIntent.getActivity(mContext, 0, infoIntent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Action action = new Notification.Action.Builder(null,
                mContext.getString(R.string.tag_app_alert_action_button), pIntent).build();
        builder.setContentTitle(mContext.getString(R.string.tag_app_alert_title))
                .setSmallIcon(R.drawable.nfc_icon)
                .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
                .setAutoCancel(true)
                .addAction(action)
                .setContentIntent(pIntent);
        mNotificationChannel = new NotificationChannel(NFC_NOTIFICATION_CHANNEL,
                mContext.getString(R.string.nfcUserLabel), NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager notificationManager =
                mContext.getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(mNotificationChannel);
        notificationManager.notify(NOTIFICATION_ID_NFC, builder.build());
    }
}

