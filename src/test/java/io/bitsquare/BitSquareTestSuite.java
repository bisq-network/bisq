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

package io.bitsquare;

import io.bitsquare.btc.RestrictionsTest;
import io.bitsquare.gui.main.trade.createoffer.CreateOfferPMTest;
import io.bitsquare.gui.util.BSFormatterTest;
import io.bitsquare.gui.util.validation.BtcValidatorTest;
import io.bitsquare.gui.util.validation.FiatValidatorTest;
import io.bitsquare.msg.P2PNodeTest;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        RestrictionsTest.class,
        P2PNodeTest.class,
        FiatValidatorTest.class,
        RestrictionsTest.class,
        CreateOfferPMTest.class,
        BSFormatterTest.class,
        BtcValidatorTest.class
})

public class BitSquareTestSuite {
}
