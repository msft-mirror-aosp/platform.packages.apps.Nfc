/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.nfc.tech.Ndef.EXTRA_NDEF_MSG;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothProtoEnums;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcBarcode;
import android.nfc.tech.TagTechnology;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.UserManager;
import android.os.test.TestLooper;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.nfc.handover.HandoverDataParser;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RunWith(AndroidJUnit4.class)
public final class NfcDispatcherTest {

    private static final String TAG = NfcDispatcherTest.class.getSimpleName();
    @Mock private NfcInjector mNfcInjector;
    private MockitoSession mStaticMockSession;
    private NfcDispatcher mNfcDispatcher;
    TestLooper mLooper;

    @Mock
    private Context mockContext;
    @Mock
    private Resources mResources;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private PowerManager mPowerManager;
    @Mock
    KeyguardManager mKeyguardManager;
    @Mock
    DisplayManager mDisplayManager;
    @Mock
    UserManager mUserManager;
    @Mock
    ActivityManager mActivityManager;
    @Mock
    NfcAdapter mNfcAdapter;
    @Mock
    ForegroundUtils mForegroundUtils;

    @Before
    public void setUp() throws PackageManager.NameNotFoundException {
        mLooper = new TestLooper();
        mStaticMockSession = ExtendedMockito.mockitoSession()
                .mockStatic(NfcStatsLog.class)
                .mockStatic(android.nfc.Flags.class)
                .mockStatic(NfcAdapter.class)
                .mockStatic(Ndef.class)
                .mockStatic(ForegroundUtils.class)
                .mockStatic(NfcWifiProtectedSetup.class)
                .strictness(Strictness.LENIENT)
                .startMocking();

        MockitoAnnotations.initMocks(this);

        when(mPowerManager.isInteractive()).thenReturn(false);
        when(mockContext.getSystemService(PowerManager.class)).thenReturn(mPowerManager);
        when(mockContext.getSystemService(KeyguardManager.class)).thenReturn(mKeyguardManager);
        when(mockContext.getSystemService(DisplayManager.class)).thenReturn(mDisplayManager);
        when(mockContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        when(mockContext.getSystemService(ActivityManager.class)).thenReturn(mActivityManager);
        when(ForegroundUtils.getInstance(mActivityManager)).thenReturn(mForegroundUtils);
        when(mockContext.createPackageContextAsUser(anyString(), anyInt(), any()))
                .thenReturn(mockContext);
        when(mockContext.createContextAsUser(any(), anyInt())).thenReturn(mockContext);
        when(mockContext.getPackageManager()).thenReturn(mPackageManager);
        when(mockContext.getApplicationContext()).thenReturn(mockContext);
        when(mResources.getBoolean(R.bool.tag_intent_app_pref_supported)).thenReturn(true);
        when(mockContext.getResources()).thenReturn(mResources);
        when(NfcAdapter.getDefaultAdapter(mockContext)).thenReturn(mNfcAdapter);

        mNfcDispatcher = new NfcDispatcher(mockContext,
                new HandoverDataParser(), mNfcInjector, true);
        mLooper.dispatchAll();
    }

    @After
    public void tearDown() {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testLogOthers() {
        Tag tag = Tag.createMockTag(null, new int[0], new Bundle[0], 0L);
        mNfcDispatcher.dispatchTag(tag);
        ExtendedMockito.verify(() ->  NfcStatsLog.write(
                NfcStatsLog.NFC_TAG_OCCURRED,
                NfcStatsLog.NFC_TAG_OCCURRED__TYPE__PROVISION,
                -1,
                tag.getTechCodeList(),
                BluetoothProtoEnums.MAJOR_CLASS_UNCATEGORIZED,
                ""));
    }

    @Test
    public void testSetForegroundDispatchForWifiConnect() {
        PendingIntent pendingIntent = mock(PendingIntent.class);
        mNfcDispatcher.setForegroundDispatch(pendingIntent, new IntentFilter[]{},
                new String[][]{});
        Bundle bundle = mock(Bundle.class);
        when(bundle.getParcelable(EXTRA_NDEF_MSG, android.nfc.NdefMessage.class)).thenReturn(
                mock(
                        NdefMessage.class));
        Tag tag = Tag.createMockTag(null, new int[]{1}, new Bundle[]{bundle}, 0L);
        Ndef ndef = mock(Ndef.class);
        when(Ndef.get(tag)).thenReturn(ndef);
        NdefMessage ndefMessage = mock(NdefMessage.class);
        when(ndef.getCachedNdefMessage()).thenReturn(ndefMessage);
        NdefRecord ndefRecord = mock(NdefRecord.class);
        NdefRecord[] records = {ndefRecord};
        when(ndefMessage.getRecords()).thenReturn(records);
        when(NfcWifiProtectedSetup.tryNfcWifiSetup(ndef, mockContext)).thenReturn(true);
        mNfcDispatcher.dispatchTag(tag);
        ExtendedMockito.verify(() -> NfcStatsLog.write(
                NfcStatsLog.NFC_TAG_OCCURRED,
                NfcStatsLog.NFC_TAG_OCCURRED__TYPE__WIFI_CONNECT,
                -1,
                tag.getTechCodeList(),
                BluetoothProtoEnums.MAJOR_CLASS_UNCATEGORIZED,
                ""));
    }

    @Test
    public void testPeripheralHandoverBTParing() {
        String btOobPayload = "00060E4C00520100000000000000000000000000000000000000000001";
        Bundle bundle = mock(Bundle.class);
        when(bundle.getParcelable(EXTRA_NDEF_MSG, android.nfc.NdefMessage.class)).thenReturn(
                mock(
                        NdefMessage.class));
        Tag tag = Tag.createMockTag(null, new int[]{1}, new Bundle[]{bundle}, 0L);
        NdefMessage ndefMessage = mock(NdefMessage.class);
        NdefRecord ndefRecord = mock(NdefRecord.class);
        when(ndefRecord.getType()).thenReturn("application/vnd.bluetooth.ep.oob"
                .getBytes(StandardCharsets.US_ASCII));
        when(ndefRecord.getTnf()).thenReturn(NdefRecord.TNF_MIME_MEDIA);
        when(ndefRecord.getPayload()).thenReturn(btOobPayload.getBytes(StandardCharsets.US_ASCII));
        NdefRecord[] records = {ndefRecord};
        when(ndefMessage.getRecords()).thenReturn(records);
        mNfcDispatcher.tryPeripheralHandover(ndefMessage, tag);
        ExtendedMockito.verify(() -> NfcStatsLog.write(
                NfcStatsLog.NFC_TAG_OCCURRED,
                NfcStatsLog.NFC_TAG_OCCURRED__TYPE__BT_PAIRING,
                -1,
                tag.getTechCodeList(),
                BluetoothProtoEnums.MAJOR_CLASS_UNCATEGORIZED,
                ""));
    }

    @Test
    public void testCheckForAar() {
        NdefRecord ndefRecord = mock(NdefRecord.class);
        when(ndefRecord.getTnf()).thenReturn(NdefRecord.TNF_EXTERNAL_TYPE);
        when(ndefRecord.getType()).thenReturn(NdefRecord.RTD_ANDROID_APP);
        when(ndefRecord.getPayload()).thenReturn("test".getBytes(StandardCharsets.US_ASCII));
        String result = NfcDispatcher.checkForAar(ndefRecord);
        assertThat(result).isEqualTo("test");
    }

    @Test
    public void testCreateNfcResolverIntent() {
        when(mResources.getBoolean(eq(R.bool.tag_intent_app_pref_supported))).thenReturn(true);
        Tag tag = mock(Tag.class);
        NdefMessage ndefMessage = mock(NdefMessage.class);
        NdefRecord ndefRecord = NdefRecord.createUri("https://www.example.com");
        when(ndefMessage.getRecords()).thenReturn(new NdefRecord[]{ndefRecord});
        NfcDispatcher.DispatchInfo dispatchInfo = new NfcDispatcher
                .DispatchInfo(mockContext, tag, ndefMessage);
        ResolveInfo activity = mock(ResolveInfo.class);
        ActivityInfo activityInfo = mock(ActivityInfo.class);
        activityInfo.packageName = "com.android.nfc";
        activityInfo.name = "test";
        ApplicationInfo applicationInfo = mock(ApplicationInfo.class);
        applicationInfo.uid = 0;
        activityInfo.applicationInfo = applicationInfo;
        activity.activityInfo = activityInfo;

        ResolveInfo activity2 = mock(ResolveInfo.class);
        ActivityInfo activityInfo2 = mock(ActivityInfo.class);
        activityInfo2.packageName = "com.android.nfc2";
        activityInfo2.name = "test2";
        ApplicationInfo applicationInfo2 = mock(ApplicationInfo.class);
        applicationInfo2.uid = 1;
        activityInfo2.applicationInfo = applicationInfo2;
        activity2.activityInfo = activityInfo2;

        List<ResolveInfo> activities = new ArrayList<>();
        activities.add(activity);
        activities.add(activity2);
        Map<String, Boolean> prefList = new HashMap<>();
        prefList.put("com.android.nfc", false);
        when(mNfcAdapter.getTagIntentAppPreferenceForUser(0)).thenReturn(prefList);
        Assert.assertNotNull(dispatchInfo.intent);
        dispatchInfo.intent.setAction(NfcAdapter.ACTION_TECH_DISCOVERED);
        when(android.nfc.Flags.enableNfcMainline()).thenReturn(true);
        dispatchInfo.checkPrefList(activities, 0);

        assertThat(dispatchInfo.rootIntent).isNotNull();
        assertThat(dispatchInfo.rootIntent.getAction()).isNotNull();
        assertThat(dispatchInfo.rootIntent.getAction())
                .isEqualTo(NfcAdapter.ACTION_SHOW_NFC_RESOLVER);
    }

    @Test
    public void testDecodeNfcBarcodeUri() {
        PendingIntent pendingIntent = mock(PendingIntent.class);
        IntentFilter[] intentFilters = {};
        String[][] techLists = {{"Ndef"}};
        mNfcDispatcher.setForegroundDispatch(pendingIntent, intentFilters, techLists);
        ArgumentCaptor<Integer> callingUid = ArgumentCaptor.forClass(Integer.class);
        verify(mForegroundUtils).registerUidToBackgroundCallback(any(), callingUid.capture());
        Tag tag = mock(Tag.class);
        Bundle bundle = new Bundle();
        bundle.putInt(NfcBarcode.EXTRA_BARCODE_TYPE, NfcBarcode.TYPE_KOVIO);
        when(tag.getTechExtras(TagTechnology.NFC_BARCODE)).thenReturn(bundle);
        when(tag.hasTech(TagTechnology.NFC_BARCODE)).thenReturn(true);
        when(tag.getId()).thenReturn(new byte[]{0x04, 0x01, 0x64, 0x0C});
        when(tag.getTechList()).thenReturn(new String[]{"Ndef"});
        mNfcDispatcher.dispatchTag(tag);
        ExtendedMockito.verify(() -> NfcStatsLog.write(
                NfcStatsLog.NFC_TAG_OCCURRED,
                NfcStatsLog.NFC_TAG_OCCURRED__TYPE__FOREGROUND_DISPATCH,
                callingUid.getValue(),
                tag.getTechCodeList(),
                BluetoothProtoEnums.MAJOR_CLASS_UNCATEGORIZED,
                ""));
    }
}
