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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.ApduServiceInfo;
import android.nfc.cardemulation.CardEmulation;
import android.nfc.cardemulation.HostApduService;
import android.nfc.cardemulation.OffHostApduService;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.nfc.NfcService;
import com.android.nfc.NfcStatsLog;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class RegisteredServicesCacheTest {

    private static final int USER_ID = 1;
    private static final UserHandle USER_HANDLE = UserHandle.of(USER_ID);
    private static final UserHandle USER_HANDLE_QUITE_MODE = UserHandle.of(2);
    private static final File DIR = new File("/");
    private static final String ANOTHER_PACKAGE_NAME = "com.android.test.another";
    private static final String NON_PAYMENT_NFC_PACKAGE_NAME = "com.android.test.nonpaymentnfc";
    private static final String WALLET_HOLDER_PACKAGE_NAME = "com.android.test.walletroleholder";
    private static final String ON_HOST_SERVICE_NAME
            = "com.android.test.walletroleholder.OnHostApduService";
    private static final String OFF_HOST_SERVICE_NAME
            = "com.android.test.another.OffHostApduService";
    private static final String NON_PAYMENT_SERVICE_NAME
            = "com.android.test.nonpaymentnfc.NonPaymentApduService";
    private static final ComponentName WALLET_HOLDER_SERVICE_COMPONENT =
            new ComponentName(WALLET_HOLDER_PACKAGE_NAME, ON_HOST_SERVICE_NAME);
    private static final ComponentName NON_PAYMENT_SERVICE_COMPONENT =
            new ComponentName(NON_PAYMENT_NFC_PACKAGE_NAME, NON_PAYMENT_SERVICE_NAME);
    private static final ComponentName ANOTHER_SERVICE_COMPONENT =
            new ComponentName(ANOTHER_PACKAGE_NAME, OFF_HOST_SERVICE_NAME);
    private static final String OFFHOST_SE_STRING = "offhostse";
    private static final String TRUE_STRING = "true";
    private static final String FALSE_STRING = "false";
    private static final String ANDROID_STRING = "android";
    private static final List<String> PAYMENT_AIDS = List.of("A000000004101011",
            "A000000004101012", "A000000004101013");
    private static final List<String> NON_PAYMENT_AID = List.of("F053414950454D");

    @Mock
    private Context mContext;
    @Mock
    private RegisteredServicesCache.Callback mCallback;
    @Mock
    private UserManager mUserManager;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private RegisteredServicesCache.SettingsFile mDynamicSettingsFile;
    @Mock
    private RegisteredServicesCache.SettingsFile mOtherSettingsFile;
    @Mock
    private RegisteredServicesCache.ServiceParser mServiceParser;
    @Captor
    private ArgumentCaptor<BroadcastReceiver> mReceiverArgumentCaptor;
    @Captor
    private ArgumentCaptor<IntentFilter> mIntentFilterArgumentCaptor;
    @Captor
    private ArgumentCaptor<PackageManager.ResolveInfoFlags> mFlagArgumentCaptor;
    @Captor
    private ArgumentCaptor<Intent> mIntentArgumentCaptor;
    @Captor
    private ArgumentCaptor<List<ApduServiceInfo>> mApduServiceListCaptor;

    private MockitoSession mStaticMockSession;
    private RegisteredServicesCache mRegisteredServicesCache;
    private Map<String, ApduServiceInfo> mMappedServices;

    @Before
    public void setUp() throws PackageManager.NameNotFoundException, XmlPullParserException,
            IOException {
        mStaticMockSession = ExtendedMockito.mockitoSession()
                .mockStatic(com.android.nfc.flags.Flags.class)
                .mockStatic(ActivityManager.class)
                .mockStatic(NfcStatsLog.class)
                .mockStatic(UserHandle.class)
                .mockStatic(NfcAdapter.class)
                .mockStatic(NfcService.class)
                .strictness(Strictness.LENIENT)
                .startMocking();
        MockitoAnnotations.initMocks(this);
        mMappedServices = new HashMap<>();
        when(UserHandle.getUserHandleForUid(eq(USER_ID))).thenReturn(USER_HANDLE);
        when(UserHandle.of(eq(USER_ID))).thenReturn(USER_HANDLE);
        when(ActivityManager.getCurrentUser()).thenReturn(USER_ID);
        when(mContext.getSystemService(eq(UserManager.class))).thenReturn(mUserManager);
        when(mContext.getFilesDir()).thenReturn(DIR);
        when(mContext.createContextAsUser(
                any(), anyInt())).thenReturn(mContext);
        when(mContext.createPackageContextAsUser(
                any(), anyInt(), any())).thenReturn(mContext);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        List<UserHandle> enabledProfiles = new ArrayList<>();
        enabledProfiles.add(USER_HANDLE);
        enabledProfiles.add(USER_HANDLE_QUITE_MODE);
        when(mUserManager.getEnabledProfiles()).thenReturn(enabledProfiles);
        when(mUserManager.isQuietModeEnabled(eq(USER_HANDLE))).thenReturn(false);
        when(mUserManager.isQuietModeEnabled(eq(USER_HANDLE_QUITE_MODE))).thenReturn(true);
        List<ResolveInfo> onHostServicesList = new ArrayList<>();
        onHostServicesList.add(createServiceResolveInfo(true,
                WALLET_HOLDER_PACKAGE_NAME, ON_HOST_SERVICE_NAME,
                List.of(CardEmulation.CATEGORY_PAYMENT)));
        onHostServicesList.add(createServiceResolveInfo(false,
                NON_PAYMENT_NFC_PACKAGE_NAME, NON_PAYMENT_SERVICE_NAME,
                List.of(CardEmulation.CATEGORY_OTHER)));
        List<ResolveInfo> offHostServicesList = new ArrayList<>();
        offHostServicesList.add(createServiceResolveInfo(true, ANOTHER_PACKAGE_NAME,
                OFF_HOST_SERVICE_NAME, List.of(CardEmulation.CATEGORY_OTHER)));
        when(mPackageManager.queryIntentServicesAsUser(
                any(), any(), any())).thenAnswer(invocation -> {
                    Intent intent = invocation.getArgument(0);
                    if(intent.getAction().equals(OffHostApduService.SERVICE_INTERFACE)) {
                        return offHostServicesList;
                    }
                    if(intent.getAction().equals(HostApduService.SERVICE_INTERFACE)) {
                        return onHostServicesList;
                    }
                    return List.of();
                });
        when(mServiceParser.parseApduService(any(), any(), anyBoolean()))
                .thenAnswer(invocation -> {
                    ResolveInfo resolveInfo = invocation.getArgument(1);
                    if(mMappedServices.containsKey(resolveInfo.serviceInfo.name)) {
                        return mMappedServices.get(resolveInfo.serviceInfo.name);
                    }
                    return null;
                });
    }

    @After
    public void tearDown() {
        mStaticMockSession.finishMocking();
    }

    // Intent filter registration is actually not happening. It's just a mock verification.
    @SuppressWarnings({"UnspecifiedRegisterReceiverFlag", "GuardedBy"})
    @Test
    public void testConstructor() {
        mRegisteredServicesCache = new RegisteredServicesCache(mContext, mCallback);

        // Verify that the users handles are populated correctly
        Assert.assertEquals(1, mRegisteredServicesCache.mUserHandles.size());
        Assert.assertEquals(mRegisteredServicesCache.mUserHandles.get(0), USER_HANDLE);
        // Verify that broadcast receivers for apk changes are created and registered properly
        Assert.assertNotNull(mRegisteredServicesCache.mReceiver.get());
        verify(mContext).createContextAsUser(eq(USER_HANDLE), eq(0));
        verify(mContext, times(2)).registerReceiverForAllUsers(
                mReceiverArgumentCaptor.capture(), mIntentFilterArgumentCaptor.capture(),
                eq(null), eq(null));
        IntentFilter packageInstallTrackerIntent = mIntentFilterArgumentCaptor
                .getAllValues().get(0);
        Assert.assertTrue(packageInstallTrackerIntent.hasAction(Intent.ACTION_PACKAGE_ADDED));
        Assert.assertTrue(packageInstallTrackerIntent.hasAction(Intent.ACTION_PACKAGE_CHANGED));
        Assert.assertTrue(packageInstallTrackerIntent.hasAction(Intent.ACTION_PACKAGE_REMOVED));
        Assert.assertTrue(packageInstallTrackerIntent.hasAction(Intent.ACTION_PACKAGE_REPLACED));
        Assert.assertTrue(packageInstallTrackerIntent
                .hasAction(Intent.ACTION_PACKAGE_FIRST_LAUNCH));
        Assert.assertTrue(packageInstallTrackerIntent.hasAction(Intent.ACTION_PACKAGE_RESTARTED));
        Assert.assertTrue(packageInstallTrackerIntent
                .hasDataScheme(RegisteredServicesCache.PACKAGE_DATA));
        IntentFilter sdCardIntentFilter = mIntentFilterArgumentCaptor.getAllValues().get(1);
        Assert.assertTrue(sdCardIntentFilter
                .hasAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE));
        Assert.assertTrue(sdCardIntentFilter
                .hasAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE));
        Assert.assertEquals(mRegisteredServicesCache.mReceiver.get(),
                mReceiverArgumentCaptor.getAllValues().get(0));
        Assert.assertEquals(mRegisteredServicesCache.mReceiver.get(),
                mReceiverArgumentCaptor.getAllValues().get(1));
        verify(mContext, times(2)).getFilesDir();
        // Verify that correct file setting directories are set
        Assert.assertEquals(mRegisteredServicesCache.mDynamicSettingsFile.getBaseFile()
                        .getParentFile(), DIR);
        Assert.assertEquals(mRegisteredServicesCache.mDynamicSettingsFile.getBaseFile()
                        .getAbsolutePath(), DIR + RegisteredServicesCache.AID_XML_PATH);
        Assert.assertEquals(mRegisteredServicesCache.mOthersFile.getBaseFile().getParentFile(),
                DIR);
        Assert.assertEquals(mRegisteredServicesCache.mOthersFile.getBaseFile()
                .getAbsolutePath(), DIR + RegisteredServicesCache.OTHER_STATUS_PATH);
    }

    @Test
    public void testInitialize_filesExist() throws IOException,
            PackageManager.NameNotFoundException {
        mRegisteredServicesCache = new RegisteredServicesCache(mContext, mCallback,
                mDynamicSettingsFile, mOtherSettingsFile, mServiceParser);
        InputStream dynamicSettingsIs = InstrumentationRegistry.getInstrumentation()
                .getContext().getResources().getAssets()
                .open(RegisteredServicesCache.AID_XML_PATH);
        InputStream otherSettingsIs = InstrumentationRegistry.getInstrumentation()
                .getContext().getResources().getAssets()
                .open(RegisteredServicesCache.OTHER_STATUS_PATH);
        when(mDynamicSettingsFile.openRead()).thenReturn(dynamicSettingsIs);
        when(mOtherSettingsFile.openRead()).thenReturn(otherSettingsIs);
        when(mDynamicSettingsFile.exists()).thenReturn(true);
        when(mOtherSettingsFile.exists()).thenReturn(true);

        mRegisteredServicesCache.initialize();

        // Verify that file operations are called
        verify(mDynamicSettingsFile).exists();
        verify(mOtherSettingsFile).exists();
        verify(mDynamicSettingsFile).openRead();
        verify(mOtherSettingsFile).openRead();
        // Verify that user services are read properly
        Assert.assertEquals(1, mRegisteredServicesCache.mUserServices.size());
        RegisteredServicesCache.UserServices userServices
                = mRegisteredServicesCache.mUserServices.get(USER_ID);
        Assert.assertEquals(2, userServices.services.size());
        Assert.assertTrue(userServices.services.containsKey(WALLET_HOLDER_SERVICE_COMPONENT));
        Assert.assertTrue(userServices.services.containsKey(ANOTHER_SERVICE_COMPONENT));
        Assert.assertEquals(3, userServices.dynamicSettings.size());
        // Verify that dynamic settings are read properly
        Assert.assertTrue(userServices.dynamicSettings
                .containsKey(WALLET_HOLDER_SERVICE_COMPONENT));
        Assert.assertTrue(userServices.dynamicSettings.containsKey(NON_PAYMENT_SERVICE_COMPONENT));
        // Verify that dynamic settings are properly populated for each service in the xml
        // Verify the details of service 1
        RegisteredServicesCache.DynamicSettings walletHolderSettings =
                userServices.dynamicSettings.get(WALLET_HOLDER_SERVICE_COMPONENT);
        Assert.assertEquals(OFFHOST_SE_STRING+"1", walletHolderSettings.offHostSE);
        Assert.assertEquals(1, walletHolderSettings.uid);
        Assert.assertEquals(TRUE_STRING, walletHolderSettings.shouldDefaultToObserveModeStr);
        Assert.assertTrue(walletHolderSettings.aidGroups
                .containsKey(CardEmulation.CATEGORY_PAYMENT));
        Assert.assertTrue(walletHolderSettings.aidGroups.get(CardEmulation.CATEGORY_PAYMENT)
                        .getAids().containsAll(PAYMENT_AIDS));
        Assert.assertFalse(walletHolderSettings.aidGroups
                .containsKey(CardEmulation.CATEGORY_OTHER));
        // Verify the details of service 2
        RegisteredServicesCache.DynamicSettings nonPaymentSettings =
                userServices.dynamicSettings.get(NON_PAYMENT_SERVICE_COMPONENT);
        Assert.assertEquals(OFFHOST_SE_STRING+"2", nonPaymentSettings.offHostSE);
        Assert.assertEquals(1, nonPaymentSettings.uid);
        Assert.assertEquals(FALSE_STRING, nonPaymentSettings.shouldDefaultToObserveModeStr);
        Assert.assertTrue(nonPaymentSettings.aidGroups
                .containsKey(CardEmulation.CATEGORY_OTHER));
        Assert.assertTrue(nonPaymentSettings.aidGroups.get(CardEmulation.CATEGORY_OTHER)
                .getAids().containsAll(NON_PAYMENT_AID));
        // Verify that other settings are read properly
        Assert.assertEquals(1, userServices.others.size());
        Assert.assertTrue(userServices.others.containsKey(ANOTHER_SERVICE_COMPONENT));
        RegisteredServicesCache.OtherServiceStatus otherServiceStatus
                = userServices.others.get(ANOTHER_SERVICE_COMPONENT);
        Assert.assertTrue(otherServiceStatus.checked);
        Assert.assertEquals(1, otherServiceStatus.uid);
        // Verify that the installed services are populated properly
        verify(mContext)
                .createPackageContextAsUser(eq(ANDROID_STRING), eq(0), eq(USER_HANDLE));
        verify(mContext).getPackageManager();
        verify(mPackageManager, times(2))
                .queryIntentServicesAsUser(mIntentArgumentCaptor.capture(),
                        mFlagArgumentCaptor.capture(), eq(USER_HANDLE));
        Intent onHostIntent = mIntentArgumentCaptor.getAllValues().get(0);
        Assert.assertEquals(HostApduService.SERVICE_INTERFACE, onHostIntent.getAction());
        Intent offHostIntent = mIntentArgumentCaptor.getAllValues().get(1);
        Assert.assertEquals(OffHostApduService.SERVICE_INTERFACE, offHostIntent.getAction());
        PackageManager.ResolveInfoFlags onHostFlag = mFlagArgumentCaptor.getAllValues().get(0);
        Assert.assertEquals(PackageManager.GET_META_DATA, onHostFlag.getValue());
        PackageManager.ResolveInfoFlags offHostFlag = mFlagArgumentCaptor.getAllValues().get(1);
        Assert.assertEquals(PackageManager.GET_META_DATA, offHostFlag.getValue());
        // Verify that the installed services are filtered properly
        verify(mPackageManager).checkPermission(eq(android.Manifest.permission.NFC),
                eq(WALLET_HOLDER_PACKAGE_NAME));
        verify(mPackageManager).checkPermission(eq(android.Manifest.permission.NFC),
                eq(NON_PAYMENT_NFC_PACKAGE_NAME));
        verify(mPackageManager).checkPermission(eq(android.Manifest.permission.NFC),
                eq(ANOTHER_PACKAGE_NAME));
        // Verify that the callback is called with properly installed and filtered services.
        verify(mCallback).onServicesUpdated(eq(USER_ID), mApduServiceListCaptor.capture(),
                eq(false));
        List<ApduServiceInfo> apduServiceInfos = mApduServiceListCaptor.getValue();
        Assert.assertEquals(2, apduServiceInfos.size());
        Assert.assertEquals(WALLET_HOLDER_SERVICE_COMPONENT, apduServiceInfos.get(0)
                .getComponent());
        Assert.assertEquals(ANOTHER_SERVICE_COMPONENT, apduServiceInfos.get(1).getComponent());
    }

    private ResolveInfo createServiceResolveInfo(boolean hasPermission,
                                                 String packageName, String className,
                                                 List<String> categories) {
        when(mPackageManager.checkPermission(any(), eq(packageName)))
                .thenReturn(hasPermission ? PackageManager.PERMISSION_GRANTED
                        : PackageManager.PERMISSION_DENIED);
        ApduServiceInfo apduServiceInfo = Mockito.mock(ApduServiceInfo.class);
        when(apduServiceInfo.getComponent()).thenReturn(new ComponentName(packageName, className));
        if (!categories.isEmpty()) {
            for(String category : categories) {
               when(apduServiceInfo.hasCategory(category)).thenReturn(true);
            }
        }
        mMappedServices.put(className, apduServiceInfo);

        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo = new ServiceInfo();
        resolveInfo.serviceInfo.permission = android.Manifest.permission.BIND_NFC_SERVICE;
        resolveInfo.serviceInfo.exported = true;
        resolveInfo.serviceInfo.packageName = packageName;
        resolveInfo.serviceInfo.name = className;
        return resolveInfo;
    }
}
