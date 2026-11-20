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

package bisq.core.dao.governance.proposal.param;

import bisq.core.btc.wallet.Restrictions;
import bisq.core.dao.governance.param.Param;
import bisq.core.locale.GlobalSettings;
import bisq.core.locale.Res;
import bisq.core.util.coin.BsqFormatter;

import org.bitcoinj.core.Coin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class ChangeParamValidatorTest {
    private Locale previousLocale;

    @BeforeEach
    public void setup() {
        previousLocale = GlobalSettings.getLocale();
        GlobalSettings.setLocale(Locale.US);
        Res.setup();
    }

    @AfterEach
    public void tearDown() {
        GlobalSettings.setLocale(previousLocale);
        Res.setup();
    }

    @Test
    public void testGetChangeValidationResult() throws ParamValidationException {
        ChangeParamValidator changeParamValidator = new ChangeParamValidator(null, null, new BsqFormatter());
        try {
            changeParamValidator.validationChange(0, 0, 2, 2, Param.UNDEFINED);
            fail();
        } catch (ParamValidationException e) {
            assertEquals(e.getError(), ParamValidationException.ERROR.SAME);
        }

        try {
            changeParamValidator.validationChange(0, 1, 2, 2, Param.UNDEFINED);
            fail();
        } catch (ParamValidationException e) {
            assertEquals(e.getError(), ParamValidationException.ERROR.NO_CHANGE_POSSIBLE);
        }

        try {
            changeParamValidator.validationChange(0, -1, 2, 2, Param.UNDEFINED);
            fail();
        } catch (ParamValidationException e) {
            assertEquals(e.getError(), ParamValidationException.ERROR.NO_CHANGE_POSSIBLE);
        }

        try {
            changeParamValidator.validationChange(2, 4, 2, 1.1, Param.UNDEFINED);
            fail();
        } catch (ParamValidationException e) {
            assertEquals(e.getError(), ParamValidationException.ERROR.TOO_HIGH);
        }

        try {
            changeParamValidator.validationChange(4, 2, 1.5, 2, Param.UNDEFINED);
            fail();
        } catch (ParamValidationException e) {
            assertEquals(e.getError(), ParamValidationException.ERROR.TOO_LOW);
        }

        changeParamValidator.validationChange(4, 2, 2, 2, Param.UNDEFINED);
        changeParamValidator.validationChange(2, 4, 2, 2, Param.UNDEFINED);
        changeParamValidator.validationChange(0, 1, 0, 0, Param.UNDEFINED);
        changeParamValidator.validationChange(0, -1, 0, 0, Param.UNDEFINED);
        changeParamValidator.validationChange(-1, 0, 0, 0, Param.UNDEFINED);
        changeParamValidator.validationChange(1, 0, 0, 0, Param.UNDEFINED);
    }

    @Test
    public void testValidateBtcValueBelowDust() {
        BsqFormatter bsqFormatter = new BsqFormatter();
        ChangeParamValidator changeParamValidator = new ChangeParamValidator(null, null, bsqFormatter);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                changeParamValidator.validateBtcValue(Coin.valueOf(100_000), Coin.ZERO, Param.DEFAULT_MAKER_FEE_BTC));
        String expectedMessage = Res.get("validation.amountBelowDust",
                bsqFormatter.formatBTCWithCode(Restrictions.getMinNonDustOutput()));
        assertEquals(expectedMessage, exception.getMessage());
        assertEquals("An amount below the dust limit of 0.00000546 BTC is not allowed.", exception.getMessage());
    }

    @Test
    public void testValidateBsqValueBelowDust() {
        BsqFormatter bsqFormatter = new BsqFormatter();
        ChangeParamValidator changeParamValidator = new ChangeParamValidator(null, null, bsqFormatter);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                changeParamValidator.validateBsqValue(Coin.valueOf(100_000), Coin.ZERO, Param.REIMBURSEMENT_MAX_AMOUNT));
        String expectedMessage = Res.get("validation.amountBelowDust",
                bsqFormatter.formatCoinWithCode(Restrictions.getMinNonDustOutput()));
        assertEquals(expectedMessage, exception.getMessage());
        assertEquals("An amount below the dust limit of 5.46 BSQ is not allowed.", exception.getMessage());
    }
}
