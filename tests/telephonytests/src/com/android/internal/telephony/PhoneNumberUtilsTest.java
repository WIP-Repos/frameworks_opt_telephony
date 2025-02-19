/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.internal.telephony;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

import com.android.internal.telephony.flags.Flags;

import android.net.Uri;
import android.platform.test.flag.junit.SetFlagsRule;
import android.telephony.PhoneNumberUtils;
import android.text.SpannableStringBuilder;
import android.text.style.TtsSpan;

import androidx.test.filters.FlakyTest;
import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

public class PhoneNumberUtilsTest {

    private static final int MIN_MATCH = 7;

    private int mOldMinMatch;

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Before
    public void setUp() throws Exception {
        mOldMinMatch = PhoneNumberUtils.getMinMatchForTest();
        PhoneNumberUtils.setMinMatchForTest(MIN_MATCH);
    }

    @After
    public void tearDown() throws Exception {
        PhoneNumberUtils.setMinMatchForTest(mOldMinMatch);
    }

    @SmallTest
    @Test
    public void testExtractNetworkPortion() throws Exception {
        assertEquals(
                "+17005554141",
                PhoneNumberUtils.extractNetworkPortion("+17005554141")
        );

        assertEquals(
                "+17005554141",
                PhoneNumberUtils.extractNetworkPortion("+1 (700).555-4141")
        );

        assertEquals(
                "17005554141",
                PhoneNumberUtils.extractNetworkPortion("1 (700).555-4141")
        );

        // This may seem wrong, but it's probably ok
        assertEquals(
                "17005554141*#",
                PhoneNumberUtils.extractNetworkPortion("1 (700).555-4141*#")
        );

        assertEquals(
                "170055541NN",
                PhoneNumberUtils.extractNetworkPortion("1 (700).555-41NN")
        );

        assertEquals(
                "170055541NN",
                PhoneNumberUtils.extractNetworkPortion("1 (700).555-41NN,1234")
        );

        assertEquals(
                "170055541NN",
                PhoneNumberUtils.extractNetworkPortion("1 (700).555-41NN;1234")
        );

        // An MMI string is unperterbed, even though it contains a
        // (valid in this case) embedded +
        assertEquals(
                "**21**17005554141#",
                PhoneNumberUtils.extractNetworkPortion("**21**+17005554141#")
                //TODO this is the correct result, although the above
                //result has been returned since change 31776
                //"**21**+17005554141#"
        );

        assertEquals("", PhoneNumberUtils.extractNetworkPortion(""));

        assertEquals("", PhoneNumberUtils.extractNetworkPortion(",1234"));

        byte [] b = new byte[20];
        b[0] = (byte) 0x81; b[1] = (byte) 0x71; b[2] = (byte) 0x00; b[3] = (byte) 0x55;
        b[4] = (byte) 0x05; b[5] = (byte) 0x20; b[6] = (byte) 0xF0;
        assertEquals("17005550020",
            PhoneNumberUtils.calledPartyBCDToString(b, 0, 7));

        b[0] = (byte) 0x80; b[1] = (byte) 0x71; b[2] = (byte) 0x00; b[3] = (byte) 0x55;
        b[4] = (byte) 0x05; b[5] = (byte) 0x20; b[6] = (byte) 0xF0;
        assertEquals("17005550020",
            PhoneNumberUtils.calledPartyBCDToString(b, 0, 7));

        b[0] = (byte) 0x90; b[1] = (byte) 0x71; b[2] = (byte) 0x00; b[3] = (byte) 0x55;
        b[4] = (byte) 0x05; b[5] = (byte) 0x20; b[6] = (byte) 0xF0;
        assertEquals("+17005550020",
            PhoneNumberUtils.calledPartyBCDToString(b, 0, 7));

        b[0] = (byte) 0x91; b[1] = (byte) 0x71; b[2] = (byte) 0x00; b[3] = (byte) 0x55;
        b[4] = (byte) 0x05; b[5] = (byte) 0x20; b[6] = (byte) 0xF0;
        assertEquals("+17005550020",
                PhoneNumberUtils.calledPartyBCDToString(b, 0, 7));

        byte[] bRet = PhoneNumberUtils.networkPortionToCalledPartyBCD("+17005550020");
        assertEquals(7, bRet.length);
        for (int i = 0; i < 7; i++) {
            assertEquals(b[i], bRet[i]);
        }

        bRet = PhoneNumberUtils.networkPortionToCalledPartyBCDWithLength("+17005550020");
        assertEquals(8, bRet.length);
        assertEquals(bRet[0], 7);
        for (int i = 1; i < 8; i++) {
            assertEquals(b[i - 1], bRet[i]);
        }

        bRet = PhoneNumberUtils.networkPortionToCalledPartyBCD("7005550020");
        assertEquals("7005550020",
            PhoneNumberUtils.calledPartyBCDToString(bRet, 0, bRet.length));

        b[0] = (byte) 0x81; b[1] = (byte) 0x71; b[2] = (byte) 0x00; b[3] = (byte) 0x55;
        b[4] = (byte) 0x05; b[5] = (byte) 0x20; b[6] = (byte) 0xB0;
        assertEquals("17005550020#",
            PhoneNumberUtils.calledPartyBCDToString(b, 0, 7));

        b[0] = (byte) 0x91; b[1] = (byte) 0x71; b[2] = (byte) 0x00; b[3] = (byte) 0x55;
        b[4] = (byte) 0x05; b[5] = (byte) 0x20; b[6] = (byte) 0xB0;
        assertEquals("+17005550020#",
            PhoneNumberUtils.calledPartyBCDToString(b, 0, 7));

        b[0] = (byte) 0x81; b[1] = (byte) 0x2A; b[2] = (byte) 0xB1;
        assertEquals("*21#",
            PhoneNumberUtils.calledPartyBCDToString(b, 0, 3));

        b[0] = (byte) 0x81; b[1] = (byte) 0x2B; b[2] = (byte) 0xB1;
        assertEquals("#21#",
            PhoneNumberUtils.calledPartyBCDToString(b, 0, 3));

        b[0] = (byte) 0x91; b[1] = (byte) 0x2A; b[2] = (byte) 0xB1;
        assertEquals("*21#+",
            PhoneNumberUtils.calledPartyBCDToString(b, 0, 3));

        b[0] = (byte) 0x81; b[1] = (byte) 0xAA; b[2] = (byte) 0x12; b[3] = (byte) 0xFB;
        assertEquals("**21#",
            PhoneNumberUtils.calledPartyBCDToString(b, 0, 4));

        b[0] = (byte) 0x91; b[1] = (byte) 0xAA; b[2] = (byte) 0x12; b[3] = (byte) 0xFB;
        assertEquals("**21#+",
            PhoneNumberUtils.calledPartyBCDToString(b, 0, 4));

        b[0] = (byte) 0x81; b[1] = (byte) 0x9A; b[2] = (byte) 0xA9; b[3] = (byte) 0x71;
        b[4] = (byte) 0x00; b[5] = (byte) 0x55; b[6] = (byte) 0x05; b[7] = (byte) 0x20;
        b[8] = (byte) 0xB0;
        assertEquals("*99*17005550020#",
            PhoneNumberUtils.calledPartyBCDToString(b, 0, 9));

        b[0] = (byte) 0x91; b[1] = (byte) 0x9A; b[2] = (byte) 0xA9; b[3] = (byte) 0x71;
        b[4] = (byte) 0x00; b[5] = (byte) 0x55; b[6] = (byte) 0x05; b[7] = (byte) 0x20;
        b[8] = (byte) 0xB0;
        assertEquals("*99*+17005550020#",
            PhoneNumberUtils.calledPartyBCDToString(b, 0, 9));

        b[0] = (byte) 0x81; b[1] = (byte) 0xAA; b[2] = (byte) 0x12; b[3] = (byte) 0x1A;
        b[4] = (byte) 0x07; b[5] = (byte) 0x50; b[6] = (byte) 0x55; b[7] = (byte) 0x00;
        b[8] = (byte) 0x02; b[9] = (byte) 0xFB;
        assertEquals("**21*17005550020#",
            PhoneNumberUtils.calledPartyBCDToString(b, 0, 10));

        b[0] = (byte) 0x91; b[1] = (byte) 0xAA; b[2] = (byte) 0x12; b[3] = (byte) 0x1A;
        b[4] = (byte) 0x07; b[5] = (byte) 0x50; b[6] = (byte) 0x55; b[7] = (byte) 0x00;
        b[8] = (byte) 0x02; b[9] = (byte) 0xFB;
        assertEquals("**21*+17005550020#",
            PhoneNumberUtils.calledPartyBCDToString(b, 0, 10));

        b[0] = (byte) 0x81; b[1] = (byte) 0x2A; b[2] = (byte) 0xA1; b[3] = (byte) 0x71;
        b[4] = (byte) 0x00; b[5] = (byte) 0x55; b[6] = (byte) 0x05; b[7] = (byte) 0x20;
        b[8] = (byte) 0xF0;
        assertEquals("*21*17005550020",
            PhoneNumberUtils.calledPartyBCDToString(b, 0, 9));

        b[0] = (byte) 0x91; b[1] = (byte) 0x2A; b[2] = (byte) 0xB1; b[3] = (byte) 0x71;
        b[4] = (byte) 0x00; b[5] = (byte) 0x55; b[6] = (byte) 0x05; b[7] = (byte) 0x20;
        b[8] = (byte) 0xF0;
        assertEquals("*21#+17005550020",
            PhoneNumberUtils.calledPartyBCDToString(b, 0, 9));

        assertNull(PhoneNumberUtils.extractNetworkPortion(null));
        assertNull(PhoneNumberUtils.extractPostDialPortion(null));
        assertTrue(PhoneNumberUtils.compare(null, null));
        assertFalse(PhoneNumberUtils.compare(null, "123"));
        assertFalse(PhoneNumberUtils.compare("123", null));
        assertNull(PhoneNumberUtils.toCallerIDMinMatch(null));
        assertNull(PhoneNumberUtils.getStrippedReversed(null));
        assertNull(PhoneNumberUtils.stringFromStringAndTOA(null, 1));

        // Test for known potential bad extraction of post dial portion.
        assertEquals(";123456",
                PhoneNumberUtils.extractPostDialPortion("6281769222;123456"));
        assertEquals("6281769222", PhoneNumberUtils.extractNetworkPortion(
                "6281769222;123456"));
    }

    @SmallTest
    @Test
    public void testNonIntegerAddress() {
        byte[] b = new byte[6];
        b[0] = (byte) 0x81; b[1] = (byte) 0xba; b[2] = (byte) 0xdc; b[3] = (byte) 0xbe;
        b[4] = (byte) 0x5a; b[5] = (byte) 0xf1;
        assertEquals("*#abc#*51",
                PhoneNumberUtils.calledPartyBCDToString(
                        b, 0, 6, PhoneNumberUtils.BCD_EXTENDED_TYPE_CALLED_PARTY));
        assertEquals("*#,N;#*51",
                PhoneNumberUtils.calledPartyBCDToString(
                        b, 0, 6, PhoneNumberUtils.BCD_EXTENDED_TYPE_EF_ADN));
    }

    @SmallTest
    @Test
    public void testExtractNetworkPortionAlt() throws Exception {
        assertEquals(
                "+17005554141",
                PhoneNumberUtils.extractNetworkPortionAlt("+17005554141")
        );

        assertEquals(
                "+17005554141",
                PhoneNumberUtils.extractNetworkPortionAlt("+1 (700).555-4141")
        );

        assertEquals(
                "17005554141",
                PhoneNumberUtils.extractNetworkPortionAlt("1 (700).555-4141")
        );

        // This may seem wrong, but it's probably ok
        assertEquals(
                "17005554141*#",
                PhoneNumberUtils.extractNetworkPortionAlt("1 (700).555-4141*#")
        );

        assertEquals(
                "170055541NN",
                PhoneNumberUtils.extractNetworkPortionAlt("1 (700).555-41NN")
        );

        assertEquals(
                "170055541NN",
                PhoneNumberUtils.extractNetworkPortionAlt("1 (700).555-41NN,1234")
        );

        assertEquals(
                "170055541NN",
                PhoneNumberUtils.extractNetworkPortionAlt("1 (700).555-41NN;1234")
        );

        // An MMI string is unperterbed, even though it contains a
        // (valid in this case) embedded +
        assertEquals(
                "**21**+17005554141#",
                PhoneNumberUtils.extractNetworkPortionAlt("**21**+17005554141#")
        );

        assertEquals(
                "*31#+447966164208",
                PhoneNumberUtils.extractNetworkPortionAlt("*31#+447966164208")
        );

        assertEquals(
                "*31#+447966164208",
                PhoneNumberUtils.extractNetworkPortionAlt("*31# (+44) 79 6616 4208")
        );

        assertEquals("", PhoneNumberUtils.extractNetworkPortionAlt(""));

        assertEquals("", PhoneNumberUtils.extractNetworkPortionAlt(",1234"));

        assertNull(PhoneNumberUtils.extractNetworkPortionAlt(null));
    }

    @SmallTest
    @Test
    public void testB() throws Exception {
        assertEquals("", PhoneNumberUtils.extractPostDialPortion("+17005554141"));
        assertEquals("", PhoneNumberUtils.extractPostDialPortion("+1 (700).555-4141"));
        assertEquals("", PhoneNumberUtils.extractPostDialPortion("+1 (700).555-41NN"));
        assertEquals(",1234", PhoneNumberUtils.extractPostDialPortion("+1 (700).555-41NN,1234"));
        assertEquals(";1234", PhoneNumberUtils.extractPostDialPortion("+1 (700).555-41NN;1234"));
        assertEquals(";1234,;N",
                PhoneNumberUtils.extractPostDialPortion("+1 (700).555-41NN;1-2.34 ,;N"));
    }

    @SmallTest
    @Test
    public void testCompare() throws Exception {
        // this is odd
        assertFalse(PhoneNumberUtils.compare("", ""));

        assertTrue(PhoneNumberUtils.compare("911", "911"));
        assertFalse(PhoneNumberUtils.compare("911", "18005550911"));
        assertTrue(PhoneNumberUtils.compare("5555", "5555"));
        assertFalse(PhoneNumberUtils.compare("5555", "180055555555"));

        assertTrue(PhoneNumberUtils.compare("+17005554141", "+17005554141"));
        assertTrue(PhoneNumberUtils.compare("+17005554141", "+1 (700).555-4141"));
        assertTrue(PhoneNumberUtils.compare("+17005554141", "+1 (700).555-4141,1234"));
        assertTrue(PhoneNumberUtils.compare("+17005554141", "17005554141"));
        assertTrue(PhoneNumberUtils.compare("+17005554141", "7005554141"));
        assertTrue(PhoneNumberUtils.compare("+17005554141", "5554141"));
        assertTrue(PhoneNumberUtils.compare("17005554141", "5554141"));
        assertTrue(PhoneNumberUtils.compare("+17005554141", "01117005554141"));
        assertTrue(PhoneNumberUtils.compare("+17005554141", "0017005554141"));
        assertTrue(PhoneNumberUtils.compare("17005554141", "0017005554141"));


        assertTrue(PhoneNumberUtils.compare("+17005554141", "**31#+17005554141"));

        assertFalse(PhoneNumberUtils.compare("+1 999 7005554141", "+1 7005554141"));
        assertTrue(PhoneNumberUtils.compare("011 1 7005554141", "7005554141"));

        assertFalse(PhoneNumberUtils.compare("011 11 7005554141", "+17005554141"));

        assertFalse(PhoneNumberUtils.compare("+17005554141", "7085882300"));

        assertTrue(PhoneNumberUtils.compare("+44 207 792 3490", "0 207 792 3490"));

        assertFalse(PhoneNumberUtils.compare("+44 207 792 3490", "00 207 792 3490"));
        assertFalse(PhoneNumberUtils.compare("+44 207 792 3490", "011 207 792 3490"));

        /***** FIXME's ******/
        //
        // MMI header should be ignored
        assertFalse(PhoneNumberUtils.compare("+17005554141", "**31#17005554141"));

        // It's too bad this is false
        // +44 (0) 207 792 3490 is not a dialable number
        // but it is commonly how European phone numbers are written
        assertFalse(PhoneNumberUtils.compare("+44 207 792 3490", "+44 (0) 207 792 3490"));

        // The japanese international prefix, for example, messes us up
        // But who uses a GSM phone in Japan?
        assertFalse(PhoneNumberUtils.compare("+44 207 792 3490", "010 44 207 792 3490"));

        // The Australian one messes us up too
        assertFalse(PhoneNumberUtils.compare("+44 207 792 3490", "0011 44 207 792 3490"));

        // The Russian trunk prefix messes us up, as does current
        // Russian area codes (which bein with 0)

        assertFalse(PhoneNumberUtils.compare("+7(095)9100766", "8(095)9100766"));

        // 444 is not a valid country code, but
        // matchIntlPrefixAndCC doesnt know this
        assertTrue(PhoneNumberUtils.compare("+444 207 792 3490", "0 207 792 3490"));

        // compare SMS short code
        assertTrue(PhoneNumberUtils.compare("404-04", "40404"));
    }

    @SmallTest
    @Test
    public void testToCallerIDIndexable() throws Exception {
        assertEquals("1414555", PhoneNumberUtils.toCallerIDMinMatch("17005554141"));
        assertEquals("1414555", PhoneNumberUtils.toCallerIDMinMatch("1-700-555-4141"));
        assertEquals("1414555", PhoneNumberUtils.toCallerIDMinMatch("1-700-555-4141,1234"));
        assertEquals("1414555", PhoneNumberUtils.toCallerIDMinMatch("1-700-555-4141;1234"));

        //this seems wrong, or at least useless
        assertEquals("NN14555", PhoneNumberUtils.toCallerIDMinMatch("1-700-555-41NN"));

        //<shrug> -- these are all not useful, but not terribly wrong
        assertEquals("", PhoneNumberUtils.toCallerIDMinMatch(""));
        assertEquals("0032", PhoneNumberUtils.toCallerIDMinMatch("2300"));
        assertEquals("0032+", PhoneNumberUtils.toCallerIDMinMatch("+2300"));
        assertEquals("#130#*", PhoneNumberUtils.toCallerIDMinMatch("*#031#"));
    }

    @SmallTest
    @Test
    public void testGetIndexable() throws Exception {
        assertEquals("14145550071", PhoneNumberUtils.getStrippedReversed("1-700-555-4141"));
        assertEquals("14145550071", PhoneNumberUtils.getStrippedReversed("1-700-555-4141,1234"));
        assertEquals("14145550071", PhoneNumberUtils.getStrippedReversed("1-700-555-4141;1234"));

        //this seems wrong, or at least useless
        assertEquals("NN145550071", PhoneNumberUtils.getStrippedReversed("1-700-555-41NN"));

        //<shrug> -- these are all not useful, but not terribly wrong
        assertEquals("", PhoneNumberUtils.getStrippedReversed(""));
        assertEquals("0032", PhoneNumberUtils.getStrippedReversed("2300"));
        assertEquals("0032+", PhoneNumberUtils.getStrippedReversed("+2300"));
        assertEquals("#130#*", PhoneNumberUtils.getStrippedReversed("*#031#"));
    }

    @SmallTest
    @Test
    public void testNanpFormatting() {
        SpannableStringBuilder number = new SpannableStringBuilder();
        number.append("8005551212");
        PhoneNumberUtils.formatNanpNumber(number);
        assertEquals("800-555-1212", number.toString());

        number.clear();
        number.append("800555121");
        PhoneNumberUtils.formatNanpNumber(number);
        assertEquals("800-555-121", number.toString());

        number.clear();
        number.append("555-1212");
        PhoneNumberUtils.formatNanpNumber(number);
        assertEquals("555-1212", number.toString());

        number.clear();
        number.append("800-55512");
        PhoneNumberUtils.formatNanpNumber(number);
        assertEquals("800-555-12", number.toString());

        number.clear();
        number.append("46645");
        PhoneNumberUtils.formatNanpNumber(number);
        assertEquals("46645", number.toString());
    }

    @SmallTest
    @Test
    public void testConvertKeypadLettersToDigits() {
        assertEquals("1-800-4664-411",
                     PhoneNumberUtils.convertKeypadLettersToDigits("1-800-GOOG-411"));
        assertEquals("18004664411",
                     PhoneNumberUtils.convertKeypadLettersToDigits("1800GOOG411"));
        assertEquals("1-800-466-4411",
                     PhoneNumberUtils.convertKeypadLettersToDigits("1-800-466-4411"));
        assertEquals("18004664411",
                     PhoneNumberUtils.convertKeypadLettersToDigits("18004664411"));
        assertEquals("222-333-444-555-666-7777-888-9999",
                     PhoneNumberUtils.convertKeypadLettersToDigits(
                             "ABC-DEF-GHI-JKL-MNO-PQRS-TUV-WXYZ"));
        assertEquals("222-333-444-555-666-7777-888-9999",
                     PhoneNumberUtils.convertKeypadLettersToDigits(
                             "abc-def-ghi-jkl-mno-pqrs-tuv-wxyz"));
        assertEquals("(800) 222-3334",
                     PhoneNumberUtils.convertKeypadLettersToDigits("(800) ABC-DEFG"));
    }

    // To run this test, the device has to be registered with network
    @FlakyTest
    @Ignore
    public void testCheckAndProcessPlusCode() {
        assertEquals("0118475797000",
                PhoneNumberUtils.cdmaCheckAndProcessPlusCode("+8475797000"));
        assertEquals("18475797000",
                PhoneNumberUtils.cdmaCheckAndProcessPlusCode("+18475797000"));
        assertEquals("0111234567",
                PhoneNumberUtils.cdmaCheckAndProcessPlusCode("+1234567"));
        assertEquals("01123456700000",
                PhoneNumberUtils.cdmaCheckAndProcessPlusCode("+23456700000"));
        assertEquals("01111875767800",
                PhoneNumberUtils.cdmaCheckAndProcessPlusCode("+11875767800"));
        assertEquals("8475797000,18475231753",
                PhoneNumberUtils.cdmaCheckAndProcessPlusCode("8475797000,+18475231753"));
        assertEquals("0118475797000,18475231753",
                PhoneNumberUtils.cdmaCheckAndProcessPlusCode("+8475797000,+18475231753"));
        assertEquals("8475797000;0118469312345",
                PhoneNumberUtils.cdmaCheckAndProcessPlusCode("8475797000;+8469312345"));
        assertEquals("8475797000,0111234567",
                PhoneNumberUtils.cdmaCheckAndProcessPlusCode("8475797000,+1234567"));
        assertEquals("847597000;01111875767000",
                PhoneNumberUtils.cdmaCheckAndProcessPlusCode("847597000;+11875767000"));
        assertEquals("8475797000,,0118469312345",
                PhoneNumberUtils.cdmaCheckAndProcessPlusCode("8475797000,,+8469312345"));
        assertEquals("8475797000;,0118469312345",
                PhoneNumberUtils.cdmaCheckAndProcessPlusCode("8475797000;,+8469312345"));
        assertEquals("8475797000,;18475231753",
                PhoneNumberUtils.cdmaCheckAndProcessPlusCode("8475797000,;+18475231753"));
        assertEquals("8475797000;,01111875767000",
                PhoneNumberUtils.cdmaCheckAndProcessPlusCode("8475797000;,+11875767000"));
        assertEquals("8475797000,;01111875767000",
                PhoneNumberUtils.cdmaCheckAndProcessPlusCode("8475797000,;+11875767000"));
        assertEquals("8475797000,,,01111875767000",
                PhoneNumberUtils.cdmaCheckAndProcessPlusCode("8475797000,,,+11875767000"));
        assertEquals("8475797000;,,01111875767000",
                PhoneNumberUtils.cdmaCheckAndProcessPlusCode("8475797000;,,+11875767000"));
        assertEquals("+;,8475797000",
                PhoneNumberUtils.cdmaCheckAndProcessPlusCode("+;,8475797000"));
        assertEquals("8475797000,",
                PhoneNumberUtils.cdmaCheckAndProcessPlusCode("8475797000,"));
        assertEquals("847+579-7000",
                PhoneNumberUtils.cdmaCheckAndProcessPlusCode("847+579-7000"));
        assertEquals(",8475797000",
                PhoneNumberUtils.cdmaCheckAndProcessPlusCode(",8475797000"));
        assertEquals(";;8475797000,,",
                PhoneNumberUtils.cdmaCheckAndProcessPlusCode(";;8475797000,,"));
        assertEquals("+this+is$weird;,+",
                PhoneNumberUtils.cdmaCheckAndProcessPlusCode("+this+is$weird;,+"));
        assertEquals("",
                PhoneNumberUtils.cdmaCheckAndProcessPlusCode(""));
        assertNull(PhoneNumberUtils.cdmaCheckAndProcessPlusCode(null));
    }

    @SmallTest
    @Test
    public void testCheckAndProcessPlusCodeByNumberFormat() {
        assertEquals("18475797000",
                PhoneNumberUtils.cdmaCheckAndProcessPlusCodeByNumberFormat("+18475797000",
                PhoneNumberUtils.FORMAT_NANP,PhoneNumberUtils.FORMAT_NANP));
    }

    /**
     * Basic checks for the VoiceMail number.
     */
    @SmallTest
    @Test
    public void testWithNumberNotEqualToVoiceMail() throws Exception {
        assertFalse(PhoneNumberUtils.isVoiceMailNumber("911"));
        assertFalse(PhoneNumberUtils.isVoiceMailNumber("tel:911"));
        assertFalse(PhoneNumberUtils.isVoiceMailNumber("+18001234567"));
        assertFalse(PhoneNumberUtils.isVoiceMailNumber(""));
        assertFalse(PhoneNumberUtils.isVoiceMailNumber(null));
        // This test fails on a device without a sim card
        /*TelephonyManager mTelephonyManager =
            (TelephonyManager)getContext().getSystemService(Context.TELEPHONY_SERVICE);
        String mVoiceMailNumber = mTelephonyManager.getDefault().getVoiceMailNumber();
        assertTrue(PhoneNumberUtils.isVoiceMailNumber(mVoiceMailNumber));
        */
    }

    @SmallTest
    @Test
    public void testFormatNumberToE164() {
        // Note: ISO 3166-1 only allows upper case country codes.
        assertEquals("+16502910000", PhoneNumberUtils.formatNumberToE164("650 2910000", "US"));
        assertNull(PhoneNumberUtils.formatNumberToE164("1234567", "US"));
        assertEquals("+18004664114", PhoneNumberUtils.formatNumberToE164("800-GOOG-114", "US"));
    }

    @SmallTest
    @Test
    public void testFormatNumber() {
        assertEquals("(650) 291-0000", PhoneNumberUtils.formatNumber("650 2910000", "US"));
        assertEquals("223-4567", PhoneNumberUtils.formatNumber("2234567", "US"));
        assertEquals("011 86 10 8888 0000",
                     PhoneNumberUtils.formatNumber("011861088880000", "US"));
        assertEquals("010 8888 0000", PhoneNumberUtils.formatNumber("01088880000", "CN"));
        // formatNumber doesn't format alpha numbers, but keep them as they are.
        assertEquals("800-GOOG-114", PhoneNumberUtils.formatNumber("800-GOOG-114", "US"));
    }

    @Test
    public void testFormatNumber_lowerCase() {
        assertEquals("(650) 291-0000", PhoneNumberUtils.formatNumber("650 2910000", "us"));
        assertEquals("223-4567", PhoneNumberUtils.formatNumber("2234567", "us"));
        assertEquals("011 86 10 8888 0000",
                PhoneNumberUtils.formatNumber("011861088880000", "us"));
        assertEquals("010 8888 0000", PhoneNumberUtils.formatNumber("01088880000", "cn"));
        // formatNumber doesn't format alpha numbers, but keep them as they are.
        assertEquals("800-GOOG-114", PhoneNumberUtils.formatNumber("800-GOOG-114", "us"));
    }

    /**
     * Tests ability to format phone numbers from Japan using the international format when the
     * current country is not Japan.
     */
    @SmallTest
    @Test
    public void testFormatJapanInternational() {
        assertEquals("+81 90-6657-1180", PhoneNumberUtils.formatNumber("+819066571180", "US"));

        // Again with lower case country code
        assertEquals("+81 90-6657-1180", PhoneNumberUtils.formatNumber("+819066571180", "us"));
    }

    /**
     * Tests ability to format phone numbers from Japan using the national format when the current
     * country is Japan.
     */
    @SmallTest
    @Test
    public void testFormatJapanNational() {
        assertEquals("090-6657-0660", PhoneNumberUtils.formatNumber("09066570660", "JP"));
        assertEquals("090-6657-1180", PhoneNumberUtils.formatNumber("+819066571180", "JP"));

        // Again with lower case country code
        assertEquals("090-6657-0660", PhoneNumberUtils.formatNumber("09066570660", "jp"));
        assertEquals("090-6657-1180", PhoneNumberUtils.formatNumber("+819066571180", "jp"));


        // US number should still be internationally formatted
        assertEquals("+1 650-555-1212", PhoneNumberUtils.formatNumber("+16505551212", "JP"));
        // Again with lower case country code
        assertEquals("+1 650-555-1212", PhoneNumberUtils.formatNumber("+16505551212", "jp"));
    }

    /**
     * Test to ensure that when international calls to Singapore are being placed the country
     * code is present with and without the feature flag enabled.
     */
    @SmallTest
    @Test
    public void testFormatSingaporeInternational() {
        // Disable feature flag.
        mSetFlagsRule.disableFlags(Flags.FLAG_REMOVE_COUNTRY_CODE_FROM_LOCAL_SINGAPORE_CALLS);
        mSetFlagsRule.enableFlags(Flags.FLAG_NATIONAL_COUNTRY_CODE_FORMATTING_FOR_LOCAL_CALLS);

        // International call from a US iso
        assertEquals("+65 6521 8000", PhoneNumberUtils.formatNumber("+6565218000", "US"));

        // Lowercase country iso
        assertEquals("+65 6521 8000", PhoneNumberUtils.formatNumber("+6565218000", "us"));

        // Enable feature flag
        mSetFlagsRule.disableFlags(Flags.FLAG_NATIONAL_COUNTRY_CODE_FORMATTING_FOR_LOCAL_CALLS);
        mSetFlagsRule.enableFlags(Flags.FLAG_REMOVE_COUNTRY_CODE_FROM_LOCAL_SINGAPORE_CALLS);

        // Internal call from a US iso
        assertEquals("+65 6521 8000", PhoneNumberUtils.formatNumber("+6565218000", "US"));

        // Lowercase country iso
        assertEquals("+65 6521 8000", PhoneNumberUtils.formatNumber("+6565218000", "us"));
        mSetFlagsRule.disableFlags(Flags.FLAG_REMOVE_COUNTRY_CODE_FROM_LOCAL_SINGAPORE_CALLS);
        mSetFlagsRule.disableFlags(Flags.FLAG_NATIONAL_COUNTRY_CODE_FORMATTING_FOR_LOCAL_CALLS);
    }

    /**
     * Test to ensure that when local calls from Singaporean numbers are being placed to other
     * Singaporean numbers the country code +65 is not being shown.
     */
    @SmallTest
    @Test
    public void testFormatSingaporeNational() {
        // Disable feature flag.
        mSetFlagsRule.disableFlags(Flags.FLAG_REMOVE_COUNTRY_CODE_FROM_LOCAL_SINGAPORE_CALLS);
        mSetFlagsRule.enableFlags(Flags.FLAG_NATIONAL_COUNTRY_CODE_FORMATTING_FOR_LOCAL_CALLS);

        // Local call from a Singaporean number to a Singaporean number
        assertEquals("6521 8000", PhoneNumberUtils.formatNumber("+6565218000", "SG"));

        // Lowercase country iso.
        assertEquals("6521 8000", PhoneNumberUtils.formatNumber("+6565218000", "sg"));

        // Enable feature flag.
        mSetFlagsRule.disableFlags(Flags.FLAG_NATIONAL_COUNTRY_CODE_FORMATTING_FOR_LOCAL_CALLS);
        mSetFlagsRule.enableFlags(Flags.FLAG_REMOVE_COUNTRY_CODE_FROM_LOCAL_SINGAPORE_CALLS);

        // Local call from a Singaporean number to a Singaporean number.
        assertEquals("6521 8000", PhoneNumberUtils.formatNumber("+6565218000", "SG"));

        // Lowercase country iso.
        assertEquals("6521 8000", PhoneNumberUtils.formatNumber("+6565218000", "sg"));
        mSetFlagsRule.disableFlags(Flags.FLAG_REMOVE_COUNTRY_CODE_FROM_LOCAL_SINGAPORE_CALLS);
        mSetFlagsRule.disableFlags(Flags.FLAG_NATIONAL_COUNTRY_CODE_FORMATTING_FOR_LOCAL_CALLS);
    }

    @SmallTest
    @Test
    public void testFormatTaiwanNational() {
        // Disable feature flag.
        mSetFlagsRule.disableFlags(Flags.FLAG_NATIONAL_COUNTRY_CODE_FORMATTING_FOR_LOCAL_CALLS);
        assertEquals("+886 2 8729 6000", PhoneNumberUtils.formatNumber("+886287296000", "TW"));
        assertEquals("+886 2 8729 6000", PhoneNumberUtils.formatNumber("+886287296000", "tw"));
        assertEquals("+886 988 102 544", PhoneNumberUtils.formatNumber("+886988102544", "TW"));
        assertEquals("+886 988 102 544", PhoneNumberUtils.formatNumber("+886988102544", "tw"));

        // Enable feature flag.
        mSetFlagsRule.enableFlags(Flags.FLAG_NATIONAL_COUNTRY_CODE_FORMATTING_FOR_LOCAL_CALLS);
        assertEquals("02 8729 6000", PhoneNumberUtils.formatNumber("+886287296000", "TW"));
        assertEquals("02 8729 6000", PhoneNumberUtils.formatNumber("+886287296000", "tw"));
        assertEquals("0988 102 544", PhoneNumberUtils.formatNumber("+886988102544", "TW"));
        assertEquals("0988 102 544", PhoneNumberUtils.formatNumber("+886988102544", "tw"));
        mSetFlagsRule.disableFlags(Flags.FLAG_NATIONAL_COUNTRY_CODE_FORMATTING_FOR_LOCAL_CALLS);
    }

    @SmallTest
    @Test
    public void testFormatTaiwanInternational() {
        // Disable feature flag.
        mSetFlagsRule.disableFlags(Flags.FLAG_NATIONAL_COUNTRY_CODE_FORMATTING_FOR_LOCAL_CALLS);
        assertEquals("+886 2 8729 6000", PhoneNumberUtils.formatNumber("+886287296000", "US"));
        assertEquals("+886 2 8729 6000", PhoneNumberUtils.formatNumber("+886287296000", "us"));
        assertEquals("+886 988 102 544", PhoneNumberUtils.formatNumber("+886988102544", "US"));
        assertEquals("+886 988 102 544", PhoneNumberUtils.formatNumber("+886988102544", "us"));

        mSetFlagsRule.enableFlags(Flags.FLAG_NATIONAL_COUNTRY_CODE_FORMATTING_FOR_LOCAL_CALLS);
        assertEquals("+886 2 8729 6000", PhoneNumberUtils.formatNumber("+886287296000", "US"));
        assertEquals("+886 2 8729 6000", PhoneNumberUtils.formatNumber("+886287296000", "us"));
        assertEquals("+886 988 102 544", PhoneNumberUtils.formatNumber("+886988102544", "US"));
        assertEquals("+886 988 102 544", PhoneNumberUtils.formatNumber("+886988102544", "us"));
        mSetFlagsRule.disableFlags(Flags.FLAG_NATIONAL_COUNTRY_CODE_FORMATTING_FOR_LOCAL_CALLS);
    }

    @SmallTest
    @Test
    public void testFormatNumber_LeadingStarAndHash() {
        // Numbers with a leading '*' or '#' should be left unchanged.
        assertEquals("*650 2910000", PhoneNumberUtils.formatNumber("*650 2910000", "US"));
        assertEquals("#650 2910000", PhoneNumberUtils.formatNumber("#650 2910000", "US"));
        assertEquals("*#650 2910000", PhoneNumberUtils.formatNumber("*#650 2910000", "US"));
        assertEquals("#*650 2910000", PhoneNumberUtils.formatNumber("#*650 2910000", "US"));
        assertEquals("#650*2910000", PhoneNumberUtils.formatNumber("#650*2910000", "US"));
        assertEquals("#650*2910000", PhoneNumberUtils.formatNumber("#650*2910000", "US"));
        assertEquals("##650 2910000", PhoneNumberUtils.formatNumber("##650 2910000", "US"));
        assertEquals("**650 2910000", PhoneNumberUtils.formatNumber("**650 2910000", "US"));
    }

    // Same as testFormatNumber_LeadingStarAndHash but using lower case country code.
    @Test
    public void testFormatNumber_LeadingStarAndHash_countryCodeLowerCase() {
        // Numbers with a leading '*' or '#' should be left unchanged.
        assertEquals("*650 2910000", PhoneNumberUtils.formatNumber("*650 2910000", "us"));
        assertEquals("#650 2910000", PhoneNumberUtils.formatNumber("#650 2910000", "us"));
        assertEquals("*#650 2910000", PhoneNumberUtils.formatNumber("*#650 2910000", "us"));
        assertEquals("#*650 2910000", PhoneNumberUtils.formatNumber("#*650 2910000", "us"));
        assertEquals("#650*2910000", PhoneNumberUtils.formatNumber("#650*2910000", "us"));
        assertEquals("#650*2910000", PhoneNumberUtils.formatNumber("#650*2910000", "us"));
        assertEquals("##650 2910000", PhoneNumberUtils.formatNumber("##650 2910000", "us"));
        assertEquals("**650 2910000", PhoneNumberUtils.formatNumber("**650 2910000", "us"));
    }

    @SmallTest
    @Test
    public void testNormalizeNumber() {
        assertEquals("6502910000", PhoneNumberUtils.normalizeNumber("650 2910000"));
        assertEquals("1234567", PhoneNumberUtils.normalizeNumber("12,3#4*567"));
        assertEquals("8004664114", PhoneNumberUtils.normalizeNumber("800-GOOG-114"));
        assertEquals("+16502910000", PhoneNumberUtils.normalizeNumber("+1 650 2910000"));
    }

    @SmallTest
    @Test
    public void testFormatDailabeNumber() {
        // Using the phoneNumberE164's country code
        assertEquals("(650) 291-0000",
                PhoneNumberUtils.formatNumber("6502910000", "+16502910000", "CN"));
        // Using the default country code for a phone number containing the IDD
        assertEquals("011 86 10 8888 0000",
                PhoneNumberUtils.formatNumber("011861088880000", "+861088880000", "US"));
        assertEquals("00 86 10 8888 0000",
                PhoneNumberUtils.formatNumber("00861088880000", "+861088880000", "GB"));
        assertEquals("+86 10 8888 0000",
                PhoneNumberUtils.formatNumber("+861088880000", "+861088880000", "GB"));
        // Wrong default country, so no formatting is done
        assertEquals("011861088880000",
                PhoneNumberUtils.formatNumber("011861088880000", "+861088880000", "GB"));
        // The phoneNumberE164 is null
        assertEquals("(650) 291-0000", PhoneNumberUtils.formatNumber("6502910000", null, "US"));
        // The given number has a country code.
        assertEquals("+1 650-291-0000", PhoneNumberUtils.formatNumber("+16502910000", null, "CN"));
        // The given number was formatted.
        assertEquals("650-291-0000", PhoneNumberUtils.formatNumber("650-291-0000", null, "US"));
        // A valid Polish number should be formatted.
        assertEquals("506 128 687", PhoneNumberUtils.formatNumber("506128687", null, "PL"));
        // An invalid Polish number should be left as it is. Note Poland doesn't use '0' as a
        // national prefix; therefore, the leading '0' makes the number invalid.
        assertEquals("0506128687", PhoneNumberUtils.formatNumber("0506128687", null, "PL"));
        // Wrong default country, so no formatting is done
        assertEquals("011861088880000",
                PhoneNumberUtils.formatNumber("011861088880000", "", "GB"));
    }

    // Same as testFormatDailabeNumber but using lower case country code.
    @Test
    public void testFormatDailabeNumber_countryCodeLowerCase() {
        // Using the phoneNumberE164's country code
        assertEquals("(650) 291-0000",
                PhoneNumberUtils.formatNumber("6502910000", "+16502910000", "cn"));
        // Using the default country code for a phone number containing the IDD
        assertEquals("011 86 10 8888 0000",
                PhoneNumberUtils.formatNumber("011861088880000", "+861088880000", "us"));
        assertEquals("00 86 10 8888 0000",
                PhoneNumberUtils.formatNumber("00861088880000", "+861088880000", "gb"));
        assertEquals("+86 10 8888 0000",
                PhoneNumberUtils.formatNumber("+861088880000", "+861088880000", "gb"));
        // Wrong default country, so no formatting is done
        assertEquals("011861088880000",
                PhoneNumberUtils.formatNumber("011861088880000", "+861088880000", "gb"));
        // The phoneNumberE164 is null
        assertEquals("(650) 291-0000", PhoneNumberUtils.formatNumber("6502910000", null, "us"));
        // The given number has a country code.
        assertEquals("+1 650-291-0000", PhoneNumberUtils.formatNumber("+16502910000", null, "cn"));
        // The given number was formatted.
        assertEquals("650-291-0000", PhoneNumberUtils.formatNumber("650-291-0000", null, "us"));
        // A valid Polish number should be formatted.
        assertEquals("506 128 687", PhoneNumberUtils.formatNumber("506128687", null, "pl"));
        // An invalid Polish number should be left as it is. Note Poland doesn't use '0' as a
        // national prefix; therefore, the leading '0' makes the number invalid.
        assertEquals("0506128687", PhoneNumberUtils.formatNumber("0506128687", null, "pl"));
        // Wrong default country, so no formatting is done
        assertEquals("011861088880000",
                PhoneNumberUtils.formatNumber("011861088880000", "", "gb"));
    }

    @FlakyTest
    @Test
    @Ignore
    public void testIsEmergencyNumber() {
        // (The difference is that isEmergencyNumber() will return true
        // only if the specified number exactly matches an actual
        // emergency number

        // Tests for isEmergencyNumber():
        assertTrue(PhoneNumberUtils.isEmergencyNumber("911"));
        assertTrue(PhoneNumberUtils.isEmergencyNumber("112"));
        // The next two numbers are not valid phone numbers in the US,
        // so do not count as emergency numbers (but they *are* "potential"
        // emergency numbers; see below.)
        assertFalse(PhoneNumberUtils.isEmergencyNumber("91112345"));
        assertFalse(PhoneNumberUtils.isEmergencyNumber("11212345"));
        // A valid mobile phone number from Singapore shouldn't be classified as an emergency number
        // in Singapore, as 911 is not an emergency number there.
        assertFalse(PhoneNumberUtils.isEmergencyNumber("91121234"));
        // A valid fixed-line phone number from Brazil shouldn't be classified as an emergency number
        // in Brazil, as 112 is not an emergency number there.
        assertFalse(PhoneNumberUtils.isEmergencyNumber("1121234567"));
        // A valid local phone number from Brazil shouldn't be classified as an emergency number in
        // Brazil.
        assertFalse(PhoneNumberUtils.isEmergencyNumber("91112345"));
    }

    @SmallTest
    @Test
    public void testStripSeparators() {
        // Smoke tests which should never fail.
        assertEquals("1234567890", PhoneNumberUtils.stripSeparators("1234567890"));
        assertEquals("911", PhoneNumberUtils.stripSeparators("911"));
        assertEquals("112", PhoneNumberUtils.stripSeparators("112"));

        // Separators should be removed, while '+' or any other digits should not.
        assertEquals("+16502910000", PhoneNumberUtils.stripSeparators("+1 (650) 291-0000"));

        // WAIT, PAUSE should *not* be stripped
        assertEquals("+16502910000,300;",
                PhoneNumberUtils.stripSeparators("+1 (650) 291-0000, 300;"));
    }

    @SmallTest
    @Test
    public void testConvertAndStrip() {
        // Smoke tests which should never fail.
        assertEquals("1234567890", PhoneNumberUtils.convertAndStrip("1234567890"));
        assertEquals("911", PhoneNumberUtils.convertAndStrip("911"));
        assertEquals("112", PhoneNumberUtils.convertAndStrip("112"));

        // It should convert keypad characters into digits, and strip separators
        assertEquals("22233344455566677778889999",
                PhoneNumberUtils.convertAndStrip("ABC DEF GHI JKL MNO PQR STUV WXYZ"));

        // Test real cases.
        assertEquals("18004664411", PhoneNumberUtils.convertAndStrip("1-800-GOOG-411"));
        assertEquals("8002223334", PhoneNumberUtils.convertAndStrip("(800) ABC-DEFG"));
    }

    @SmallTest
    @Test
    public void testConvertSipUriToTelUri1() {
        // Nominal case, a tel Uri came in, so we expect one out.
        Uri source = Uri.fromParts("tel", "+16505551212", null);
        Uri expected = Uri.fromParts("tel", "+16505551212", null);
        Uri converted = PhoneNumberUtils.convertSipUriToTelUri(source);
        assertEquals(expected, converted);

        // Valid cases
        source = Uri.fromParts("sip", "+16505551212@sipinator.com", null);
        converted = PhoneNumberUtils.convertSipUriToTelUri(source);
        assertEquals(expected, converted);

        source = Uri.fromParts("sip", "+16505551212;phone-context=blah.com@host.com", null);
        converted = PhoneNumberUtils.convertSipUriToTelUri(source);
        assertEquals(expected, converted);

        source = Uri.fromParts("sip", "+16505551212@something.com;user=phone", null);
        converted = PhoneNumberUtils.convertSipUriToTelUri(source);
        assertEquals(expected, converted);
    }

    @SmallTest
    @Test
    public void testIsInternational() {
        assertFalse(PhoneNumberUtils.isInternationalNumber("", "US"));
        assertFalse(PhoneNumberUtils.isInternationalNumber(null, "US"));
        assertFalse(PhoneNumberUtils.isInternationalNumber("+16505551212", "US"));
        assertTrue(PhoneNumberUtils.isInternationalNumber("+16505551212", "UK"));
        assertTrue(PhoneNumberUtils.isInternationalNumber("+16505551212", "JP"));
        assertTrue(PhoneNumberUtils.isInternationalNumber("+86 10 8888 0000", "US"));
        assertTrue(PhoneNumberUtils.isInternationalNumber("001-541-754-3010", "DE"));
        assertFalse(PhoneNumberUtils.isInternationalNumber("001-541-754-3010", "US"));
        assertTrue(PhoneNumberUtils.isInternationalNumber("01161396694916", "US"));
        assertTrue(PhoneNumberUtils.isInternationalNumber("011-613-966-94916", "US"));
        assertFalse(PhoneNumberUtils.isInternationalNumber("011-613-966-94916", "AU"));
    }

    // Same as testIsInternational but using lower case country code.
    @Test
    public void testIsInternational_countryCodeLowerCase() {
        assertFalse(PhoneNumberUtils.isInternationalNumber("", "us"));
        assertFalse(PhoneNumberUtils.isInternationalNumber(null, "us"));
        assertFalse(PhoneNumberUtils.isInternationalNumber("+16505551212", "us"));
        assertTrue(PhoneNumberUtils.isInternationalNumber("+16505551212", "uk"));
        assertTrue(PhoneNumberUtils.isInternationalNumber("+16505551212", "jp"));
        assertTrue(PhoneNumberUtils.isInternationalNumber("+86 10 8888 0000", "us"));
        assertTrue(PhoneNumberUtils.isInternationalNumber("001-541-754-3010", "de"));
        assertFalse(PhoneNumberUtils.isInternationalNumber("001-541-754-3010", "us"));
        assertTrue(PhoneNumberUtils.isInternationalNumber("01161396694916", "us"));
        assertTrue(PhoneNumberUtils.isInternationalNumber("011-613-966-94916", "us"));
        assertFalse(PhoneNumberUtils.isInternationalNumber("011-613-966-94916", "au"));
    }

    @SmallTest
    @Test
    public void testIsUriNumber() {
        assertTrue(PhoneNumberUtils.isUriNumber("foo@google.com"));
        assertTrue(PhoneNumberUtils.isUriNumber("xyz@zzz.org"));
        assertFalse(PhoneNumberUtils.isUriNumber("+15103331245"));
        assertFalse(PhoneNumberUtils.isUriNumber("+659231235"));
    }

    @SmallTest
    @Test
    public void testGetUsernameFromUriNumber() {
        assertEquals("john", PhoneNumberUtils.getUsernameFromUriNumber("john@myorg.com"));
        assertEquals("tim_123", PhoneNumberUtils.getUsernameFromUriNumber("tim_123@zzz.org"));
        assertEquals("5103331245", PhoneNumberUtils.getUsernameFromUriNumber("5103331245"));
    }

    @SmallTest
    @Test
    public void testCreateTtsSpan() {
        checkTtsNumber("650 555 1212", "650-555-1212");
        checkTtsNumber("6505551212", "+1-650-555-1212");
        checkTtsNumber("232", "232");
        checkTtsNumber("*232", "*232");
        checkTtsNumber("*232#", "*232#");
        checkTtsNumber("*650 555 1212#", "*650-555-1212#");
    }

    private void checkTtsNumber(String expected, String sourceNumber) {
        TtsSpan ttsSpan = PhoneNumberUtils.createTtsSpan(sourceNumber);
        assertEquals(TtsSpan.TYPE_TELEPHONE, ttsSpan.getType());
        assertEquals(expected, ttsSpan.getArgs().getString(TtsSpan.ARG_NUMBER_PARTS));
    }

    @SmallTest
    @Test
    public void testWpsCallNumber() {
        // Test number without special symbols.
        assertFalse(PhoneNumberUtils.isWpsCallNumber("12345678"));

        // TTS number should not be recognized as wps.
        assertFalse(PhoneNumberUtils.isWpsCallNumber("*23212345678"));
        assertFalse(PhoneNumberUtils.isWpsCallNumber("*232#12345678"));

        // Check WPS valid numbers
        assertTrue(PhoneNumberUtils.isWpsCallNumber("*27212345678"));
        assertTrue(PhoneNumberUtils.isWpsCallNumber("*31#*27212345678"));
        assertTrue(PhoneNumberUtils.isWpsCallNumber("#31#*27212345678"));
    }
}
