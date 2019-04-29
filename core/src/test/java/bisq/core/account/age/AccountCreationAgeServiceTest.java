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

import org.bitcoinj.core.Coin;

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

        requiredAccountAge = 30 * DateUtils.MILLIS_PER_DAY;
        buyersAccountAge = 0;
        expected = 30;
        assertEquals(expected, AccountCreationAgeService.getDelayInDays(buyersAccountAge, requiredAccountAge));

        requiredAccountAge = 30 * DateUtils.MILLIS_PER_DAY;
        buyersAccountAge = 15 * DateUtils.MILLIS_PER_DAY;
        expected = 19;
        assertEquals(expected, AccountCreationAgeService.getDelayInDays(buyersAccountAge, requiredAccountAge));


        buyersAccountAge = 60 * DateUtils.MILLIS_PER_DAY;
        expected = 7;
        assertEquals(expected, AccountCreationAgeService.getDelayInDays(buyersAccountAge, requiredAccountAge));

        buyersAccountAge = DateUtils.MILLIS_PER_DAY;
        expected = 29;
        assertEquals(expected, AccountCreationAgeService.getDelayInDays(buyersAccountAge, requiredAccountAge));

        buyersAccountAge = 2 * DateUtils.MILLIS_PER_DAY;
        expected = 28;
        assertEquals(expected, AccountCreationAgeService.getDelayInDays(buyersAccountAge, requiredAccountAge));
    }

    @Test
    public void testGetMyAccountMinDepositAsCoin() {
        long buyersAccountAge, requiredAccountAge, expected;
        long minBuyerSecurityDeposit = Coin.parseCoin("0.001").value;
        requiredAccountAge = 0;
        buyersAccountAge = 0;
        expected = minBuyerSecurityDeposit;
        assertEquals(expected, AccountCreationAgeService.getMyAccountMinDepositAsCoin(buyersAccountAge, requiredAccountAge, minBuyerSecurityDeposit));

        requiredAccountAge = 30 * DateUtils.MILLIS_PER_DAY;
        buyersAccountAge = 0;
        expected = 3 * minBuyerSecurityDeposit;
        assertEquals(expected, AccountCreationAgeService.getMyAccountMinDepositAsCoin(buyersAccountAge, requiredAccountAge, minBuyerSecurityDeposit));

        requiredAccountAge = 30 * DateUtils.MILLIS_PER_DAY;
        buyersAccountAge = 15 * DateUtils.MILLIS_PER_DAY;
        expected = 2 * minBuyerSecurityDeposit;
        assertEquals(expected, AccountCreationAgeService.getMyAccountMinDepositAsCoin(buyersAccountAge, requiredAccountAge, minBuyerSecurityDeposit));

        requiredAccountAge = 30 * DateUtils.MILLIS_PER_DAY;
        buyersAccountAge = requiredAccountAge;
        expected = minBuyerSecurityDeposit;
        assertEquals(expected, AccountCreationAgeService.getMyAccountMinDepositAsCoin(buyersAccountAge, requiredAccountAge, minBuyerSecurityDeposit));

        requiredAccountAge = 30 * DateUtils.MILLIS_PER_DAY;
        buyersAccountAge = 2 * requiredAccountAge;
        expected = minBuyerSecurityDeposit;
        assertEquals(expected, AccountCreationAgeService.getMyAccountMinDepositAsCoin(buyersAccountAge, requiredAccountAge, minBuyerSecurityDeposit));
    }

    @Test
    public void testGetMyAccountMinDepositAsPercent() {
        long buyersAccountAge, requiredAccountAge;
        double expected;
        double delta = 0.00001;

      /*  requiredAccountAge = 0;
        buyersAccountAge = 0;
        expected = minBuyerSecurityDeposit;
        assertEquals(expected, AccountCreationAgeService.getMyAccountMinDepositAsPercent(buyersAccountAge, requiredAccountAge, minBuyerSecurityDeposit), delta);
*/
        requiredAccountAge = 30 * DateUtils.MILLIS_PER_DAY;
        buyersAccountAge = 0;
        expected = 0.3;
        assertEquals(expected, AccountCreationAgeService.getMyAccountMinDepositAsPercent(buyersAccountAge, requiredAccountAge, 0.1), delta);

        requiredAccountAge = 30 * DateUtils.MILLIS_PER_DAY;
        buyersAccountAge = 15 * DateUtils.MILLIS_PER_DAY;
        expected = 0.2;
        assertEquals(expected, AccountCreationAgeService.getMyAccountMinDepositAsPercent(buyersAccountAge, requiredAccountAge, 0.1), delta);

        requiredAccountAge = 30 * DateUtils.MILLIS_PER_DAY;
        buyersAccountAge = 30 * DateUtils.MILLIS_PER_DAY;
        expected = 0.1;
        assertEquals(expected, AccountCreationAgeService.getMyAccountMinDepositAsPercent(buyersAccountAge, requiredAccountAge, 0.1), delta);

        requiredAccountAge = 30 * DateUtils.MILLIS_PER_DAY;
        buyersAccountAge = 60 * DateUtils.MILLIS_PER_DAY;
        expected = 0.1;
        assertEquals(expected, AccountCreationAgeService.getMyAccountMinDepositAsPercent(buyersAccountAge, requiredAccountAge, 0.1), delta);
    }


}
