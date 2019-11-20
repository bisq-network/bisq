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

import bisq.core.dao.governance.param.Param;
import bisq.core.locale.Res;
import bisq.core.util.coin.BsqFormatter;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ChangeParamValidatorTest {
    @Before
    public void setup() {
        Res.setup();
    }

    @Test
    public void testGetChangeValidationResult() throws ParamValidationException {
        ChangeParamValidator changeParamValidator = new ChangeParamValidator(null, null, new BsqFormatter());
        try {
            changeParamValidator.validationChange(0, 0, 2, 2, Param.UNDEFINED);
            Assert.fail();
        } catch (ParamValidationException e) {
            Assert.assertEquals(e.getError(), ParamValidationException.ERROR.SAME);
        }

        try {
            changeParamValidator.validationChange(0, 1, 2, 2, Param.UNDEFINED);
            Assert.fail();
        } catch (ParamValidationException e) {
            Assert.assertEquals(e.getError(), ParamValidationException.ERROR.NO_CHANGE_POSSIBLE);
        }

        try {
            changeParamValidator.validationChange(0, -1, 2, 2, Param.UNDEFINED);
            Assert.fail();
        } catch (ParamValidationException e) {
            Assert.assertEquals(e.getError(), ParamValidationException.ERROR.NO_CHANGE_POSSIBLE);
        }

        try {
            changeParamValidator.validationChange(2, 4, 2, 1.1, Param.UNDEFINED);
            Assert.fail();
        } catch (ParamValidationException e) {
            Assert.assertEquals(e.getError(), ParamValidationException.ERROR.TOO_HIGH);
        }

        try {
            changeParamValidator.validationChange(4, 2, 1.5, 2, Param.UNDEFINED);
            Assert.fail();
        } catch (ParamValidationException e) {
            Assert.assertEquals(e.getError(), ParamValidationException.ERROR.TOO_LOW);
        }

        changeParamValidator.validationChange(4, 2, 2, 2, Param.UNDEFINED);
        changeParamValidator.validationChange(2, 4, 2, 2, Param.UNDEFINED);
        changeParamValidator.validationChange(0, 1, 0, 0, Param.UNDEFINED);
        changeParamValidator.validationChange(0, -1, 0, 0, Param.UNDEFINED);
        changeParamValidator.validationChange(-1, 0, 0, 0, Param.UNDEFINED);
        changeParamValidator.validationChange(1, 0, 0, 0, Param.UNDEFINED);
    }
}
