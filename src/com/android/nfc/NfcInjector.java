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

import android.annotation.NonNull;
import android.content.ApexEnvironment;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserHandle;
import android.util.AtomicFile;

import com.android.nfc.proto.NfcEventProto;

import java.io.File;
import java.time.LocalDateTime;

/**
 * To be used for dependency injection (especially helps mocking static dependencies).
 * TODO: Migrate more classes to injector to resolve circular dependencies in the NFC stack.
 */
public class NfcInjector {
    private static final String TAG = "NfcInjector";
    private static final String APEX_NAME = "com.android.nfcservices";
    private static final String NFC_DATA_DIR = "/data/nfc";
    private static final String EVENT_LOG_FILE_NAME = "event_log.binpb";

    private final Context mContext;
    private final NfcEventLog mNfcEventLog;

    private static NfcInjector sInstance;

    public static NfcInjector getInstance() {
        if (sInstance == null) throw new IllegalStateException("Nfc injector instance null");
        return sInstance;
    }

    public NfcInjector(@NonNull Context context) {
        if (sInstance != null) throw new IllegalStateException("Nfc injector instance not null");

        mContext = context;

        // Create UWB event log thread.
        HandlerThread eventLogThread = new HandlerThread("NfcEventLog");
        eventLogThread.start();
        mNfcEventLog = new NfcEventLog(mContext, this, eventLogThread.getLooper(),
                new AtomicFile(new File(NFC_DATA_DIR, EVENT_LOG_FILE_NAME)));
        sInstance = this;
    }

    public NfcEventLog getNfcEventLog() {
        return mNfcEventLog;
    }

    /**
     * NFC apex DE folder.
     */
    public static File getDeviceProtectedDataDir() {
        return ApexEnvironment.getApexEnvironment(APEX_NAME)
                .getDeviceProtectedDataDir();
    }

    public LocalDateTime getLocalDateTime() {
        return LocalDateTime.now();
    }

}
