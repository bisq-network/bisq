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

package bisq.core.account.age;

import org.apache.commons.lang3.time.DateUtils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AccountCreationAgeServiceTest {

    @Test
    public void testGetDelay() {
        long buyersAccountAge, requiredAccountAge, expected;

        requiredAccountAge = 0;
        buyersAccountAge = 0;
        expected = 0;
        assertEquals(expected, AccountCreationAgeService.getDelayInDays(buyersAccountAge, requiredAccountAge));

        requiredAccountAge = 42 * DateUtils.MILLIS_PER_DAY;
        buyersAccountAge = 0;
        expected = 28;
        assertEquals(expected, AccountCreationAgeService.getDelayInDays(buyersAccountAge, requiredAccountAge));

        buyersAccountAge = 42 * DateUtils.MILLIS_PER_DAY;
        expected = 0;
        assertEquals(expected, AccountCreationAgeService.getDelayInDays(buyersAccountAge, requiredAccountAge));

        buyersAccountAge = 28 * DateUtils.MILLIS_PER_DAY;
        expected = 9;
        assertEquals(expected, AccountCreationAgeService.getDelayInDays(buyersAccountAge, requiredAccountAge));

        buyersAccountAge = 100 * DateUtils.MILLIS_PER_DAY;
        expected = 0;
        assertEquals(expected, AccountCreationAgeService.getDelayInDays(buyersAccountAge, requiredAccountAge));

        buyersAccountAge = 1 * DateUtils.MILLIS_PER_DAY;
        expected = 27;
        assertEquals(expected, AccountCreationAgeService.getDelayInDays(buyersAccountAge, requiredAccountAge));

        buyersAccountAge = 2 * DateUtils.MILLIS_PER_DAY;
        expected = 27;
        assertEquals(expected, AccountCreationAgeService.getDelayInDays(buyersAccountAge, requiredAccountAge));

        buyersAccountAge = 40 * DateUtils.MILLIS_PER_DAY;
        expected = 1;
        assertEquals(expected, AccountCreationAgeService.getDelayInDays(buyersAccountAge, requiredAccountAge));

        buyersAccountAge = 41 * DateUtils.MILLIS_PER_DAY;
        expected = 1;
        assertEquals(expected, AccountCreationAgeService.getDelayInDays(buyersAccountAge, requiredAccountAge));
    }
}
