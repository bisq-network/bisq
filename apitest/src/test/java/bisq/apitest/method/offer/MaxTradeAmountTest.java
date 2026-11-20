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

import bisq.apitest.dao.DaoTestUtils;

import bisq.proto.grpc.OfferInfo;

import io.grpc.StatusRuntimeException;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static bisq.apitest.config.ApiTestConfig.BTC;
import static bisq.apitest.config.ApiTestConfig.XMR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static protobuf.OfferDirection.BUY;

/**
 * The hard cap of a trade is 0.5 BTC (TradeLimits.MAX_TRADE_AMOUNT). The regtest DAO keeps the default
 * MAX_TRADE_LIMIT of 2 BTC, so a payment method without risk factor (XMR) is limited by the cap alone:
 * 0.5 BTC is accepted (v1.10.11 refused it with its cap of 0.25 BTC) and any amount above it is refused.
 * The offers left open are cancelled by DockerOfferTest.
 */
@Slf4j
public class MaxTradeAmountTest extends DockerOfferTest {

    private static final long MAX_TRADE_AMOUNT = 50_000_000L;
    // Above the cap by 0.0001 BTC, so that a rounding of the amount cannot bring it back to the cap
    private static final long ABOVE_MAX_TRADE_AMOUNT = 50_010_000L;
    private static final String XMR_PRICE = "0.005";

    @BeforeAll
    public static void setupAccounts() {
        ensureXmrAccounts();
    }

    @Test
    public void testOfferAtAndAboveMaxTradeAmount() {
        OfferInfo offer = DaoTestUtils.placeV1OfferWhenReady(() -> aliceClient.createFixedPricedOffer(BUY.name(),
                XMR,
                MAX_TRADE_AMOUNT,
                MAX_TRADE_AMOUNT,
                XMR_PRICE,
                defaultBuyerSecurityDepositPct.get(),
                alicesXmrAcct.getId(),
                BTC));
        assertTrue(offer.getIsMyOffer());
        assertEquals(MAX_TRADE_AMOUNT, offer.getAmount());

        // The maker is ready now, as the offer above was accepted. A ValidateOffer failure can only be the amount.
        Throwable exception = assertThrows(StatusRuntimeException.class, () ->
                aliceClient.createFixedPricedOffer(BUY.name(),
                        XMR,
                        ABOVE_MAX_TRADE_AMOUNT,
                        ABOVE_MAX_TRADE_AMOUNT,
                        XMR_PRICE,
                        defaultBuyerSecurityDepositPct.get(),
                        alicesXmrAcct.getId(),
                        BTC));
        assertTrue(exception.getMessage().toLowerCase().contains("validateoffer"),
                "expected validateoffer rejection, got: " + exception.getMessage());
    }
}
