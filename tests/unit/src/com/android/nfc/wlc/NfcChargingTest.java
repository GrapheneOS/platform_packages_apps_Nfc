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

package com.android.nfc.wlc;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.nfc.DeviceHost;
import com.android.nfc.NfcService;

import com.google.common.truth.Truth;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class NfcChargingTest {
    private static String TAG = NfcChargingTest.class.getSimpleName();
    private MockitoSession mStaticMockSession;
    private NfcCharging mNfcCharging;
    private Context mContext;
    @Mock
    private DeviceHost mDeviceHost;

    @Mock
    private DeviceHost.TagEndpoint mTagEndpoint;

    @Before
    public void setUp() throws Exception {
        mStaticMockSession = ExtendedMockito.mockitoSession()
                .mockStatic(NfcService.class)
                .strictness(Strictness.LENIENT)
                .startMocking();
        MockitoAnnotations.initMocks(this);

        mContext = new ContextWrapper(
                InstrumentationRegistry.getInstrumentation().getTargetContext()) {
        };


        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> mNfcCharging = new NfcCharging(mContext, mDeviceHost));
        mNfcCharging.TagHandler = mTagEndpoint;
        Assert.assertNotNull(mNfcCharging);
    }

    @After
    public void tearDown() {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void bytesToHex_convertsByteArrayToHexString() {
        byte[] bytes = new byte[] {0x01, 0x0A, (byte) 0xFF};
        String hexString = NfcCharging.bytesToHex(bytes);
        assertThat(hexString).isEqualTo("010AFF");
    }

    @Test
    public void testResetInternalValues() {
        // Set some values to non-default
        mNfcCharging.mCnt = 10;
        mNfcCharging.WlcCtl_BatteryLevel = 50;
        mNfcCharging.WlcDeviceInfo.put(NfcCharging.BatteryLevel, 80);

        mNfcCharging.resetInternalValues();

        assertEquals(-1, mNfcCharging.mCnt);
        assertEquals(-1, mNfcCharging.WlcCtl_BatteryLevel);
        assertEquals(-1, mNfcCharging.WlcDeviceInfo.get(NfcCharging.BatteryLevel).intValue());
    }

    @Test
    public void testCheckWlcCapMsg_InvalidMessageType() {
        // Construct an NDEF message with an invalid type
        byte[] type = NfcCharging.WLCCTL; // Incorrect type
        byte[] payload = new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04,0x05 };
        NdefRecord record = new NdefRecord(NdefRecord.TNF_WELL_KNOWN, type, new byte[] {}, payload);
        NdefMessage ndefMessage = new NdefMessage(record);

        assertFalse(mNfcCharging.checkWlcCapMsg(ndefMessage));
    }

    @Test
    public void testCheckWlcCtlMsg_ValidMessage() {
        // Construct a valid WLCCTL NDEF message
        byte[] type = NfcCharging.WLCCTL;
        byte[] payload = new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05 };
        NdefRecord record = new NdefRecord(NdefRecord.TNF_WELL_KNOWN, type, new byte[] {}, payload);
        NdefMessage ndefMessage = new NdefMessage(record);

        assertTrue(mNfcCharging.checkWlcCtlMsg(ndefMessage));
        assertEquals(0, mNfcCharging.WlcCtl_ErrorFlag);
        assertEquals(0, mNfcCharging.WlcCtl_BatteryStatus);
    }

    @Test
    public void testCheckWlcCtlMsg_InvalidMessageType() {
        // Construct an NDEF message with an invalid type
        byte[] type = NfcCharging.WLCCAP; // Incorrect type
        byte[] payload = new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05 };
        NdefRecord record = new NdefRecord(NdefRecord.TNF_WELL_KNOWN, type, new byte[] {}, payload);
        NdefMessage ndefMessage = new NdefMessage(record);

        assertFalse(mNfcCharging.checkWlcCtlMsg(ndefMessage));
    }

    @Test
    public void testWLCL_Presence() {
        NdefMessage ndefMessage = mock(NdefMessage.class);
        when(mTagEndpoint.getNdef()).thenReturn(ndefMessage);
        mNfcCharging.mFirstOccurrence = false;
        NfcService nfcService = mock(NfcService.class);
        when(NfcService.getInstance()).thenReturn(nfcService);
        mNfcCharging.HandleWLCState();
        verify(mNfcCharging.mNdefMessage).getRecords();
        Assert.assertFalse(mNfcCharging.WLCL_Presence);
    }

    @Test
    public void testHandleWlcCap_ModeReq_State6() {
        NdefMessage ndefMessage = mock(NdefMessage.class);
        NdefRecord ndefRecord = mock(NdefRecord.class);
        when(ndefRecord.getType()).thenReturn(NfcCharging.WLCCAP);
        byte[] payload = {0x01, 0x02, 0x01, 0x10, 0x02, 0x01};
        when(ndefRecord.getPayload()).thenReturn(payload);
        NdefRecord[] records = {ndefRecord};
        when(ndefMessage.getRecords()).thenReturn(records);
        when(mTagEndpoint.getNdef()).thenReturn(ndefMessage);
        mNfcCharging.mFirstOccurrence = false;
        NfcService nfcService = mock(NfcService.class);
        when(NfcService.getInstance()).thenReturn(nfcService);
        mNfcCharging.HandleWLCState();
        Assert.assertEquals(1, mNfcCharging.WLCState);
    }

}

