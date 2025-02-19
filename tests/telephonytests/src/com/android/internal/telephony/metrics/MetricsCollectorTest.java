/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.internal.telephony.metrics;

import static com.android.internal.telephony.TelephonyStatsLog.CARRIER_ROAMING_SATELLITE_CONTROLLER_STATS;
import static com.android.internal.telephony.TelephonyStatsLog.CARRIER_ROAMING_SATELLITE_SESSION;
import static com.android.internal.telephony.TelephonyStatsLog.CELLULAR_DATA_SERVICE_SWITCH;
import static com.android.internal.telephony.TelephonyStatsLog.CELLULAR_SERVICE_STATE;
import static com.android.internal.telephony.TelephonyStatsLog.OUTGOING_SHORT_CODE_SMS;
import static com.android.internal.telephony.TelephonyStatsLog.SATELLITE_CONFIG_UPDATER;
import static com.android.internal.telephony.TelephonyStatsLog.SATELLITE_ENTITLEMENT;
import static com.android.internal.telephony.TelephonyStatsLog.SIM_SLOT_STATE;
import static com.android.internal.telephony.TelephonyStatsLog.SUPPORTED_RADIO_ACCESS_FAMILY;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_RAT_USAGE;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION;
import static com.android.internal.telephony.util.TelephonyUtils.IS_DEBUGGABLE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.StatsManager;
import android.telephony.TelephonyManager;
import android.util.StatsEvent;

import androidx.test.filters.SmallTest;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.flags.FeatureFlags;
import com.android.internal.telephony.nano.PersistAtomsProto.CarrierRoamingSatelliteControllerStats;
import com.android.internal.telephony.nano.PersistAtomsProto.CarrierRoamingSatelliteSession;
import com.android.internal.telephony.nano.PersistAtomsProto.CellularDataServiceSwitch;
import com.android.internal.telephony.nano.PersistAtomsProto.CellularServiceState;
import com.android.internal.telephony.nano.PersistAtomsProto.OutgoingShortCodeSms;
import com.android.internal.telephony.nano.PersistAtomsProto.SatelliteConfigUpdater;
import com.android.internal.telephony.nano.PersistAtomsProto.SatelliteEntitlement;
import com.android.internal.telephony.nano.PersistAtomsProto.VoiceCallRatUsage;
import com.android.internal.telephony.nano.PersistAtomsProto.VoiceCallSession;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.uicc.UiccPort;
import com.android.internal.telephony.uicc.UiccProfile;
import com.android.internal.telephony.uicc.UiccSlot;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class MetricsCollectorTest extends TelephonyTest {
    private static final StatsManager.PullAtomMetadata POLICY_PULL_DAILY =
            new StatsManager.PullAtomMetadata.Builder()
                    .setCoolDownMillis(24L * 3600L * 1000L)
                    .build();
    private static final long MIN_COOLDOWN_MILLIS = 23L * 3600L * 1000L;
    private static final long POWER_CORRELATED_MIN_COOLDOWN_MILLIS =
            IS_DEBUGGABLE ? 4L *  60L * 1000L : 5L * 3600L * 1000L;
    private static final long MIN_CALLS_PER_BUCKET = 5L;

    // NOTE: these fields are currently 32-bit internally and padded to 64-bit by TelephonyManager
    private static final int SUPPORTED_RAF_1 =
            (int) TelephonyManager.NETWORK_TYPE_BITMASK_GSM
                    | (int) TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                    | (int) TelephonyManager.NETWORK_TYPE_BITMASK_NR;
    private static final int SUPPORTED_RAF_2 =
            (int) TelephonyManager.NETWORK_TYPE_BITMASK_GSM
                    | (int) TelephonyManager.NETWORK_TYPE_BITMASK_1xRTT
                    | (int) TelephonyManager.NETWORK_TYPE_BITMASK_UMTS;
    private static final int SUPPORTED_RAF_BOTH = SUPPORTED_RAF_1 | SUPPORTED_RAF_2;

    // TODO: if we want to check puller registration by mocking StatsManager, we will have to enable
    // inline mocking since the StatsManager class is final

    // b/153195691: we cannot verify the contents of StatsEvent as its getters are marked with @hide

    // Mocked classes
    private Phone mSecondPhone;
    private UiccSlot mPhysicalSlot;
    private UiccSlot mEsimSlot;
    private UiccCard mActiveCard;
    private UiccPort mActivePort;
    private ServiceStateStats mServiceStateStats;
    private VonrHelper mVonrHelper;
    private FeatureFlags mFeatureFlags;

    private MetricsCollector mMetricsCollector;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mSecondPhone = mock(Phone.class);
        mPhysicalSlot = mock(UiccSlot.class);
        mEsimSlot = mock(UiccSlot.class);
        mActiveCard = mock(UiccCard.class);
        mActivePort = mock(UiccPort.class);
        mServiceStateStats = mock(ServiceStateStats.class);
        mVonrHelper = mock(VonrHelper.class);
        mFeatureFlags = mock(FeatureFlags.class);
        mMetricsCollector =
                new MetricsCollector(mContext, mPersistAtomsStorage,
                        mDeviceStateHelper, mVonrHelper, mDefaultNetworkMonitor, mFeatureFlags);
        doReturn(mSST).when(mSecondPhone).getServiceStateTracker();
        doReturn(mServiceStateStats).when(mSST).getServiceStateStats();
    }

    @After
    public void tearDown() throws Exception {
        mMetricsCollector = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void onPullAtom_simSlotState_bothSimPresent() {
        // these have been tested extensively in SimSlotStateTest, here we verify atom generation
        UiccProfile activeProfile = mock(UiccProfile.class);
        doReturn(4).when(activeProfile).getNumApplications();
        doReturn(activeProfile).when(mActivePort).getUiccProfile();
        doReturn(true).when(mPhysicalSlot).isActive();
        doReturn(CardState.CARDSTATE_PRESENT).when(mPhysicalSlot).getCardState();
        doReturn(false).when(mPhysicalSlot).isEuicc();
        doReturn(true).when(mEsimSlot).isActive();
        doReturn(CardState.CARDSTATE_PRESENT).when(mEsimSlot).getCardState();
        doReturn(true).when(mEsimSlot).isEuicc();
        doReturn(mActiveCard).when(mEsimSlot).getUiccCard();
        doReturn(new UiccPort[] {mActivePort}).when(mActiveCard).getUiccPortList();
        doReturn(new UiccSlot[] {mPhysicalSlot, mEsimSlot}).when(mUiccController).getUiccSlots();
        doReturn(mPhysicalSlot).when(mUiccController).getUiccSlot(eq(0));
        doReturn(mEsimSlot).when(mUiccController).getUiccSlot(eq(1));
        StatsEvent expectedAtom =
                StatsEvent.newBuilder()
                        .setAtomId(SIM_SLOT_STATE)
                        .writeInt(2)
                        .writeInt(2)
                        .writeInt(1)
                        .build();
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(SIM_SLOT_STATE, actualAtoms);

        assertThat(actualAtoms).hasSize(1);
        assertThat(result).isEqualTo(StatsManager.PULL_SUCCESS);
        // TODO(b/153196254): verify atom contents
    }

    @Test
    @SmallTest
    public void onPullAtom_simSlotState_beforeUiccControllerReady() throws Exception {
        // there is a slight chance that MetricsCollector gets pulled after registration while
        // PhoneFactory havne't made UiccController yet, RuntimeException will be thrown
        replaceInstance(UiccController.class, "mInstance", mUiccController, null);
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(SIM_SLOT_STATE, actualAtoms);

        assertThat(actualAtoms).hasSize(0);
        assertThat(result).isEqualTo(StatsManager.PULL_SKIP);
    }

    @Test
    @SmallTest
    public void onPullAtom_supportedRadioAccessFamily_singlePhone() {
        doReturn(SUPPORTED_RAF_1).when(mPhone).getRadioAccessFamily();
        StatsEvent expectedAtom =
                StatsEvent.newBuilder()
                        .setAtomId(SUPPORTED_RADIO_ACCESS_FAMILY)
                        .writeLong(SUPPORTED_RAF_1)
                        .build();
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(SUPPORTED_RADIO_ACCESS_FAMILY, actualAtoms);

        assertThat(actualAtoms).hasSize(1);
        assertThat(result).isEqualTo(StatsManager.PULL_SUCCESS);
        // TODO(b/153196254): verify atom contents
    }

    @Test
    @SmallTest
    public void onPullAtom_supportedRadioAccessFamily_dualPhones() {
        doReturn(SUPPORTED_RAF_1).when(mPhone).getRadioAccessFamily();
        doReturn(SUPPORTED_RAF_2).when(mSecondPhone).getRadioAccessFamily();
        mPhones = new Phone[] {mPhone, mSecondPhone};
        StatsEvent expectedAtom =
                StatsEvent.newBuilder()
                        .setAtomId(SUPPORTED_RADIO_ACCESS_FAMILY)
                        .writeLong(SUPPORTED_RAF_BOTH)
                        .build();
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(SUPPORTED_RADIO_ACCESS_FAMILY, actualAtoms);

        assertThat(actualAtoms).hasSize(1);
        assertThat(result).isEqualTo(StatsManager.PULL_SUCCESS);
        // TODO(b/153196254): verify atom contents
    }

    @Test
    @SmallTest
    public void onPullAtom_supportedRadioAccessFamily_dualPhonesWithUnknownRaf() {
        doReturn(SUPPORTED_RAF_1).when(mPhone).getRadioAccessFamily();
        doReturn((int) TelephonyManager.NETWORK_TYPE_BITMASK_UNKNOWN)
                .when(mSecondPhone)
                .getRadioAccessFamily();
        mPhones = new Phone[] {mPhone, mSecondPhone};
        StatsEvent expectedAtom =
                StatsEvent.newBuilder()
                        .setAtomId(SUPPORTED_RADIO_ACCESS_FAMILY)
                        .writeLong(SUPPORTED_RAF_1)
                        .build();
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(SUPPORTED_RADIO_ACCESS_FAMILY, actualAtoms);

        assertThat(actualAtoms).hasSize(1);
        assertThat(result).isEqualTo(StatsManager.PULL_SUCCESS);
        // TODO(b/153196254): verify atom contents
    }

    @Test
    @SmallTest
    public void onPullAtom_supportedRadioAccessFamily_beforePhoneReady() throws Exception {
        replaceInstance(PhoneFactory.class, "sMadeDefaults", true, false);
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(SUPPORTED_RADIO_ACCESS_FAMILY, actualAtoms);

        assertThat(actualAtoms).hasSize(0);
        assertThat(result).isEqualTo(StatsManager.PULL_SKIP);
    }

    @Test
    @SmallTest
    public void onPullAtom_voiceCallRatUsage_empty() throws Exception {
        doReturn(new VoiceCallRatUsage[0])
                .when(mPersistAtomsStorage)
                .getVoiceCallRatUsages(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(VOICE_CALL_RAT_USAGE, actualAtoms);

        assertThat(actualAtoms).hasSize(0);
        assertThat(result).isEqualTo(StatsManager.PULL_SUCCESS);
    }

    @Test
    @SmallTest
    public void onPullAtom_voiceCallRatUsage_tooFrequent() throws Exception {
        doReturn(null).when(mPersistAtomsStorage).getVoiceCallRatUsages(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(VOICE_CALL_RAT_USAGE, actualAtoms);

        assertThat(actualAtoms).hasSize(0);
        assertThat(result).isEqualTo(StatsManager.PULL_SKIP);
        verify(mPersistAtomsStorage, times(1)).getVoiceCallRatUsages(eq(MIN_COOLDOWN_MILLIS));
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onPullAtom_voiceCallRatUsage_bucketWithTooFewCalls() throws Exception {
        VoiceCallRatUsage usage1 = new VoiceCallRatUsage();
        usage1.callCount = MIN_CALLS_PER_BUCKET;
        VoiceCallRatUsage usage2 = new VoiceCallRatUsage();
        usage2.callCount = MIN_CALLS_PER_BUCKET - 1L;
        doReturn(new VoiceCallRatUsage[] {usage1, usage1, usage1, usage2})
                .when(mPersistAtomsStorage)
                .getVoiceCallRatUsages(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(VOICE_CALL_RAT_USAGE, actualAtoms);

        assertThat(actualAtoms).hasSize(3); // usage 2 should be dropped
        assertThat(result).isEqualTo(StatsManager.PULL_SUCCESS);
        // TODO(b/153196254): verify atom contents
    }

    @Test
    @SmallTest
    public void onPullAtom_voiceCallSession_empty() throws Exception {
        doReturn(new VoiceCallSession[0])
                .when(mPersistAtomsStorage)
                .getVoiceCallSessions(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(VOICE_CALL_SESSION, actualAtoms);

        assertThat(actualAtoms).hasSize(0);
        assertThat(result).isEqualTo(StatsManager.PULL_SUCCESS);
    }

    @Test
    @SmallTest
    public void onPullAtom_voiceCallSession_tooFrequent() throws Exception {
        doReturn(null).when(mPersistAtomsStorage).getVoiceCallSessions(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(VOICE_CALL_SESSION, actualAtoms);

        assertThat(actualAtoms).hasSize(0);
        assertThat(result).isEqualTo(StatsManager.PULL_SKIP);
        verify(mPersistAtomsStorage, times(1)).getVoiceCallSessions(eq(MIN_COOLDOWN_MILLIS));
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onPullAtom_voiceCallSession_multipleCalls() throws Exception {
        VoiceCallSession call = new VoiceCallSession();
        doReturn(new VoiceCallSession[] {call, call, call, call})
                .when(mPersistAtomsStorage)
                .getVoiceCallSessions(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(VOICE_CALL_SESSION, actualAtoms);

        assertThat(actualAtoms).hasSize(4);
        assertThat(result).isEqualTo(StatsManager.PULL_SUCCESS);
        // TODO(b/153196254): verify atom contents
    }

    @Test
    @SmallTest
    public void onPullAtom_cellularDataServiceSwitch_empty() throws Exception {
        doReturn(new CellularDataServiceSwitch[0])
                .when(mPersistAtomsStorage)
                .getCellularDataServiceSwitches(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(CELLULAR_DATA_SERVICE_SWITCH, actualAtoms);

        assertThat(actualAtoms).hasSize(0);
        assertThat(result).isEqualTo(StatsManager.PULL_SUCCESS);
        // TODO(b/153196254): verify atom contents
    }

    @Test
    @SmallTest
    public void onPullAtom_cellularDataServiceSwitch_tooFrequent() throws Exception {
        doReturn(null).when(mPersistAtomsStorage).getCellularDataServiceSwitches(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(CELLULAR_DATA_SERVICE_SWITCH, actualAtoms);

        assertThat(actualAtoms).hasSize(0);
        assertThat(result).isEqualTo(StatsManager.PULL_SKIP);
        verify(mPersistAtomsStorage, times(1))
                .getCellularDataServiceSwitches(eq(MIN_COOLDOWN_MILLIS));
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onPullAtom_cellularDataServiceSwitch_multipleSwitches() throws Exception {
        CellularDataServiceSwitch serviceSwitch = new CellularDataServiceSwitch();
        doReturn(new CellularDataServiceSwitch[] {serviceSwitch, serviceSwitch, serviceSwitch})
                .when(mPersistAtomsStorage)
                .getCellularDataServiceSwitches(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(CELLULAR_DATA_SERVICE_SWITCH, actualAtoms);

        assertThat(actualAtoms).hasSize(3);
        assertThat(result).isEqualTo(StatsManager.PULL_SUCCESS);
        // TODO(b/153196254): verify atom contents
    }

    @Test
    @SmallTest
    public void onPullAtom_cellularServiceState_empty() throws Exception {
        doReturn(new CellularServiceState[0])
                .when(mPersistAtomsStorage)
                .getCellularServiceStates(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(CELLULAR_SERVICE_STATE, actualAtoms);

        assertThat(actualAtoms).hasSize(0);
        assertThat(result).isEqualTo(StatsManager.PULL_SUCCESS);
        // TODO(b/153196254): verify atom contents
    }

    @Test
    @SmallTest
    public void onPullAtom_cellularServiceState_tooFrequent() throws Exception {
        doReturn(null).when(mPersistAtomsStorage).getCellularServiceStates(anyLong());
        mContextFixture.putIntResource(
                com.android.internal.R.integer.config_metrics_pull_cooldown_millis,
                (int) POWER_CORRELATED_MIN_COOLDOWN_MILLIS);
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(CELLULAR_SERVICE_STATE, actualAtoms);

        assertThat(actualAtoms).hasSize(0);
        assertThat(result).isEqualTo(StatsManager.PULL_SKIP);
        verify(mPersistAtomsStorage, times(1)).getCellularServiceStates(
                eq(POWER_CORRELATED_MIN_COOLDOWN_MILLIS));
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onPullAtom_cellularServiceState_multipleStates() throws Exception {
        CellularServiceState state = new CellularServiceState();
        doReturn(new CellularServiceState[] {state, state, state})
                .when(mPersistAtomsStorage)
                .getCellularServiceStates(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(CELLULAR_SERVICE_STATE, actualAtoms);

        assertThat(actualAtoms).hasSize(3);
        assertThat(result).isEqualTo(StatsManager.PULL_SUCCESS);
        // TODO(b/153196254): verify atom contents
    }

    @Test
    public void onPullAtom_outgoingShortCodeSms_empty() {
        doReturn(new OutgoingShortCodeSms[0]).when(mPersistAtomsStorage)
                .getOutgoingShortCodeSms(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(OUTGOING_SHORT_CODE_SMS, actualAtoms);

        assertThat(actualAtoms).hasSize(0);
        assertThat(result).isEqualTo(StatsManager.PULL_SUCCESS);
    }

    @Test
    public void onPullAtom_outgoingShortCodeSms_tooFrequent() {
        doReturn(null).when(mPersistAtomsStorage).getOutgoingShortCodeSms(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(OUTGOING_SHORT_CODE_SMS, actualAtoms);

        assertThat(actualAtoms).hasSize(0);
        assertThat(result).isEqualTo(StatsManager.PULL_SKIP);
        verify(mPersistAtomsStorage, times(1))
                .getOutgoingShortCodeSms(eq(MIN_COOLDOWN_MILLIS));
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    public void onPullAtom_outgoingShortCodeSms_multipleSms() {
        OutgoingShortCodeSms outgoingShortCodeSms = new OutgoingShortCodeSms();
        doReturn(new OutgoingShortCodeSms[] {outgoingShortCodeSms, outgoingShortCodeSms,
                outgoingShortCodeSms, outgoingShortCodeSms})
                .when(mPersistAtomsStorage)
                .getOutgoingShortCodeSms(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(OUTGOING_SHORT_CODE_SMS, actualAtoms);

        assertThat(actualAtoms).hasSize(4);
        assertThat(result).isEqualTo(StatsManager.PULL_SUCCESS);
    }

    @Test
    public void onPullAtom_carrierRoamingSatelliteSession_empty() {
        doReturn(new CarrierRoamingSatelliteSession[0]).when(mPersistAtomsStorage)
                .getCarrierRoamingSatelliteSessionStats(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(CARRIER_ROAMING_SATELLITE_SESSION, actualAtoms);

        assertThat(actualAtoms).hasSize(0);
        assertThat(result).isEqualTo(StatsManager.PULL_SUCCESS);
    }

    @Test
    public void onPullAtom_carrierRoamingSatelliteSession_tooFrequent() {
        doReturn(null).when(mPersistAtomsStorage)
                .getCarrierRoamingSatelliteSessionStats(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(CARRIER_ROAMING_SATELLITE_SESSION, actualAtoms);

        assertThat(actualAtoms).hasSize(0);
        assertThat(result).isEqualTo(StatsManager.PULL_SKIP);
        verify(mPersistAtomsStorage, times(1))
                .getCarrierRoamingSatelliteSessionStats(eq(MIN_COOLDOWN_MILLIS));
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    public void onPullAtom_carrierRoamingSatelliteSession_multipleAtoms() {
        CarrierRoamingSatelliteSession carrierRoamingSatelliteSession =
                new CarrierRoamingSatelliteSession();
        doReturn(new CarrierRoamingSatelliteSession[] {carrierRoamingSatelliteSession,
                carrierRoamingSatelliteSession, carrierRoamingSatelliteSession,
                carrierRoamingSatelliteSession})
                .when(mPersistAtomsStorage)
                .getCarrierRoamingSatelliteSessionStats(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(CARRIER_ROAMING_SATELLITE_SESSION, actualAtoms);

        assertThat(actualAtoms).hasSize(4);
        assertThat(result).isEqualTo(StatsManager.PULL_SUCCESS);
    }

    @Test
    public void onPullAtom_carrierRoamingSatelliteControllerStats_empty() {
        doReturn(new CarrierRoamingSatelliteControllerStats[0]).when(mPersistAtomsStorage)
                .getCarrierRoamingSatelliteControllerStats(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(CARRIER_ROAMING_SATELLITE_CONTROLLER_STATS,
                actualAtoms);

        assertThat(actualAtoms).hasSize(0);
        assertThat(result).isEqualTo(StatsManager.PULL_SUCCESS);
    }

    @Test
    public void onPullAtom_carrierRoamingSatelliteControllerStats_multipleAtoms() {
        CarrierRoamingSatelliteControllerStats carrierRoamingSatelliteControllerStats =
                new CarrierRoamingSatelliteControllerStats();
        doReturn(new CarrierRoamingSatelliteControllerStats[] {
                carrierRoamingSatelliteControllerStats})
                .when(mPersistAtomsStorage)
                .getCarrierRoamingSatelliteControllerStats(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(CARRIER_ROAMING_SATELLITE_CONTROLLER_STATS,
                actualAtoms);

        assertThat(actualAtoms).hasSize(1);
        assertThat(result).isEqualTo(StatsManager.PULL_SUCCESS);
    }

    @Test
    public void onPullAtom_carrierRoamingSatelliteControllerStats_tooFrequent() {
        doReturn(null).when(mPersistAtomsStorage)
                .getCarrierRoamingSatelliteControllerStats(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(CARRIER_ROAMING_SATELLITE_CONTROLLER_STATS,
                actualAtoms);

        assertThat(actualAtoms).hasSize(0);
        assertThat(result).isEqualTo(StatsManager.PULL_SKIP);
        verify(mPersistAtomsStorage, times(1))
                .getCarrierRoamingSatelliteControllerStats(eq(MIN_COOLDOWN_MILLIS));
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    public void onPullAtom_satelliteEntitlement_empty() {
        doReturn(new SatelliteEntitlement[0]).when(mPersistAtomsStorage)
                .getSatelliteEntitlementStats(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(SATELLITE_ENTITLEMENT, actualAtoms);

        assertThat(actualAtoms).hasSize(0);
        assertThat(result).isEqualTo(StatsManager.PULL_SUCCESS);
    }

    @Test
    public void onPullAtom_satelliteEntitlement_tooFrequent() {
        doReturn(null).when(mPersistAtomsStorage).getSatelliteEntitlementStats(
                anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(SATELLITE_ENTITLEMENT, actualAtoms);

        assertThat(actualAtoms).hasSize(0);
        assertThat(result).isEqualTo(StatsManager.PULL_SKIP);
        verify(mPersistAtomsStorage, times(1))
                .getSatelliteEntitlementStats(eq(MIN_COOLDOWN_MILLIS));
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    public void onPullAtom_satelliteEntitlement_multipleAtoms() {
        SatelliteEntitlement satelliteEntitlement = new SatelliteEntitlement();
        doReturn(new SatelliteEntitlement[] {satelliteEntitlement, satelliteEntitlement,
                satelliteEntitlement, satelliteEntitlement})
                .when(mPersistAtomsStorage)
                .getSatelliteEntitlementStats(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(SATELLITE_ENTITLEMENT, actualAtoms);

        assertThat(actualAtoms).hasSize(4);
        assertThat(result).isEqualTo(StatsManager.PULL_SUCCESS);
    }

    @Test
    public void onPullAtom_satelliteConfigUpdater_empty() {
        doReturn(new SatelliteConfigUpdater[0]).when(mPersistAtomsStorage)
                .getSatelliteConfigUpdaterStats(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(SATELLITE_CONFIG_UPDATER, actualAtoms);

        assertThat(actualAtoms).hasSize(0);
        assertThat(result).isEqualTo(StatsManager.PULL_SUCCESS);
    }

    @Test
    public void onPullAtom_satelliteConfigUpdater_tooFrequent() {
        doReturn(null).when(mPersistAtomsStorage).getSatelliteConfigUpdaterStats(
                anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(SATELLITE_CONFIG_UPDATER, actualAtoms);

        assertThat(actualAtoms).hasSize(0);
        assertThat(result).isEqualTo(StatsManager.PULL_SKIP);
        verify(mPersistAtomsStorage, times(1))
                .getSatelliteConfigUpdaterStats(eq(MIN_COOLDOWN_MILLIS));
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    public void onPullAtom_satelliteConfigUpdater_multipleAtoms() {
        SatelliteConfigUpdater satelliteConfigUpdater = new SatelliteConfigUpdater();
        doReturn(new SatelliteConfigUpdater[] {satelliteConfigUpdater, satelliteConfigUpdater,
                satelliteConfigUpdater, satelliteConfigUpdater})
                .when(mPersistAtomsStorage)
                .getSatelliteConfigUpdaterStats(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMetricsCollector.onPullAtom(SATELLITE_CONFIG_UPDATER, actualAtoms);

        assertThat(actualAtoms).hasSize(4);
        assertThat(result).isEqualTo(StatsManager.PULL_SUCCESS);
    }
}
