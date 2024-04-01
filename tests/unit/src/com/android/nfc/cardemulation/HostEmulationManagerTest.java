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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.ApduServiceInfo;
import android.nfc.cardemulation.HostApduService;
import android.nfc.cardemulation.PollingFrame;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.dx.mockito.inline.extended.ExtendedMockito;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class HostEmulationManagerTest {

    private static final String WALLET_HOLDER_PACKAGE_NAME = "com.android.test.walletroleholder";
    private static final ComponentName WALLET_PAYMENT_SERVICE
            = new ComponentName(WALLET_HOLDER_PACKAGE_NAME,
            "com.android.test.walletroleholder.WalletRoleHolderApduService");
    private static final int USER_ID = 0;
    private static final UserHandle USER_HANDLE = UserHandle.of(USER_ID);
    private static final String PL_FILTER = "66696C746572";
    private static final Pattern PL_PATTERN = Pattern.compile("66696C*46572");
    private static final List<String> POLLING_LOOP_FILTER = List.of(PL_FILTER);
    private static final List<Pattern> POLLING_LOOP_PATTEN_FILTER
            = List.of(PL_PATTERN);

    @Mock
    private Context mContext;
    @Mock
    private RegisteredAidCache mRegisteredAidCache;
    @Mock
    private PowerManager mPowerManager;
    @Mock
    private KeyguardManager mKeyguardManager;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private NfcAdapter mNfcAdapter;
    @Mock
    private Messenger mMessanger;
    @Captor
    private ArgumentCaptor<Intent> mIntentArgumentCaptor;
    @Captor
    private ArgumentCaptor<ServiceConnection> mServiceConnectionArgumentCaptor;
    @Captor
    private ArgumentCaptor<List<ApduServiceInfo>> mServiceListArgumentCaptor;
    @Captor
    private ArgumentCaptor<Message> mMessageArgumentCaptor;

    private MockitoSession mStaticMockSession;
    private TestableLooper mTestableLooper;
    private HostEmulationManager mHostEmulationManager;

    @Before
    public void setUp() {
        mStaticMockSession = ExtendedMockito.mockitoSession()
                .mockStatic(com.android.nfc.flags.Flags.class)
                .mockStatic(UserHandle.class)
                .mockStatic(NfcAdapter.class)
                .strictness(Strictness.LENIENT)
                .startMocking();
        MockitoAnnotations.initMocks(this);
        mTestableLooper = TestableLooper.get(this);
        when(NfcAdapter.getDefaultAdapter(mContext)).thenReturn(mNfcAdapter);
        when(UserHandle.getUserHandleForUid(eq(USER_ID))).thenReturn(USER_HANDLE);
        when(com.android.nfc.flags.Flags.statsdCeEventsFlag()).thenReturn(true);
        when(mContext.getSystemService(eq(PowerManager.class))).thenReturn(mPowerManager);
        when(mContext.getSystemService(eq(KeyguardManager.class))).thenReturn(mKeyguardManager);
        mHostEmulationManager = new HostEmulationManager(mContext, mTestableLooper.getLooper(),
                mRegisteredAidCache);
    }

    @After
    public void tearDown() {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testConstructor() {
        verify(mContext).getSystemService(eq(PowerManager.class));
        verify(mContext).getSystemService(eq(KeyguardManager.class));
        verifyNoMoreInteractions(mContext);
    }

    @Test
    public void testOnPreferredPaymentServiceChanged_nullService() {
        mHostEmulationManager.mPaymentServiceBound = true;

        mHostEmulationManager.onPreferredPaymentServiceChanged(USER_ID, null);
        mTestableLooper.processAllMessages();

        verify(mContext).getSystemService(eq(PowerManager.class));
        verify(mContext).getSystemService(eq(KeyguardManager.class));
        verify(mContext).unbindService(eq(mHostEmulationManager.getPaymentConnection()));
        verifyNoMoreInteractions(mContext);
    }

    @Test
    public void testOnPreferredPaymentServiceChanged_noPreviouslyBoundService() {
        when(mContext.bindServiceAsUser(any(), any(), anyInt(), any())).thenReturn(true);
        UserHandle userHandle = UserHandle.of(USER_ID);

        mHostEmulationManager.onPreferredPaymentServiceChanged(USER_ID, WALLET_PAYMENT_SERVICE);
        mTestableLooper.processAllMessages();

        verify(mContext).getSystemService(eq(PowerManager.class));
        verify(mContext).getSystemService(eq(KeyguardManager.class));
        verify(mContext).bindServiceAsUser(mIntentArgumentCaptor.capture(),
                mServiceConnectionArgumentCaptor.capture(),
                eq(Context.BIND_AUTO_CREATE | Context.BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS),
                eq(userHandle));
        verifyNoMoreInteractions(mContext);
        Intent intent = mIntentArgumentCaptor.getValue();
        Assert.assertEquals(intent.getAction(), HostApduService.SERVICE_INTERFACE);
        Assert.assertEquals(intent.getComponent(), WALLET_PAYMENT_SERVICE);
        Assert.assertTrue(mHostEmulationManager.mPaymentServiceBound);
        Assert.assertEquals(mHostEmulationManager.mLastBoundPaymentServiceName,
                WALLET_PAYMENT_SERVICE);
        Assert.assertEquals(mHostEmulationManager.mPaymentServiceUserId,
                USER_ID);
    }

    @Test
    public void testOnPreferredPaymentServiceChanged_previouslyBoundService() {
        when(mContext.bindServiceAsUser(any(), any(), anyInt(), any())).thenReturn(true);
        UserHandle userHandle = UserHandle.of(USER_ID);
        mHostEmulationManager.mPaymentServiceBound = true;

        mHostEmulationManager.onPreferredPaymentServiceChanged(USER_ID, WALLET_PAYMENT_SERVICE);
        mTestableLooper.processAllMessages();

        verify(mContext).getSystemService(eq(PowerManager.class));
        verify(mContext).getSystemService(eq(KeyguardManager.class));
        verify(mContext).unbindService(eq(mHostEmulationManager.getPaymentConnection()));
        verify(mContext).bindServiceAsUser(mIntentArgumentCaptor.capture(),
                mServiceConnectionArgumentCaptor.capture(),
                eq(Context.BIND_AUTO_CREATE | Context.BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS),
                eq(userHandle));
        verifyNoMoreInteractions(mContext);
        Intent intent = mIntentArgumentCaptor.getValue();
        Assert.assertEquals(intent.getAction(), HostApduService.SERVICE_INTERFACE);
        Assert.assertEquals(intent.getComponent(), WALLET_PAYMENT_SERVICE);
        Assert.assertTrue(mHostEmulationManager.mPaymentServiceBound);
        Assert.assertEquals(mHostEmulationManager.mLastBoundPaymentServiceName,
                WALLET_PAYMENT_SERVICE);
        Assert.assertEquals(mHostEmulationManager.mPaymentServiceUserId,
                USER_ID);
        Assert.assertNotNull(mServiceConnectionArgumentCaptor.getValue());
    }

    @Test
    public void testUpdatePollingLoopFilters() {
        ApduServiceInfo serviceWithFilter = Mockito.mock(ApduServiceInfo.class);
        when(serviceWithFilter.getPollingLoopFilters()).thenReturn(POLLING_LOOP_FILTER);
        when(serviceWithFilter.getPollingLoopPatternFilters()).thenReturn(List.of());
        ApduServiceInfo serviceWithPatternFilter = Mockito.mock(ApduServiceInfo.class);
        when(serviceWithPatternFilter.getPollingLoopFilters()).thenReturn(List.of());
        when(serviceWithPatternFilter.getPollingLoopPatternFilters())
                .thenReturn(POLLING_LOOP_PATTEN_FILTER);

        mHostEmulationManager.updatePollingLoopFilters(USER_ID, List.of(serviceWithFilter,
                serviceWithPatternFilter));

        Map<Integer, Map<String, List<ApduServiceInfo>>> pollingLoopFilters
                = mHostEmulationManager.getPollingLoopFilters();
        Map<Integer, Map<Pattern, List<ApduServiceInfo>>> pollingLoopPatternFilters
                = mHostEmulationManager.getPollingLoopPatternFilters();
        Assert.assertTrue(pollingLoopFilters.containsKey(USER_ID));
        Assert.assertTrue(pollingLoopPatternFilters.containsKey(USER_ID));
        Map<String, List<ApduServiceInfo>> filtersForUser = pollingLoopFilters.get(USER_ID);
        Map<Pattern, List<ApduServiceInfo>> patternFiltersForUser = pollingLoopPatternFilters
                .get(USER_ID);
        Assert.assertTrue(filtersForUser.containsKey(PL_FILTER));
        Assert.assertTrue(patternFiltersForUser.containsKey(PL_PATTERN));
        Assert.assertTrue(filtersForUser.get(PL_FILTER).contains(serviceWithFilter));
        Assert.assertTrue(patternFiltersForUser.get(PL_PATTERN).contains(serviceWithPatternFilter));
    }

    @Test
    public void testOnPollingLoopDetected_paymentServiceAlreadyBound_overlappingServices()
            throws PackageManager.NameNotFoundException, RemoteException {
        ApduServiceInfo serviceWithFilter = Mockito.mock(ApduServiceInfo.class);
        when(serviceWithFilter.getPollingLoopFilters()).thenReturn(POLLING_LOOP_FILTER);
        when(serviceWithFilter.getPollingLoopPatternFilters()).thenReturn(List.of());
        when(serviceWithFilter.getShouldAutoTransact(anyString())).thenReturn(true);
        when(serviceWithFilter.getComponent()).thenReturn(WALLET_PAYMENT_SERVICE);
        when(serviceWithFilter.getUid()).thenReturn(USER_ID);
        ApduServiceInfo overlappingServiceWithFilter = Mockito.mock(ApduServiceInfo.class);
        when(overlappingServiceWithFilter.getPollingLoopFilters()).thenReturn(POLLING_LOOP_FILTER);
        when(overlappingServiceWithFilter.getPollingLoopPatternFilters()).thenReturn(List.of());
        ApduServiceInfo serviceWithPatternFilter = Mockito.mock(ApduServiceInfo.class);
        when(serviceWithPatternFilter.getPollingLoopFilters()).thenReturn(List.of());
        when(serviceWithPatternFilter.getPollingLoopPatternFilters())
                .thenReturn(POLLING_LOOP_PATTEN_FILTER);
        when(mRegisteredAidCache.resolvePollingLoopFilterConflict(anyList()))
                .thenReturn(serviceWithFilter);
        mHostEmulationManager.updatePollingLoopFilters(USER_ID, List.of(serviceWithFilter,
                serviceWithPatternFilter, overlappingServiceWithFilter));
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mRegisteredAidCache.getPreferredService()).thenReturn(WALLET_PAYMENT_SERVICE);
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.uid = USER_ID;
        mHostEmulationManager.mPaymentServiceName = WALLET_PAYMENT_SERVICE;
        when(mPackageManager.getApplicationInfo(eq(WALLET_HOLDER_PACKAGE_NAME), eq(0)))
                .thenReturn(applicationInfo);
        String data = "filter";
        Bundle frame1 = new Bundle();
        Bundle frame2 = new Bundle();
        frame1.putInt(PollingFrame.KEY_POLLING_LOOP_TYPE, PollingFrame.POLLING_LOOP_TYPE_UNKNOWN);
        frame1.putByteArray(PollingFrame.KEY_POLLING_LOOP_DATA, data.getBytes());
        mHostEmulationManager.mActiveService = mMessanger;

        mHostEmulationManager.onPollingLoopDetected(List.of(frame1, frame2));

        verify(mContext).getSystemService(eq(PowerManager.class));
        verify(mContext).getSystemService(eq(KeyguardManager.class));
        verify(mRegisteredAidCache)
                .resolvePollingLoopFilterConflict(mServiceListArgumentCaptor.capture());
        Assert.assertTrue(mServiceListArgumentCaptor.getValue().contains(serviceWithFilter));
        Assert.assertTrue(mServiceListArgumentCaptor.getValue()
                .contains(overlappingServiceWithFilter));
        verify(mNfcAdapter).setObserveModeEnabled(eq(false));
        Assert.assertTrue(mHostEmulationManager.mEnableObserveModeAfterTransaction);
        Assert.assertTrue(frame1.containsKey(PollingFrame.KEY_POLLING_LOOP_TRIGGERED_AUTOTRANSACT));
        Assert.assertTrue(frame1.getBoolean(PollingFrame.KEY_POLLING_LOOP_TRIGGERED_AUTOTRANSACT));
        Assert.assertEquals(mHostEmulationManager.mState, HostEmulationManager.STATE_POLLING_LOOP);
        verify(mMessanger).send(mMessageArgumentCaptor.capture());
        Message message = mMessageArgumentCaptor.getValue();
        Bundle bundle = message.getData();
        Assert.assertEquals(message.what, HostApduService.MSG_POLLING_LOOP);
        Assert.assertTrue(bundle.containsKey(HostApduService.KEY_POLLING_LOOP_FRAMES_BUNDLE));
        ArrayList<Bundle> sentFrames = bundle
                .getParcelableArrayList(HostApduService.KEY_POLLING_LOOP_FRAMES_BUNDLE);
        Assert.assertTrue(sentFrames.contains(frame1));
        Assert.assertTrue(sentFrames.contains(frame2));
    }

}
