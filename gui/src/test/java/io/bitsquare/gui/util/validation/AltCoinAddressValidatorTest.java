/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.util.validation;

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

    // TODO test not successful
   /* @Test
    public void testXTO() {
        AltCoinAddressValidator validator = new AltCoinAddressValidator();
        validator.setCurrencyCode("XTO");

        assertTrue(validator.validate("TddV2k4mbm6tkUYbv3zoEoiB3kDUFBdaRT").isValid);
        assertTrue(validator.validate("2USHRW8UcSAAnHxWnPe4yX2BV5K3dbWss9").isValid);

        assertFalse(validator.validate("TddV2k4mbm6tkUYbv3zoEoiB3kDUFBdaRTT").isValid);
        assertFalse(validator.validate("TddV2k4mbm6tkUYbv3zoEoiB3kDUFBdaTo").isValid);
        assertFalse(validator.validate("TddV2k4mbm6tkUYbv3zoEoiB3kDUFBdaRt#").isValid);
    }*/
    
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
}
