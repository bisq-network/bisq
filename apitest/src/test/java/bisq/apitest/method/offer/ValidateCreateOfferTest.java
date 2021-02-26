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

package bisq.apitest.method.offer;

import bisq.core.payment.PaymentAccount;

import io.grpc.StatusRuntimeException;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.core.btc.wallet.Restrictions.getDefaultBuyerSecurityDepositAsPercent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Disabled
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ValidateCreateOfferTest extends AbstractOfferTest {

    @Test
    @Order(1)
    public void testAmtTooLargeShouldThrowException() {
        PaymentAccount usdAccount = createDummyF2FAccount(aliceClient, "US");
        @SuppressWarnings("ResultOfMethodCallIgnored")
        Throwable exception = assertThrows(StatusRuntimeException.class, () ->
                aliceClient.createFixedPricedOffer("buy",
                        "usd",
                        100000000000L, // exceeds amount limit
                        100000000000L,
                        "10000.0000",
                        getDefaultBuyerSecurityDepositAsPercent(),
                        usdAccount.getId(),
                        "bsq"));
        assertEquals("UNKNOWN: An error occurred at task: ValidateOffer",
                exception.getMessage());
    }
}
