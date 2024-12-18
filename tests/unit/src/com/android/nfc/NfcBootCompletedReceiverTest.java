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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.nfc.Constants;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class NfcBootCompletedReceiverTest {
    @Mock
    private Context mMockContext;
    @Mock
    private Intent mMockIntent;
    @Mock
    private PackageManager mMockPkgManager;
    private NfcBootCompletedReceiver mNfcBootCompletedReceiver;
    private static final String PACKAGE_NAME = "com.android.nfc";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getPackageManager()).thenReturn(mMockPkgManager);
        when(mMockContext.getPackageName()).thenReturn(PACKAGE_NAME);

        mNfcBootCompletedReceiver = new NfcBootCompletedReceiver();
    }

    @Test
    public void testFeatureNoNfc() {
        when(mMockIntent.getAction()).thenReturn(Intent.ACTION_BOOT_COMPLETED);
        when(mMockPkgManager.hasSystemFeature(Constants.FEATURE_NFC_ANY)).thenReturn(false);

        mNfcBootCompletedReceiver.onReceive(mMockContext, mMockIntent);
        verify(mMockPkgManager).setApplicationEnabledSetting(eq(PACKAGE_NAME),
                eq(PackageManager.COMPONENT_ENABLED_STATE_DISABLED),
                eq(0));
    }

    @Test
    public void testFeatureSupportNfc() {
        when(mMockIntent.getAction()).thenReturn(Intent.ACTION_BOOT_COMPLETED);
        when(mMockPkgManager.hasSystemFeature(Constants.FEATURE_NFC_ANY)).thenReturn(true);

        mNfcBootCompletedReceiver.onReceive(mMockContext, mMockIntent);
        verify(mMockPkgManager).setComponentEnabledSetting(
                eq(new ComponentName(mMockContext, NfcBootCompletedReceiver.class)),
                eq(PackageManager.COMPONENT_ENABLED_STATE_DISABLED),
                eq(PackageManager.DONT_KILL_APP));
    }

    @Test
    public void testFeatureNonBootCompleteAction() {
        when(mMockIntent.getAction()).thenReturn(Intent.ACTION_ALARM_CHANGED);

        mNfcBootCompletedReceiver.onReceive(mMockContext, mMockIntent);
        verifyNoMoreInteractions(mMockPkgManager);
    }
}
