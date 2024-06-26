/*
 * Copyright 2024 The Android Open Source Project
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

package android.nfc.test;

import static android.nfc.test.TestUtils.createAndResumeActivity;
import static android.nfc.test.TestUtils.supportsHardware;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.CardEmulation;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsEnabled;
import androidx.test.InstrumentationRegistry;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ObserveModeTests {
  private Context mContext;
  final int STRESS_TEST_DURATION = 10000;

  @Before
  public void setUp() throws NoSuchFieldException, RemoteException {
    assumeTrue(supportsHardware());
    mContext = InstrumentationRegistry.getContext();
  }

  @Test(timeout=20000)
  @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_OBSERVE_MODE)
  public void testObserveModeStress() throws InterruptedException {
    Assert.assertNotNull(mContext);
    final NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
    Assert.assertNotNull(adapter);
    assumeTrue(adapter.isObserveModeSupported());
    CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
    try {
      Activity activity = createAndResumeActivity();
      cardEmulation.setShouldDefaultToObserveModeForService(
          new ComponentName(mContext, CustomHostApduService.class), true);
      Assert.assertTrue(
          cardEmulation.setPreferredService(
              activity, new ComponentName(mContext, CustomHostApduService.class)));
      TestUtils.ensurePreferredService(CustomHostApduService.class, mContext);
      long stop = System.currentTimeMillis() + STRESS_TEST_DURATION;
      Thread thread1 =
          new Thread() {
            @Override
            public void run() {
              while (System.currentTimeMillis() < stop) {
                Assert.assertTrue(adapter.setObserveModeEnabled(true));
              }
            }
          };

      Thread thread2 =
          new Thread() {
            @Override
            public void run() {
              while (System.currentTimeMillis() < stop) {
                Assert.assertTrue(adapter.setObserveModeEnabled(false));
              }
            }
          };
      thread1.start();
      thread2.start();
      thread1.join();
      thread2.join();

    } finally {
      cardEmulation.setShouldDefaultToObserveModeForService(
          new ComponentName(mContext, CustomHostApduService.class), false);
    }
  }

  void ensurePreferredService(Class serviceClass) {
    TestUtils.ensurePreferredService(serviceClass, mContext);
  }
}
