/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.util.validation;

import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.locale.Res;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.btc.BaseCurrencyNetwork;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertTrue;

public class AltCoinAddressValidatorTest {
    @Before
    public void setup() {
        final BaseCurrencyNetwork baseCurrencyNetwork = BisqEnvironment.getBaseCurrencyNetwork();
        final String currencyCode = baseCurrencyNetwork.getCurrencyCode();
        Res.setBaseCurrencyCode(currencyCode);
        Res.setBaseCurrencyName(baseCurrencyNetwork.getCurrencyName());
        CurrencyUtil.setBaseCurrencyCode(currencyCode);
    }

    @Test
    public void testBTC() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("BTC");

        assertTrue(validator.validate("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhem").isValid);
        assertTrue(validator.validate("3EktnHQD7RiAE6uzMj2ZifT9YgRrkSgzQX").isValid);
        assertTrue(validator.validate("1111111111111111111114oLvT2").isValid);
        assertTrue(validator.validate("1BitcoinEaterAddressDontSendf59kuE").isValid);

        assertFalse(validator.validate("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhemqq").isValid);
        assertFalse(validator.validate("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYheO").isValid);
        assertFalse(validator.validate("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhek#").isValid);
        assertFalse(validator.validate("").isValid);
    }

    @Test
    public void testBSQ() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("BSQ");

        assertTrue(validator.validate("B17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhem").isValid);
        assertTrue(validator.validate("B3EktnHQD7RiAE6uzMj2ZifT9YgRrkSgzQX").isValid);
        assertTrue(validator.validate("B1111111111111111111114oLvT2").isValid);
        assertTrue(validator.validate("B1BitcoinEaterAddressDontSendf59kuE").isValid);

        assertFalse(validator.validate("B17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhemqq").isValid);
        assertFalse(validator.validate("B17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYheO").isValid);
        assertFalse(validator.validate("B17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhek#").isValid);
        assertFalse(validator.validate("").isValid);
    }

    @Test
    public void testLTC() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("LTC");

        assertTrue(validator.validate("Lg3PX8wRWmApFCoCMAsPF5P9dPHYQHEWKW").isValid);
        assertTrue(validator.validate("LTuoeY6RBHV3n3cfhXVVTbJbxzxnXs9ofm").isValid);
        assertTrue(validator.validate("LgfapHEPhZbRF9pMd5WPT35hFXcZS1USrW").isValid);

        assertFalse(validator.validate("1LgfapHEPhZbRF9pMd5WPT35hFXcZS1USrW").isValid);
        assertFalse(validator.validate("LgfapHEPhZbdRF9pMd5WPT35hFXcZS1USrW").isValid);
        assertFalse(validator.validate("LgfapHEPhZbRF9pMd5WPT35hFXcZS1USrW#").isValid);
        assertFalse(validator.validate("").isValid);
    }

    @Test
    public void testDOGE() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("DOGE");

        assertTrue(validator.validate("DEa7damK8MsbdCJztidBasZKVsDLJifWfE").isValid);
        assertTrue(validator.validate("DNkkfdUvkCDiywYE98MTVp9nQJTgeZAiFr").isValid);
        assertTrue(validator.validate("DDWUYQ3GfMDj8hkx8cbnAMYkTzzAunAQxg").isValid);

        assertFalse(validator.validate("1DDWUYQ3GfMDj8hkx8cbnAMYkTzzAunAQxg").isValid);
        assertFalse(validator.validate("DDWUYQ3GfMDj8hkx8cbnAMYkTzzAunAQxgs").isValid);
        assertFalse(validator.validate("DDWUYQ3GfMDj8hkx8cbnAMYkTzzAunAQxg#").isValid);
        assertFalse(validator.validate("").isValid);
    }

    @Test
    public void testDASH() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("DASH");

        assertTrue(validator.validate("XjNms118hx6dGyBqsrVMTbzMUmxDVijk7Y").isValid);
        assertTrue(validator.validate("XjNPzWfzGiY1jHUmwn9JDSVMsTs6EtZQMc").isValid);
        assertTrue(validator.validate("XnaJzoAKTNa67Fpt1tLxD5bFMcyN4tCvTT").isValid);

        assertFalse(validator.validate("1XnaJzoAKTNa67Fpt1tLxD5bFMcyN4tCvTT").isValid);
        assertFalse(validator.validate("XnaJzoAKTNa67Fpt1tLxD5bFMcyN4tCvTTd").isValid);
        assertFalse(validator.validate("XnaJzoAKTNa67Fpt1tLxD5bFMcyN4tCvTT#").isValid);
        assertFalse(validator.validate("").isValid);
    }

    @Test
    public void testETH() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("ETH");

        assertTrue(validator.validate("0x2a65Aca4D5fC5B5C859090a6c34d164135398226").isValid);
        assertTrue(validator.validate("2a65Aca4D5fC5B5C859090a6c34d164135398226").isValid);

        assertFalse(validator.validate("0x2a65Aca4D5fC5B5C859090a6c34d1641353982266").isValid);
        assertFalse(validator.validate("0x2a65Aca4D5fC5B5C859090a6c34d16413539822g").isValid);
        assertFalse(validator.validate("2a65Aca4D5fC5B5C859090a6c34d16413539822g").isValid);
        assertFalse(validator.validate("").isValid);
    }

    @Test
    public void testPIVX() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("PIVX");

        assertTrue(validator.validate("DFJku78A14HYwPSzC5PtUmda7jMr5pbD2B").isValid);
        assertTrue(validator.validate("DAeiBSH4nudXgoxS4kY6uhTPobc7ALrWDA").isValid);
        assertTrue(validator.validate("DRbnCYbuMXdKU4y8dya9EnocL47gFjErWe").isValid);
        assertTrue(validator.validate("DTPAqTryNRCE2FgsxzohTtJXfCBCDnG6Rc").isValid);

        assertFalse(validator.validate("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhemqq").isValid);
        assertFalse(validator.validate("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYheO").isValid);
        assertFalse(validator.validate("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhek#").isValid);
        assertFalse(validator.validate("").isValid);
    }

    @Test
    public void testIOP() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("IOP");

        assertTrue(validator.validate("pKbz7iRUSiUaTgh4UuwQCnc6pWZnyCGWxM").isValid);
        assertTrue(validator.validate("pAubDQFjUMaR93V4RjHYFh1YW1dzJ9YPW1").isValid);

        assertFalse(validator.validate("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhem").isValid);
        assertFalse(validator.validate("").isValid);
    }

    @Test
    public void test888() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("888");

        assertTrue(validator.validate("8TP9rh3SH6n9cSLmV22vnSNNw56LKGpLra").isValid);
        assertTrue(validator.validate("37NwrYsD1HxQW5zfLTuQcUUXGMPvQgzTSn").isValid);

        assertFalse(validator.validate("1ANNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62i").isValid);
        assertFalse(validator.validate("38NwrYsD1HxQW5zfLT0QcUUXGMPvQgzTSn").isValid);
        assertFalse(validator.validate("8tP9rh3SH6n9cSLmV22vnSNNw56LKGpLrB").isValid);
        assertFalse(validator.validate("8Zbvjr").isValid);
        assertFalse(validator.validate("").isValid);
    }

    @Test
    public void testGBYTE() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("GBYTE");

        assertTrue(validator.validate("BN7JXKXWEG4BVJ7NW6Q3Z7SMJNZJYM3G").isValid);
        assertTrue(validator.validate("XGKZODTTTRXIUA75TKONWHFDCU6634DE").isValid);

        assertFalse(validator.validate("XGKZODTGTRXIUA75TKONWHFDCU6634DE").isValid);
        assertFalse(validator.validate("XGKZODTTTRXIUA75TKONWHFDCU6634D").isValid);
        assertFalse(validator.validate("XGKZODTTTRXIUA75TKONWHFDCU6634DZ").isValid);
        assertFalse(validator.validate("").isValid);
    }

    @Test
    public void testNXT() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("NXT");

        assertTrue(validator.validate("NXT-JM2U-U4AE-G7WF-3NP9F").isValid);
        assertTrue(validator.validate("NXT-6UNJ-UMFM-Z525-4S24M").isValid);
        assertTrue(validator.validate("NXT-2223-2222-KB8Y-22222").isValid);

        assertFalse(validator.validate("").isValid);
        assertFalse(validator.validate("abcde").isValid);
        assertFalse(validator.validate("NXT-").isValid);
        assertFalse(validator.validate("NXT-JM2U-U4AE-G7WF-3ND9F").isValid);
        assertFalse(validator.validate("NXT-JM2U-U4AE-G7WF-3Np9F").isValid);
        assertFalse(validator.validate("NXT-2222-2222-2222-22222").isValid);
    }

    // Added at 0.6.0
    @Test
    public void testDCT() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("DCT");

        assertTrue(validator.validate("ud6910c2790bda53bcc53cb131f8fa3bf").isValid);
        assertTrue(validator.validate("decent-account123").isValid);
        assertTrue(validator.validate("decent.acc-123").isValid);

        assertFalse(validator.validate("my.acc123").isValid);
        assertFalse(validator.validate("123decent").isValid);
        assertFalse(validator.validate("decent_acc").isValid);
        assertFalse(validator.validate("dEcent").isValid);
        assertFalse(validator.validate("dct1").isValid);
        assertFalse(validator.validate("decent-").isValid);
        assertFalse(validator.validate("").isValid);
    }

    @Test
    public void testPNC() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("PNC");

        assertTrue(validator.validate("3AB1qXhaU3hK5oAPQfwzN3QkM8LxAgL8vB").isValid);
        assertTrue(validator.validate("PD57PGdk69yioZ6FD3zFNzVUeJhMf6Kti4").isValid);

        assertFalse(validator.validate("3AB1qXhaU3hK5oAPQfwzN3QkM8LxAgL8v").isValid);
        assertFalse(validator.validate("PD57PGdk69yioZ6FD3zFNzVUeJhMf6Kti42").isValid);
        assertFalse(validator.validate("PD57PGdk69yioZ6FD3zFNzVUeJhMMMKti4").isValid);
        assertFalse(validator.validate("PD57PG").isValid);
        assertFalse(validator.validate("").isValid);
    }

    @Test
    public void testWAC() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("WAC");

        assertTrue(validator.validate("WfEnB3VGrBqW7uamJMymymEwxMBYQKELKY").isValid);
        assertTrue(validator.validate("WTLWtNN5iJJQyTeMfZMMrfrDvdGZrYGP5U").isValid);
        assertTrue(validator.validate("WemK3MgwREsEaF4vdtYLxmMqAXp49C2LYQ").isValid);
        assertTrue(validator.validate("WZggcFY5cJdAxx9unBW5CVPAH8VLTxZ6Ym").isValid);

        assertFalse(validator.validate("").isValid);
        assertFalse(validator.validate("abcde").isValid);
        assertFalse(validator.validate("mWvZ7nZAUzpRMFp2Bfjxz27Va47nUfB79E").isValid);
        assertFalse(validator.validate("WemK3MgwREsE23fgsadtYLxmMqAX9C2LYQ").isValid);
    }

    @Test
    public void testZEN() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("ZEN");

        assertTrue(validator.validate("znk62Ey7ptTyHgYLaLDTEwhLF6uN1DXTBfa").isValid);
        assertTrue(validator.validate("znTqzi5rTXf6KJnX5tLaC5CMGHfeWJwy1c7").isValid);
        assertTrue(validator.validate("t1V9h2P9n4sYg629Xn4jVDPySJJxGmPb1HK").isValid);  // Random address from ZCash blockchain
        assertTrue(validator.validate("t3Ut4KUq2ZSMTPNE67pBU5LqYCi2q36KpXQ").isValid);  // Random address from ZCash blockchain

        assertFalse(validator.validate("zcKffBrza1cirFY47aKvXiV411NZMscf7zUY5bD1HwvkoQvKHgpxLYUHtMCLqBAeif1VwHmMjrMAKNrdCknCVqCzRNizHUq").isValid);
        assertFalse(validator.validate("AFTqzi5rTXf6KJnX5tLaC5CMGHfeWJwy1c7").isValid);
        assertFalse(validator.validate("zig-zag").isValid);
        assertFalse(validator.validate("0123456789").isValid);
        assertFalse(validator.validate("").isValid);
    }

    @Test
    public void testELLA() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("ELLA");

        assertTrue(validator.validate("0x65767ec6d4d3d18a200842352485cdc37cbf3a21").isValid);
        assertTrue(validator.validate("65767ec6d4d3d18a200842352485cdc37cbf3a21").isValid);

        assertFalse(validator.validate("0x65767ec6d4d3d18a200842352485cdc37cbf3a216").isValid);
        assertFalse(validator.validate("0x65767ec6d4d3d18a200842352485cdc37cbf3a2g").isValid);
        assertFalse(validator.validate("65767ec6d4d3d18a200842352485cdc37cbf3a2g").isValid);
        assertFalse(validator.validate("").isValid);
    }

    @Test
    public void testXCN() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("XCN");

        assertTrue(validator.validate("CT49DTNo5itqYoAD6XTGyTKbe8z5nGY2D5").isValid);
        assertTrue(validator.validate("CGTta3M4t3yXu8uRgkKvaWd2d8DQvDPnpL").isValid);
        assertTrue(validator.validate("Cco3zGiEJMyz3wrndEr6wg5cm1oUAbBoR2").isValid);
        assertTrue(validator.validate("CPzmjGCDEdQuRffmbpkrYQtSiUAm4oZJgt").isValid);

        assertFalse(validator.validate("CT49DTNo5itqYoAD6XTGyTKbe8z5nGY2D4").isValid);
        assertFalse(validator.validate("CGTta3M4t3yXu8uRgkKvaWd2d8DQvDPnpl").isValid);
        assertFalse(validator.validate("Cco3zGiEJMyz3wrndEr6wg5cm1oUAbBoR1").isValid);
        assertFalse(validator.validate("CPzmjGCDEdQuRffmbpkrYQtSiUAm4oZJgT").isValid);
        assertFalse(validator.validate("CT49DTNo5itqYoAD6XTGyTKbe8z5nGY2Da").isValid);
        assertFalse(validator.validate("").isValid);
        assertFalse(validator.validate("asdasd").isValid);
        assertFalse(validator.validate("cT49DTNo5itqYoAD6XTGyTKbe8z5nGY2Da").isValid);
        assertFalse(validator.validate("No5itqYoAD6XTGyTKbe8z5nGY2Da").isValid);
    }

    @Test
    public void testTRC() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("TRC");

        assertTrue(validator.validate("1Bys8pZaKo4GTWcpArMg92cBgYqij8mKXt").isValid);
        assertTrue(validator.validate("12Ycuof6g5GRyWy56eQ3NvJpwAM8z9pb4g").isValid);
        assertTrue(validator.validate("1DEBTTVCn1h9bQS9scVP6UjoSsjbtJBvXF").isValid);
        assertTrue(validator.validate("18s142HdWDfDQXYBpuyMvsU3KHwryLxnCr").isValid);

        assertFalse(validator.validate("18s142HdWDfDQXYBpyuMvsU3KHwryLxnCr").isValid);
        assertFalse(validator.validate("18s142HdWDfDQXYBpuyMvsU3KHwryLxnC").isValid);
        assertFalse(validator.validate("8s142HdWDfDQXYBpuyMvsU3KHwryLxnCr").isValid);
        assertFalse(validator.validate("18s142HdWDfDQXYBuyMvsU3KHwryLxnCr").isValid);
        assertFalse(validator.validate("").isValid);
        assertFalse(validator.validate("1asdasd").isValid);
        assertFalse(validator.validate("asdasd").isValid);
    }

    @Test
    public void testINXT() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("INXT");

        assertTrue(validator.validate("0x2a65Aca4D5fC5B5C859090a6c34d164135398226").isValid);
        assertTrue(validator.validate("2a65Aca4D5fC5B5C859090a6c34d164135398226").isValid);

        assertFalse(validator.validate("0x2a65Aca4D5fC5B5C859090a6c34d1641353982266").isValid);
        assertFalse(validator.validate("0x2a65Aca4D5fC5B5C859090a6c34d16413539822g").isValid);
        assertFalse(validator.validate("2a65Aca4D5fC5B5C859090a6c34d16413539822g").isValid);
        assertFalse(validator.validate("").isValid);
    }

    @Test
    public void testPART() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("PART");
        assertTrue(validator.validate("PZdYWHgyhuG7NHVCzEkkx3dcLKurTpvmo6").isValid);
        assertTrue(validator.validate("RJAPhgckEgRGVPZa9WoGSWW24spskSfLTQ").isValid);
        assertTrue(validator.validate("PaqMewoBY4vufTkKeSy91su3CNwviGg4EK").isValid);
        assertTrue(validator.validate("PpWHwrkUKRYvbZbTic57YZ1zjmsV9X9Wu7").isValid);

        assertFalse(validator.validate("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhemqq").isValid);
        assertFalse(validator.validate("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYheO").isValid);
        assertFalse(validator.validate("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhek#").isValid);
        assertFalse(validator.validate("").isValid);
    }

    // Added 0.6.1
    @Test
    public void testMDC() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("MDC");

        assertTrue(validator.validate("LiL8HvSJRDgnS5BXATWe5MzWYicCbRvisr").isValid);
        assertTrue(validator.validate("LX8B2K8eHSxWjfS55sdMoQj2PCPmvRRyLv").isValid);

        assertFalse(validator.validate("mzBc4XEFSdzCDcTxAgf6EZXgsZWpztRhe").isValid);
        assertFalse(validator.validate("miCVC7QcY917Cz427qTBEUrvBzRapHrupc").isValid);
        assertFalse(validator.validate("12KYrjTdVGjFMtaxERSk3gphreJ5US8aUP").isValid);
        assertFalse(validator.validate("").isValid);
    }

    @Test
    public void testBCH() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("BCH");

        assertTrue(validator.validate("1HQQgsvLTgN9xD9hNmAgAreakzVzQUSLSH").isValid);
        assertTrue(validator.validate("1MEbUJ5v5MdDEqFJGz4SZp58KkaLdmXZ85").isValid);
        assertTrue(validator.validate("34dvotXMg5Gxc37TBVV2e5GUAfCFu7Ms4g").isValid);

        assertFalse(validator.validate("21HQQgsvLTgN9xD9hNmAgAreakzVzQUSLSHa").isValid);
        assertFalse(validator.validate("1HQQgsvLTgN9xD9hNmAgAreakzVzQUSLSHs").isValid);
        assertFalse(validator.validate("1HQQgsvLTgN9xD9hNmAgAreakzVzQUSLSH#").isValid);
        assertFalse(validator.validate("").isValid);
    }

    @Test
    public void testBCHC() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("BCHC");

        assertTrue(validator.validate("1HQQgsvLTgN9xD9hNmAgAreakzVzQUSLSH").isValid);
        assertTrue(validator.validate("1MEbUJ5v5MdDEqFJGz4SZp58KkaLdmXZ85").isValid);
        assertTrue(validator.validate("34dvotXMg5Gxc37TBVV2e5GUAfCFu7Ms4g").isValid);

        assertFalse(validator.validate("21HQQgsvLTgN9xD9hNmAgAreakzVzQUSLSHa").isValid);
        assertFalse(validator.validate("1HQQgsvLTgN9xD9hNmAgAreakzVzQUSLSHs").isValid);
        assertFalse(validator.validate("1HQQgsvLTgN9xD9hNmAgAreakzVzQUSLSH#").isValid);
        assertFalse(validator.validate("").isValid);
    }

    @Test
    public void testBTG() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("BTG");

        assertTrue(validator.validate("AehvQ57Fp168uY592LCUYBbyNEpiRAPufb").isValid);
        assertTrue(validator.validate("GWaSW6PHfQKBv8EXV3xiqGG2zxKZh4XYNu").isValid);
        assertTrue(validator.validate("GLpT8yG2kwPMdMfgwekG6tEAa91PSmN4ZC").isValid);

        assertFalse(validator.validate("GVTPWDVJgLxo5ZYSPXPDxE4s7LE5cLRwCc1").isValid);
        assertFalse(validator.validate("1GVTPWDVJgLxo5ZYSPXPDxE4s7LE5cLRwCc").isValid);
        assertFalse(validator.validate("1HQQgsvLTgN9xD9hNmAgAreakzVzQUSLSH#").isValid);
        assertFalse(validator.validate("").isValid);
    }

    // Added 0.6.2
	@Test
    public void testCAGE() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("CAGE");

        assertTrue(validator.validate("Db97PgfdBDhXk8DmrDhrUPyydTCELn8YSb").isValid);
        assertTrue(validator.validate("DYV4h7MTsQ91jqzbg94GFAAUbgdK7RZmpG").isValid);
        assertTrue(validator.validate("Db5y8iKtZ24DqgYpP2G8685vTWEvk3WACC").isValid);
        assertTrue(validator.validate("DjiQbPuBLJcVYUtzYMuFuzDwDYwb9mVhaK").isValid);

        assertFalse(validator.validate("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhemqq").isValid);
        assertFalse(validator.validate("0x2a65Aca4D5fC5B5C859090a6c34d1641353982266").isValid);
		assertFalse(validator.validate("DNkkfdUvkCDiywYE98MTVp9nQJTgeZAiFr").isValid);
        assertFalse(validator.validate("").isValid);
    }

    @Test
    public void testCRED() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("CRED");

        assertTrue(validator.validate("0x65767ec6d4d3d18a200842352485cdc37cbf3a21").isValid);
        assertTrue(validator.validate("65767ec6d4d3d18a200842352485cdc37cbf3a21").isValid);

        assertFalse(validator.validate("0x65767ec6d4d3d18a200842352485cdc37cbf3a216").isValid);
        assertFalse(validator.validate("0x65767ec6d4d3d18a200842352485cdc37cbf3a2g").isValid);
        assertFalse(validator.validate("65767ec6d4d3d18a200842352485cdc37cbf3a2g").isValid);
        assertFalse(validator.validate("").isValid);
    }

    @Test
    public void testXSPEC() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("XSPEC");

        assertTrue(validator.validate("SUZRHjTLSCr581qLsGqMqBD5f3oW2JHckn").isValid);
        assertTrue(validator.validate("SZ4S1oFfUa4a9s9Kg8bNRywucHiDZmcUuz").isValid);
        assertTrue(validator.validate("SdyjGEmgroK2vxBhkHE1MBUVRbUWpRAdVG").isValid);

        assertFalse(validator.validate("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhemqq").isValid);
        assertFalse(validator.validate("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYheO").isValid);
        assertFalse(validator.validate("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhek#").isValid);
        assertFalse(validator.validate("").isValid);
    }
}
