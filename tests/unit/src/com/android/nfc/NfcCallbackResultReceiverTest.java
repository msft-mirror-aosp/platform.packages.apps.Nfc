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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.os.Bundle;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CountDownLatch;

public class NfcCallbackResultReceiverTest {

    @Mock
    private CountDownLatch mMockCountDownLatch;
    @Mock
    private NfcCallbackResultReceiver.OnReceiveResultListener mMockListener;
    private NfcCallbackResultReceiver mNfcCallbackResultReceiver;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mNfcCallbackResultReceiver = new NfcCallbackResultReceiver(mMockCountDownLatch,
                mMockListener);
    }

    @Test
    public void testOnReceiveResult() {
        Bundle mockBundle = mock(Bundle.class);
        mNfcCallbackResultReceiver.onReceiveResult(1, mockBundle);
        verify(mMockCountDownLatch).countDown();
        verify(mMockListener).onReceiveResult(1, mockBundle);
    }

    @Test
    public void testListenerOnReceiveResult() {
        Bundle mockBundle = mock(Bundle.class);
        NfcCallbackResultReceiver.OnReceiveResultListener onReceiveListener =
                new NfcCallbackResultReceiver.OnReceiveResultListener();
        onReceiveListener.onReceiveResult(1, mockBundle);
        assertEquals(1, onReceiveListener.getResultCode());
        assertEquals(mockBundle, onReceiveListener.getResultData());
    }
}
