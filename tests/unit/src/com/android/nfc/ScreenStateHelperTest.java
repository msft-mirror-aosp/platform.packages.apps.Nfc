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

import static com.android.nfc.ScreenStateHelper.SCREEN_POLLING_READER_MASK;
import static com.android.nfc.ScreenStateHelper.SCREEN_STATE_OFF_LOCKED;
import static com.android.nfc.ScreenStateHelper.SCREEN_STATE_OFF_UNLOCKED;
import static com.android.nfc.ScreenStateHelper.SCREEN_STATE_ON_LOCKED;
import static com.android.nfc.ScreenStateHelper.SCREEN_STATE_ON_UNLOCKED;
import static com.android.nfc.ScreenStateHelper.SCREEN_STATE_UNKNOWN;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.KeyguardManager;
import android.hardware.display.DisplayManager;
import android.os.PowerManager;
import android.view.Display;

import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

public class ScreenStateHelperTest {
    @Mock
    private PowerManager mMockPowerMngr;
    @Mock
    private KeyguardManager mMockKeyguardMngr;
    @Mock
    private DisplayManager mMockDisplayMngr;
    @Mock
    private NfcInjector mMockNfcInjector;
    private MockitoSession mStaticMockSession;
    private ScreenStateHelper mScreenStateHelper;

    @Before
    public void setUp() {
        mStaticMockSession = ExtendedMockito.mockitoSession()
                .mockStatic(NfcInjector.class)
                .strictness(Strictness.LENIENT).startMocking();
        MockitoAnnotations.initMocks(this);
        mScreenStateHelper = new ScreenStateHelper(mMockKeyguardMngr, mMockPowerMngr,
                mMockDisplayMngr);
        when(NfcInjector.getInstance()).thenReturn(mMockNfcInjector);
    }

    @After
    public void tearDown() {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testScreenStateToStringOffLocked() {
        assertEquals("OFF_LOCKED", ScreenStateHelper.screenStateToString(SCREEN_STATE_OFF_LOCKED));
    }

    @Test
    public void testScreenStateToStringOnLocked() {
        assertEquals("ON_LOCKED", ScreenStateHelper.screenStateToString(SCREEN_STATE_ON_LOCKED));
    }

    @Test
    public void testScreenStateToStringOnUnlocked() {
        assertEquals("ON_UNLOCKED",
                ScreenStateHelper.screenStateToString(SCREEN_STATE_ON_UNLOCKED));
    }

    @Test
    public void testScreenStateToStringOffUnlocked() {
        assertEquals("OFF_UNLOCKED",
                ScreenStateHelper.screenStateToString(SCREEN_STATE_OFF_UNLOCKED));
    }

    @Test
    public void testScreenStateToStringUnknown() {
        assertEquals("UNKNOWN", ScreenStateHelper.screenStateToString(SCREEN_POLLING_READER_MASK));
    }

    @Test
    public void testScreenStateToProtoEnumOffLocked() {
        assertEquals(NfcServiceDumpProto.SCREEN_STATE_OFF_LOCKED,
                ScreenStateHelper.screenStateToProtoEnum(SCREEN_STATE_OFF_LOCKED));
    }

    @Test
    public void testScreenStateToProtoEnumOnLocked() {
        assertEquals(NfcServiceDumpProto.SCREEN_STATE_ON_LOCKED,
                ScreenStateHelper.screenStateToProtoEnum(SCREEN_STATE_ON_LOCKED));
    }

    @Test
    public void testScreenStateToProtoEnumOnUnlocked() {
        assertEquals(NfcServiceDumpProto.SCREEN_STATE_ON_UNLOCKED,
                ScreenStateHelper.screenStateToProtoEnum(SCREEN_STATE_ON_UNLOCKED));
    }

    @Test
    public void testScreenStateToProtoEnumOffUnlocked() {
        assertEquals(NfcServiceDumpProto.SCREEN_STATE_OFF_UNLOCKED,
                ScreenStateHelper.screenStateToProtoEnum(SCREEN_STATE_OFF_UNLOCKED));
    }

    @Test
    public void testScreenStateToProtoEnumUnknown() {
        assertEquals(NfcServiceDumpProto.SCREEN_STATE_UNKNOWN,
                ScreenStateHelper.screenStateToProtoEnum(SCREEN_STATE_UNKNOWN));
    }

    @Test
    public void testCheckScreenStateOffLocked() {
        Display mockDisplay = mock(Display.class);
        when(mMockPowerMngr.isInteractive()).thenReturn(true);
        when(mMockDisplayMngr.getDisplay(anyInt())).thenReturn(mockDisplay);
        when((mockDisplay.getState())).thenReturn(Display.STATE_OFF);
        when(mMockNfcInjector.isDeviceLocked()).thenReturn(true);

        assertEquals(SCREEN_STATE_OFF_LOCKED, mScreenStateHelper.checkScreenState(true));
    }

    @Test
    public void testCheckScreenStateOffUnLocked() {
        Display mockDisplay = mock(Display.class);
        when(mMockPowerMngr.isInteractive()).thenReturn(true);
        when(mMockDisplayMngr.getDisplay(anyInt())).thenReturn(mockDisplay);
        when((mockDisplay.getState())).thenReturn(Display.STATE_OFF);
        when(mMockNfcInjector.isDeviceLocked()).thenReturn(false);

        assertEquals(SCREEN_STATE_OFF_UNLOCKED, mScreenStateHelper.checkScreenState(true));
    }

    @Test
    public void testCheckScreenStateOnLocked() {
        when(mMockPowerMngr.isInteractive()).thenReturn(true);
        when(mMockNfcInjector.isDeviceLocked()).thenReturn(true);

        assertEquals(SCREEN_STATE_ON_LOCKED, mScreenStateHelper.checkScreenState(false));
    }

    @Test
    public void testCheckScreenStateOnUnlocked() {
        when(mMockPowerMngr.isInteractive()).thenReturn(true);
        when(mMockNfcInjector.isDeviceLocked()).thenReturn(false);

        assertEquals(SCREEN_STATE_ON_UNLOCKED, mScreenStateHelper.checkScreenState(false));
    }

    @Test
    public void testCheckScreenStateProvisionModeOffLocked() {
        when(mMockPowerMngr.isInteractive()).thenReturn(false);
        when(mMockNfcInjector.isDeviceLocked()).thenReturn(true);

        assertEquals(SCREEN_STATE_OFF_LOCKED, mScreenStateHelper.checkScreenStateProvisionMode());
    }

    @Test
    public void testCheckScreenStateProvisionModeOffUnLocked() {
        when(mMockPowerMngr.isInteractive()).thenReturn(false);
        when(mMockNfcInjector.isDeviceLocked()).thenReturn(false);

        assertEquals(SCREEN_STATE_OFF_UNLOCKED, mScreenStateHelper.checkScreenStateProvisionMode());
    }

    @Test
    public void testCheckScreenStateProvisionModeOnLocked() {
        when(mMockPowerMngr.isInteractive()).thenReturn(true);
        when(mMockNfcInjector.isDeviceLocked()).thenReturn(true);

        assertEquals(SCREEN_STATE_ON_LOCKED, mScreenStateHelper.checkScreenStateProvisionMode());
    }

    @Test
    public void testCheckScreenStateProvisionModeOnUnlocked() {
        when(mMockPowerMngr.isInteractive()).thenReturn(true);
        when(mMockNfcInjector.isDeviceLocked()).thenReturn(false);

        assertEquals(SCREEN_STATE_ON_UNLOCKED, mScreenStateHelper.checkScreenStateProvisionMode());
    }
}
