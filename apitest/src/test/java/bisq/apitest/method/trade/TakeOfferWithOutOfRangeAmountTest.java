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

package bisq.apitest.method.trade;

import bisq.core.payment.PaymentAccount;

import bisq.proto.grpc.OfferInfo;

import io.grpc.StatusRuntimeException;

import org.bitcoinj.core.Coin;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.config.ApiTestConfig.BTC;
import static bisq.apitest.config.ApiTestConfig.USD;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static protobuf.OfferDirection.BUY;

@Disabled
@SuppressWarnings("ConstantConditions")
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TakeOfferWithOutOfRangeAmountTest extends AbstractTradeTest {

    @Test
    @Order(1)
    public void testTakeOfferWithInvalidAmountParam(final TestInfo testInfo) {
        try {
            PaymentAccount alicesUsdAccount = createDummyF2FAccount(aliceClient, "US");
            PaymentAccount bobsUsdAccount = createDummyF2FAccount(bobClient, "US");

            var alicesOffer = aliceClient.createMarketBasedPricedOffer(BUY.name(),
                    USD,
                    10_000_000L,
                    8_000_000L,
                    0.00,
                    defaultBuyerSecurityDepositPct.get(),
                    alicesUsdAccount.getId(),
                    BTC,
                    NO_TRIGGER_PRICE);

            // Wait for Alice's AddToOfferBook task.
            // Wait times vary;  my logs show >= 2-second delay.
            sleep(3_000); // TODO loop instead of hard code a wait time
            List<OfferInfo> alicesUsdOffers = aliceClient.getMyOffersSortedByDate(BUY.name(), USD);
            assertEquals(1, alicesUsdOffers.size());

            var intendedTradeAmountTooLow = 7_000_000L;
            takeOfferWithInvalidAmountParam(bobsUsdAccount, alicesOffer, intendedTradeAmountTooLow);

            var intendedTradeAmountTooHigh = 11_000_000L;
            takeOfferWithInvalidAmountParam(bobsUsdAccount, alicesOffer, intendedTradeAmountTooHigh);
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    private void takeOfferWithInvalidAmountParam(PaymentAccount paymentAccount,
                                                 OfferInfo offer,
                                                 long invalidTakeOfferAmount) {
        Throwable exception = assertThrows(StatusRuntimeException.class, () ->
                takeAlicesOffer(offer.getId(),
                        paymentAccount.getId(),
                        BTC,
                        invalidTakeOfferAmount,
                        false));

        var invalidAmount = Coin.valueOf(invalidTakeOfferAmount);
        var minAmount = Coin.valueOf(offer.getMinAmount());
        var maxAmount = Coin.valueOf(offer.getAmount());
        String expectedExceptionMessage =
                format("INVALID_ARGUMENT: intended trade amount %s is outside offer's min - max amount range of %s - %s",
                        invalidAmount.toPlainString(),
                        minAmount.toPlainString(),
                        maxAmount.toPlainString());
        log.info(exception.getMessage());
        assertEquals(expectedExceptionMessage, exception.getMessage());
    }

}
