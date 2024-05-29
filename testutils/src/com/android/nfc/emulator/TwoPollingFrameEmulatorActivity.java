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
package com.android.nfc.emulator;


import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.cardemulation.PollingFrame;
import android.os.Bundle;
import android.util.Log;

import com.android.nfc.service.PollingLoopService;
import com.android.nfc.service.PollingLoopService2;

import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.List;
import java.util.Queue;

public class TwoPollingFrameEmulatorActivity extends BaseEmulatorActivity {
    private static final String TAG = "PollingLoopActivity";

    private static final String SERVICE_1_FRAME = "aaaaaaaa";
    private static final String SERVICE_2_FRAME = "bbbbbbbb";

    // Number of loops to track in queue
    public static final String NFC_TECH_KEY = "NFC_TECH";
    public static final String NFC_CUSTOM_FRAME_KEY = "NFC_CUSTOM_FRAME";
    public static final String SEEN_CORRECT_POLLING_LOOP_ACTION =
            PACKAGE_NAME + ".SEEN_CORRECT_POLLING_LOOP_ACTION";
    private boolean mSentBroadcast = false;

    // Keeps track of last mCapacity PollingFrames
    private Queue<PollingFrame> mQueue = new ArrayDeque<PollingFrame>();

    private int mCapacity;

    private int mLoopSize;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupServices(PollingLoopService.COMPONENT, PollingLoopService2.COMPONENT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(PollingLoopService.POLLING_FRAME_ACTION);
        registerReceiver(mFieldStateReceiver, filter, RECEIVER_EXPORTED);
        ComponentName serviceName1 =
                new ComponentName(this.getApplicationContext(), PollingLoopService.class);
        mCardEmulation.setShouldDefaultToObserveModeForService(serviceName1, true);
        mCardEmulation.registerPollingLoopFilterForService(serviceName1, SERVICE_1_FRAME, false);

        ComponentName serviceName2 =
                new ComponentName(this.getApplicationContext(), PollingLoopService2.class);
        mCardEmulation.setShouldDefaultToObserveModeForService(serviceName2, true);
        mCardEmulation.registerPollingLoopFilterForService(serviceName2, SERVICE_2_FRAME, false);

        mCardEmulation.setPreferredService(this, serviceName1);
        waitForService();
        waitForObserveModeEnabled(true);
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.e(TAG, "onPause");
        unregisterReceiver(mFieldStateReceiver);
        mCardEmulation.unsetPreferredService(this);
    }

    @Override
    public ComponentName getPreferredServiceComponent() {
        return PollingLoopService.COMPONENT;
    }

    void processPollingFrames(List<PollingFrame> frames, String serviceName) {
        Log.d(TAG, "processPollingFrames of size " + frames.size());
        for (PollingFrame frame : frames) {
            processPollingFrame(frame, serviceName);
        }
    }

    void processPollingFrame(PollingFrame frame, String serviceName) {
        Log.d(TAG, "processPollingFrame: " + (char) (frame.getType()) + " service: " + serviceName);

        switch (frame.getType()) {
            case PollingFrame.POLLING_LOOP_TYPE_UNKNOWN:
                Log.e(TAG, "got custom frame: " + HexFormat.of().formatHex(frame.getData()));
                byte[] data = frame.getData();
                boolean matchesService1 = serviceName.equals(PollingLoopService.class.getName()) &&
                        SERVICE_1_FRAME.equals(HexFormat.of().formatHex(data));
                boolean matchesService2 = serviceName.equals(PollingLoopService2.class.getName()) &&
                        SERVICE_2_FRAME.equals(HexFormat.of().formatHex(data));
                if (matchesService1 || matchesService2) {
                    Intent intent = new Intent(SEEN_CORRECT_POLLING_LOOP_ACTION);
                    sendBroadcast(intent);
                    Log.d(TAG, "Correct custom polling frame seen. Sent broadcast");
                }
                break;
        }
    }

    final BroadcastReceiver mFieldStateReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(PollingLoopService.POLLING_FRAME_ACTION)) {
                        processPollingFrames(
                                intent.getParcelableArrayListExtra(
                                        PollingLoopService.POLLING_FRAME_EXTRA,
                                        PollingFrame.class),
                                intent.getStringExtra(PollingLoopService.SERVICE_NAME_EXTRA));
                    }
                }
            };
}
