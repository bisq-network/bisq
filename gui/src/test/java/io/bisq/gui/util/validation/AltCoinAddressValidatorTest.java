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

import org.junit.Test;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertTrue;

public class AltCoinAddressValidatorTest {

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
    }

    @Test
    public void testIOP() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("IOP");

        assertTrue(validator.validate("pKbz7iRUSiUaTgh4UuwQCnc6pWZnyCGWxM").isValid);
        assertTrue(validator.validate("pAubDQFjUMaR93V4RjHYFh1YW1dzJ9YPW1").isValid);

        assertFalse(validator.validate("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhem").isValid);
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
    public void test888() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("888");

        assertTrue(validator.validate("8TP9rh3SH6n9cSLmV22vnSNNw56LKGpLra").isValid);
        assertTrue(validator.validate("37NwrYsD1HxQW5zfLTuQcUUXGMPvQgzTSn").isValid);

        assertFalse(validator.validate("1ANNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62i").isValid);
        assertFalse(validator.validate("38NwrYsD1HxQW5zfLT0QcUUXGMPvQgzTSn").isValid);
        assertFalse(validator.validate("8tP9rh3SH6n9cSLmV22vnSNNw56LKGpLrB").isValid);
        assertFalse(validator.validate("8Zbvjr").isValid);
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
}
