/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.internal.telephony.imsphone;

import static junit.framework.Assert.fail;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.AsyncResult;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.flags.FeatureFlags;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executor;

/**
 * Unit tests for the {@link ImsPhoneMmiCodeTest}.
 */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class ImsPhoneMmiCodeTest extends TelephonyTest {
    private static final String TEST_DIAL_STRING_SERVICE_CODE = "*67911";
    private static final String TEST_DIAL_STRING_NO_SERVICE_CODE = "*767911";
    private static final String TEST_DIAL_STRING_NON_EMERGENCY_NUMBER = "11976";
    private static final String TEST_DIAL_NUMBER = "123456789";
    private static final String TEST_DIAL_STRING_CFNRY_ACTIVE_CODE = "**61*" + TEST_DIAL_NUMBER;
    private static final String TEST_DIAL_STRING_CFNRY_DEACTIVE_CODE = "##61#";
    private ImsPhoneMmiCode mImsPhoneMmiCode;
    private ImsPhone mImsPhoneUT;

    // Mocked classes
    private ServiceState mServiceState;
    private FeatureFlags mFeatureFlags;

    private final Executor mExecutor = Runnable::run;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mServiceState = mock(ServiceState.class);
        mFeatureFlags = mock(FeatureFlags.class);

        doReturn(mExecutor).when(mContext).getMainExecutor();
        doReturn(mPhone).when(mPhone).getDefaultPhone();
        doReturn(mServiceState).when(mPhone).getServiceState();
        doReturn(false).when(mServiceState).getVoiceRoaming();
        doReturn(false).when(mPhone).supportsConversionOfCdmaCallerIdMmiCodesWhileRoaming();
        mImsPhoneUT = new ImsPhone(mContext, mNotifier, mPhone, mFeatureFlags);
        setCarrierSupportsCallerIdVerticalServiceCodesCarrierConfig();
    }

    @After
    public void tearDown() throws Exception {
        mImsPhoneMmiCode = null;
        mImsPhoneUT = null;
        super.tearDown();
    }

    @Test
    public void testIsTemporaryModeCLIRFromCarrierConfig() {
        // Test *67911 is treated as temporary mode CLIR
        doReturn(true).when(mTelephonyManager).isEmergencyNumber(TEST_DIAL_STRING_SERVICE_CODE);
        mImsPhoneMmiCode = ImsPhoneMmiCode.newFromDialString(TEST_DIAL_STRING_SERVICE_CODE,
                mImsPhoneUT, mFeatureFlags);
        assertTrue(mImsPhoneMmiCode.isTemporaryModeCLIR());
    }

    @Test
    public void testIsTemporaryModeCLIRForNonServiceCode() {
        // Test if prefix isn't *67 or *82
        doReturn(true).when(mTelephonyManager).isEmergencyNumber(TEST_DIAL_STRING_NO_SERVICE_CODE);
        mImsPhoneMmiCode = ImsPhoneMmiCode.newFromDialString(TEST_DIAL_STRING_NO_SERVICE_CODE,
                mImsPhoneUT, mFeatureFlags);
        assertTrue(mImsPhoneMmiCode == null);
    }

    @Test
    public void testIsTemporaryModeCLIRForNonEmergencyNumber() {
        // Test if dialing string isn't an emergency number
        mImsPhoneMmiCode = ImsPhoneMmiCode.newFromDialString(TEST_DIAL_STRING_NON_EMERGENCY_NUMBER,
                mImsPhoneUT, mFeatureFlags);
        assertTrue(mImsPhoneMmiCode == null);
    }

    @Test
    public void testNoCrashOnEmptyMessage() {
        ImsPhoneMmiCode mmi = ImsPhoneMmiCode.newNetworkInitiatedUssd(null, true, mImsPhoneUT,
                mFeatureFlags);
        try {
            mmi.onUssdFinishedError();
        } catch (Exception e) {
            fail("Shouldn't crash!!!");
        }
    }

    private void setCarrierSupportsCallerIdVerticalServiceCodesCarrierConfig() {
        final PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager
                .KEY_CARRIER_SUPPORTS_CALLER_ID_VERTICAL_SERVICE_CODES_BOOL, true);
        doReturn(bundle).when(mCarrierConfigManager).getConfigForSubId(anyInt());
    }

    /**
     * Ensure that when an operation is not supported that the correct message is returned.
     */
    @Test
    @SmallTest
    public void testOperationNotSupported() {
        mImsPhoneMmiCode = ImsPhoneMmiCode.newNetworkInitiatedUssd(null, true, mImsPhoneUT,
                mFeatureFlags);

        // Emulate request not supported from the network.
        AsyncResult ar = new AsyncResult(null, null,
                new CommandException(CommandException.Error.REQUEST_NOT_SUPPORTED));
        mImsPhoneMmiCode.getMmiErrorMessage(ar);
        verify(mContext.getResources()).getText(
                eq(com.android.internal.R.string.mmiErrorNotSupported));
    }

    @Test
    @SmallTest
    public void testActivationCfnrWithCfnry() throws Exception {
        doNothing().when(mImsPhone).setCallForwardingOption(
                anyInt(), anyInt(), any(), anyInt(), anyInt(), any());

        doReturn(true).when(mFeatureFlags).useCarrierConfigForCfnryTimeViaMmi();

        int carrierConfigTime = 40;
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(CarrierConfigManager
                .KEY_NO_REPLY_TIMER_FOR_CFNRY_SEC_INT, carrierConfigTime);
        doReturn(bundle).when(mCarrierConfigManager).getConfigForSubId(anyInt());

        int mmiTime = 30;
        String withoutTimeCode = TEST_DIAL_STRING_CFNRY_ACTIVE_CODE + "#";
        String withTimeCode = TEST_DIAL_STRING_CFNRY_ACTIVE_CODE + "**" + mmiTime + "#";

        mImsPhoneMmiCode = ImsPhoneMmiCode.newFromDialString(withoutTimeCode, mImsPhoneUT,
                mFeatureFlags);
        // For verification, replace the internal object of target with mock
        replaceInstance(ImsPhoneMmiCode.class, "mPhone", mImsPhoneMmiCode, mImsPhone);

        mImsPhoneMmiCode.processCode();
        verify(mImsPhone, times(1)).setCallForwardingOption(
                eq(CommandsInterface.CF_ACTION_REGISTRATION),
                anyInt(),
                eq(TEST_DIAL_NUMBER),
                anyInt(),
                eq(carrierConfigTime),
                any());

        mImsPhoneMmiCode = ImsPhoneMmiCode.newFromDialString(withTimeCode, mImsPhoneUT,
                mFeatureFlags);
        // For verification, replace the internal object of target with mock
        replaceInstance(ImsPhoneMmiCode.class, "mPhone", mImsPhoneMmiCode, mImsPhone);

        mImsPhoneMmiCode.processCode();
        verify(mImsPhone, times(1)).setCallForwardingOption(
                eq(CommandsInterface.CF_ACTION_REGISTRATION),
                anyInt(),
                eq(TEST_DIAL_NUMBER),
                anyInt(),
                eq(mmiTime),
                any());
    }

    @Test
    @SmallTest
    public void testDeactivationCfnrWithCfnry() throws Exception {
        doNothing().when(mImsPhone).setCallForwardingOption(
                anyInt(), anyInt(), any(), anyInt(), anyInt(), any());

        doReturn(true).when(mFeatureFlags).useCarrierConfigForCfnryTimeViaMmi();

        int carrierConfigTime = 40;
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(CarrierConfigManager
                .KEY_NO_REPLY_TIMER_FOR_CFNRY_SEC_INT, carrierConfigTime);
        doReturn(bundle).when(mCarrierConfigManager).getConfigForSubId(anyInt());

        mImsPhoneMmiCode = ImsPhoneMmiCode.newFromDialString(TEST_DIAL_STRING_CFNRY_DEACTIVE_CODE,
                mImsPhoneUT, mFeatureFlags);
        // For verification, replace the internal object of target with mock
        replaceInstance(ImsPhoneMmiCode.class, "mPhone", mImsPhoneMmiCode, mImsPhone);

        mImsPhoneMmiCode.processCode();
        verify(mImsPhone, times(1)).setCallForwardingOption(
                eq(CommandsInterface.CF_ACTION_ERASURE),
                anyInt(),
                eq(null),
                anyInt(),
                eq(carrierConfigTime),
                any());
    }
}
