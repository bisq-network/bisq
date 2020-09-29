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

import bisq.proto.grpc.CreateOfferRequest;
import bisq.proto.grpc.OfferInfo;

import io.grpc.StatusRuntimeException;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static bisq.core.btc.wallet.Restrictions.getDefaultBuyerSecurityDepositAsPercent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ValidateCreateOfferTest extends AbstractCreateOfferTest {

    @Test
    @Order(1)
    public void testAmtTooLargeShouldThrowException() {
        var paymentAccount = getDefaultPerfectDummyPaymentAccount(alicedaemon);
        var req = CreateOfferRequest.newBuilder()
                .setPaymentAccountId(paymentAccount.getId())
                .setDirection("buy")
                .setCurrencyCode("usd")
                .setAmount(100000000000L)
                .setMinAmount(100000000000L)
                .setUseMarketBasedPrice(false)
                .setMarketPriceMargin(0.00)
                .setPrice("10000.0000")
                .setBuyerSecurityDeposit(getDefaultBuyerSecurityDepositAsPercent())
                .build();
        OfferInfo newOffer = aliceStubs.offersService.createOffer(req).getOffer();
        Throwable exception = assertThrows(StatusRuntimeException.class, () ->
            aliceStubs.offersService.placeOffer(
                    createPlaceOfferRequest(newOffer.getId(),
                            getDefaultBuyerSecurityDepositAsPercent())));
        assertEquals("UNKNOWN: Error at taskRunner: An error occurred at task ValidateOffer: Amount is larger than 1.00 BTC",
                exception.getMessage());
    }
}
