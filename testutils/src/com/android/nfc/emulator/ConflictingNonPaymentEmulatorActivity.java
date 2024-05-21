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

import android.content.ComponentName;
import android.os.Bundle;
import android.util.Log;

import com.android.nfc.service.TransportService1;
import com.android.nfc.service.TransportService2;

public class ConflictingNonPaymentEmulatorActivity extends BaseEmulatorActivity {
    protected static final String TAG = "ConflictingNonPayment";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setupServices(TransportService1.COMPONENT, TransportService2.COMPONENT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }

    @Override
    protected void onApduSequenceComplete(ComponentName component, long duration) {
        if (component.equals(TransportService2.COMPONENT)) {
            setTestPassed();
        }
    }

    @Override
    public ComponentName getPreferredServiceComponent(){
        return TransportService2.COMPONENT;
    }
}
