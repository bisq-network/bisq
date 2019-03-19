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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChangeParamValidatorTest {

    @Test
    public void testGetChangeValidationResult() {
        assertEquals(ChangeParamValidator.Result.SAME,
                ChangeParamValidator.getChangeValidationResult(0, 0, 2, 2));
        assertEquals(ChangeParamValidator.Result.NO_CHANGE_POSSIBLE,
                ChangeParamValidator.getChangeValidationResult(0, 1, 2, 2));
        assertEquals(ChangeParamValidator.Result.NO_CHANGE_POSSIBLE,
                ChangeParamValidator.getChangeValidationResult(0, -1, 2, 2));
        assertEquals(ChangeParamValidator.Result.NO_CHANGE_POSSIBLE,
                ChangeParamValidator.getChangeValidationResult(-1, 0, 2, 2));
        assertEquals(ChangeParamValidator.Result.NO_CHANGE_POSSIBLE,
                ChangeParamValidator.getChangeValidationResult(1, 0, 2, 2));

        assertEquals(ChangeParamValidator.Result.SAME,
                ChangeParamValidator.getChangeValidationResult(0, 0, 0, 0));
        assertEquals(ChangeParamValidator.Result.OK,
                ChangeParamValidator.getChangeValidationResult(0, 1, 0, 0));
        assertEquals(ChangeParamValidator.Result.OK,
                ChangeParamValidator.getChangeValidationResult(0, -1, 0, 0));
        assertEquals(ChangeParamValidator.Result.OK,
                ChangeParamValidator.getChangeValidationResult(-1, 0, 0, 0));
        assertEquals(ChangeParamValidator.Result.OK,
                ChangeParamValidator.getChangeValidationResult(1, 0, 0, 0));

        assertEquals(ChangeParamValidator.Result.OK,
                ChangeParamValidator.getChangeValidationResult(2, 4, 2, 2));
        assertEquals(ChangeParamValidator.Result.TOO_HIGH,
                ChangeParamValidator.getChangeValidationResult(2, 4, 2, 1.1));
        assertEquals(ChangeParamValidator.Result.OK,
                ChangeParamValidator.getChangeValidationResult(4, 2, 2, 2));
        assertEquals(ChangeParamValidator.Result.TOO_LOW,
                ChangeParamValidator.getChangeValidationResult(4, 2, 1.5, 2));

    }
}
