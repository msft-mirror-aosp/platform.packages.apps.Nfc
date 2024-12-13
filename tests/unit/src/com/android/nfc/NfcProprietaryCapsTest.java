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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NfcProprietaryCapsTest {

    private static final int PASSIVE_OBSERVE_MODE = 0;
    private static final int POLLING_FRAME_NTF = 1;
    private static final int POWER_SAVING_MODE = 2;
    private static final int AUTOTRANSACT_POLLING_LOOP_FILTER = 3;
    private static final int NUMBER_OF_EXIT_FRAMES_SUPPORTED = 4;

    @Test
    public void testCreateFromByteArraySupportWithoutRfDeactivation() {
        byte[] inputCaps = {
                (byte) PASSIVE_OBSERVE_MODE, 1, 2,
                (byte) POLLING_FRAME_NTF, 1, 1,
                (byte) POWER_SAVING_MODE, 1, 0,
                (byte) AUTOTRANSACT_POLLING_LOOP_FILTER, 1, 1,
                (byte) NUMBER_OF_EXIT_FRAMES_SUPPORTED, 1, 5
        };

        NfcProprietaryCaps result = NfcProprietaryCaps.createFromByteArray(inputCaps);
        assertNotNull(result);
        assertEquals(NfcProprietaryCaps.PassiveObserveMode.SUPPORT_WITHOUT_RF_DEACTIVATION,
                result.getPassiveObserveMode());
        assertTrue(result.isPollingFrameNotificationSupported());
        assertFalse(result.isPowerSavingModeSupported());
        assertTrue(result.isAutotransactPollingLoopFilterSupported());
        assertEquals(5, result.getNumberOfExitFramesSupported());
    }

    @Test
    public void testCreateFromByteArraySupportWithRfDeactivation() {
        byte[] inputCaps = {
                (byte) PASSIVE_OBSERVE_MODE, 1, 1,
                (byte) POLLING_FRAME_NTF, 1, 1,
                (byte) POWER_SAVING_MODE, 1, 0,
                (byte) AUTOTRANSACT_POLLING_LOOP_FILTER, 1, 1,
                (byte) NUMBER_OF_EXIT_FRAMES_SUPPORTED, 1, 5
        };

        NfcProprietaryCaps result = NfcProprietaryCaps.createFromByteArray(inputCaps);
        assertNotNull(result);
        assertEquals(NfcProprietaryCaps.PassiveObserveMode.SUPPORT_WITH_RF_DEACTIVATION,
                result.getPassiveObserveMode());
        assertTrue(result.isPollingFrameNotificationSupported());
        assertFalse(result.isPowerSavingModeSupported());
        assertTrue(result.isAutotransactPollingLoopFilterSupported());
        assertEquals(5, result.getNumberOfExitFramesSupported());
    }

    @Test
    public void testCreateFromByteArrayNotSupported() {
        byte[] inputCaps = {
                (byte) PASSIVE_OBSERVE_MODE, 1, 0,
                (byte) POLLING_FRAME_NTF, 1, 1,
                (byte) POWER_SAVING_MODE, 1, 0,
                (byte) AUTOTRANSACT_POLLING_LOOP_FILTER, 1, 1,
                (byte) NUMBER_OF_EXIT_FRAMES_SUPPORTED, 1, 5
        };

        NfcProprietaryCaps result = NfcProprietaryCaps.createFromByteArray(inputCaps);
        assertNotNull(result);
        assertEquals(NfcProprietaryCaps.PassiveObserveMode.NOT_SUPPORTED,
                result.getPassiveObserveMode());
        assertTrue(result.isPollingFrameNotificationSupported());
        assertFalse(result.isPowerSavingModeSupported());
        assertTrue(result.isAutotransactPollingLoopFilterSupported());
        assertEquals(5, result.getNumberOfExitFramesSupported());
    }

    @Test
    public void testCreateFromByteArrayWithInvalidData() {
        byte[] invalidCaps = {(byte) PASSIVE_OBSERVE_MODE, 2, 3}; // Invalid length

        NfcProprietaryCaps result = NfcProprietaryCaps.createFromByteArray(invalidCaps);
        assertNotNull(result);
        assertEquals(NfcProprietaryCaps.PassiveObserveMode.NOT_SUPPORTED,
                result.getPassiveObserveMode());
        assertFalse(result.isPollingFrameNotificationSupported());
        assertFalse(result.isPowerSavingModeSupported());
        assertFalse(result.isAutotransactPollingLoopFilterSupported());
        assertEquals(0, result.getNumberOfExitFramesSupported());
    }

    @Test
    public void testCreateFromByteArrayWithEmptyArray() {
        byte[] emptyCaps = {};

        NfcProprietaryCaps result = NfcProprietaryCaps.createFromByteArray(emptyCaps);
        assertNotNull(result);
        assertEquals(NfcProprietaryCaps.PassiveObserveMode.NOT_SUPPORTED,
                result.getPassiveObserveMode());
        assertFalse(result.isPollingFrameNotificationSupported());
        assertFalse(result.isPowerSavingModeSupported());
        assertFalse(result.isAutotransactPollingLoopFilterSupported());
        assertEquals(0, result.getNumberOfExitFramesSupported());
    }

    @Test
    public void testToString() {
        NfcProprietaryCaps caps = new NfcProprietaryCaps(
                NfcProprietaryCaps.PassiveObserveMode.SUPPORT_WITHOUT_RF_DEACTIVATION,
                true,
                false,
                true,
                5
        );
        String expected = "NfcProprietaryCaps{" +
                "passiveObserveMode=SUPPORT_WITHOUT_RF_DEACTIVATION, " +
                "isPollingFrameNotificationSupported=true, " +
                "isPowerSavingModeSupported=false, " +
                "isAutotransactPollingLoopFilterSupported=true}";

        assertEquals(expected, caps.toString());
    }
}
