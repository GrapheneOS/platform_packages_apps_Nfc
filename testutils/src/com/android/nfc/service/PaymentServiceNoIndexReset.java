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
package com.android.nfc.service;

import android.content.ComponentName;
import android.util.Log;

/**
 * Modifies the behavior of onDeactivated in PaymentService1 to not reset the APDU index when
 * onDeactivated is called
 */
public class PaymentServiceNoIndexReset extends PaymentService1 {

    private static final String TAG = "PaymentService3";
    public static final ComponentName COMPONENT =
            new ComponentName("com.android.nfc.emulator",
                    PaymentServiceNoIndexReset.class.getName());

    @Override
    public ComponentName getComponent() {
        return PaymentServiceNoIndexReset.COMPONENT;
    }

    /** Called when service is deactivated - don't reset the apduIndex. */
    @Override
    public void onDeactivated(int arg0) {
        Log.d(TAG, "onDeactivated");
        mState = STATE_IDLE;
    }
}
