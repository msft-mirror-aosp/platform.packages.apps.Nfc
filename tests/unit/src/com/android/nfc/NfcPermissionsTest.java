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

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static com.android.nfc.NfcPermissions.NFC_PERMISSION;
import static com.android.nfc.NfcPermissions.NFC_PREFERRED_PAYMENT_INFO_PERMISSION;
import static com.android.nfc.NfcPermissions.NFC_SET_CONTROLLER_ALWAYS_ON;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

public class NfcPermissionsTest {

    @Mock
    private Context mMockContext;
    @Mock
    private AppOpsManager mMockAppOpsMngr;
    @Mock
    private UserManager mMockUserMngr;
    @Mock
    private UserHandle mMockUserHandle;
    private MockitoSession mStaticMockSession;
    private NfcPermissions mNfcPermissions;

    @Before
    public void setUp() {
        mStaticMockSession = ExtendedMockito.mockitoSession()
                .mockStatic(UserHandle.class)
                .strictness(Strictness.LENIENT).startMocking();
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getSystemService(AppOpsManager.class)).thenReturn(mMockAppOpsMngr);

        mNfcPermissions = new NfcPermissions(mMockContext);
    }

    @After
    public void tearDown() {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testValidateProfileIdOfCurrentUser() {
        Context mockContextAsUser = mock(Context.class);
        List<UserHandle> luh = Collections.singletonList(mMockUserHandle);
        int profileId = 1001;
        when(mMockContext.createContextAsUser(any(), anyInt())).thenReturn(mockContextAsUser);
        when(mockContextAsUser.getSystemService(UserManager.class)).thenReturn(mMockUserMngr);
        when(mMockUserMngr.getEnabledProfiles()).thenReturn(luh);
        when(mMockUserHandle.getIdentifier()).thenReturn(profileId);

        NfcPermissions.validateProfileId(mMockContext, profileId);
        verify(mMockUserMngr).getEnabledProfiles();
    }

    @Test(expected = SecurityException.class)
    public void testValidateProfileIdOfDifferentUser() {
        Context mockContextAsUser = mock(Context.class);
        List<UserHandle> luh = Collections.singletonList(mMockUserHandle);
        int currentProfileId = 1001;
        int diffProfileId = 1122;
        when(mMockContext.createContextAsUser(any(), anyInt())).thenReturn(mockContextAsUser);
        when(mockContextAsUser.getSystemService(UserManager.class)).thenReturn(mMockUserMngr);
        when(mMockUserMngr.getEnabledProfiles()).thenReturn(luh);
        when(mMockUserHandle.getIdentifier()).thenReturn(currentProfileId);

        NfcPermissions.validateProfileId(mMockContext, diffProfileId);
    }

    @Test
    public void testEnforceAdminPermissions() {
        NfcPermissions.enforceAdminPermissions(mMockContext);
        verify(mMockContext).enforceCallingOrSelfPermission(
                android.Manifest.permission.WRITE_SECURE_SETTINGS,
                "WRITE_SECURE_SETTINGS permission required");
    }

    @Test
    public void testCheckAdminPermissions() {
        when(mMockContext.checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_SECURE_SETTINGS)).thenReturn(PERMISSION_GRANTED);

        assertTrue(NfcPermissions.checkAdminPermissions(mMockContext));
        verify(mMockContext).checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_SECURE_SETTINGS);
    }

    @Test
    public void testEnforceUserPermissions() {
        NfcPermissions.enforceUserPermissions(mMockContext);
        verify(mMockContext).enforceCallingOrSelfPermission(NFC_PERMISSION,
                "NFC permission required");
    }

    @Test
    public void testEnforcePreferredPaymentInfoPermissions() {
        NfcPermissions.enforcePreferredPaymentInfoPermissions(mMockContext);
        verify(mMockContext).enforceCallingOrSelfPermission(NFC_PREFERRED_PAYMENT_INFO_PERMISSION,
                "NFC_PREFERRED_PAYMENT_INFO permission required");
    }

    @Test
    public void testEnforceSetControllerAlwaysOnPermissions() {
        NfcPermissions.enforceSetControllerAlwaysOnPermissions(mMockContext);
        verify(mMockContext).enforceCallingOrSelfPermission(NFC_SET_CONTROLLER_ALWAYS_ON,
                "NFC_SET_CONTROLLER_ALWAYS_ON permission required");
    }

    @Test
    public void testRetrieveDevicePolicyManagerFromContext() {
        DevicePolicyManager mockDevicePolicyManager = mock(DevicePolicyManager.class);
        PackageManager mockPkgMagr = mock(PackageManager.class);
        when(mMockContext.getSystemService(DevicePolicyManager.class)).thenReturn(
                mockDevicePolicyManager);
        when(mMockContext.getPackageManager()).thenReturn(mockPkgMagr);
        when(mockPkgMagr.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN)).thenReturn(true);

        assertEquals(mockDevicePolicyManager,
                NfcPermissions.retrieveDevicePolicyManagerFromContext(mMockContext));
    }

    @Test
    public void testIsDeviceOwnerWhenPackageNameNull() {
        assertFalse(mNfcPermissions.isDeviceOwner(1, null));
    }

    @Test
    public void testISDeviceOwnerWhenDevicePolicyManagerNull()
            throws PackageManager.NameNotFoundException {
        when(mMockContext.createPackageContextAsUser(anyString(), anyInt(), any()))
                .thenReturn(null);

        assertFalse(mNfcPermissions.isDeviceOwner(101, "com.sample.package"));
    }

    @Test
    public void testISDeviceOwnerWhenPackageRegisterAsDeviceOwner()
            throws PackageManager.NameNotFoundException {
        String packageName = "com.sample.package";
        Context mockUserContext = mock(Context.class);
        DevicePolicyManager mockDevicePolicyManager = mock(DevicePolicyManager.class);
        PackageManager mockPkgMagr = mock(PackageManager.class);
        when(mockPkgMagr.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN)).thenReturn(true);
        when(UserHandle.getUserHandleForUid(101)).thenReturn(mMockUserHandle);
        when(mMockContext.getPackageName()).thenReturn(packageName);
        when(mMockContext.createPackageContextAsUser(packageName, 0,
                mMockUserHandle)).thenReturn(mockUserContext);
        when(mockUserContext.getSystemService(DevicePolicyManager.class))
                .thenReturn(mockDevicePolicyManager);
        when(mockUserContext.getPackageManager()).thenReturn(mockPkgMagr);
        when(mockDevicePolicyManager.isDeviceOwnerApp(packageName)).thenReturn(true);

        assertTrue(mNfcPermissions.isDeviceOwner(101, packageName));
    }

    @Test(expected = SecurityException.class)
    public void testCheckPackageWithNullPackageName() {
        mNfcPermissions.checkPackage(101, null);
    }

    @Test
    public void testCheckPackageWithPackageName() {
        String packageName = "com.sample.package";

        mNfcPermissions.checkPackage(101, packageName);
        verify(mMockAppOpsMngr).checkPackage(101, packageName);
    }

    @Test
    public void testIsProfileOwnerWhenPackageNameNull() {
        assertFalse(mNfcPermissions.isProfileOwner(1, null));
    }

    @Test
    public void testIsProfileOwnerWhenDevicePolicyManagerNull()
            throws PackageManager.NameNotFoundException {
        when(mMockContext.createPackageContextAsUser(anyString(), anyInt(), any()))
                .thenReturn(null);

        assertFalse(mNfcPermissions.isProfileOwner(101, "com.sample.package"));
    }

    @Test
    public void testIsProfileOwnerWhenPackageRegisterAsProfileOwner()
            throws PackageManager.NameNotFoundException {
        String packageName = "com.sample.package";
        Context mockUserContext = mock(Context.class);
        DevicePolicyManager mockDevicePolicyManager = mock(DevicePolicyManager.class);
        PackageManager mockPkgMagr = mock(PackageManager.class);
        when(mockPkgMagr.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN)).thenReturn(true);
        when(UserHandle.getUserHandleForUid(101)).thenReturn(mMockUserHandle);
        when(mMockContext.getPackageName()).thenReturn(packageName);
        when(mMockContext.createPackageContextAsUser(packageName, 0,
                mMockUserHandle)).thenReturn(mockUserContext);
        when(mockUserContext.getSystemService(DevicePolicyManager.class))
                .thenReturn(mockDevicePolicyManager);
        when(mockUserContext.getPackageManager()).thenReturn(mockPkgMagr);
        when(mockDevicePolicyManager.isProfileOwnerApp(packageName)).thenReturn(true);

        assertTrue(mNfcPermissions.isProfileOwner(101, packageName));
    }
}
