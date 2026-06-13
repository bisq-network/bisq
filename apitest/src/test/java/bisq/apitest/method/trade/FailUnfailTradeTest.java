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

import bisq.apitest.dao.DaoTestUtils;
import bisq.proto.grpc.OfferInfo;
import bisq.proto.grpc.TradeInfo;

import io.grpc.StatusRuntimeException;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static bisq.apitest.config.ApiTestConfig.BTC;
import static bisq.apitest.config.ApiTestConfig.USD;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static protobuf.OfferDirection.BUY;
import static protobuf.OfferDirection.SELL;

/**
 * Fresh-stack test: drives a trade up to the deposit-confirmed phase, then
 * exercises {@code failTrade} / {@code unFailTrade} admin API on both v1 BUY
 * and v1 SELL flows.
 */
@Slf4j
@Tag("freshstack")
public class FailUnfailTradeTest extends DockerTradeTest {

    @Test
    public void testFailAndUnFailBuyBTCTrade() {
        ensureF2FAccounts("US");
        OfferInfo offer = DaoTestUtils.placeV1OfferWhenReady(() -> aliceClient.createFixedPricedOffer(BUY.name(),
                USD, 1_250_000L, 1_250_000L, "50000",
                defaultBuyerSecurityDepositPct.get(), alicesF2F.getId(), BTC));
        TradeInfo trade = driveUpToDepositConfirmed(offer);
        assertFailUnfailCycle(trade.getTradeId());
    }

    @Test
    public void testFailAndUnFailSellBTCTrade() {
        ensureF2FAccounts("US");
        OfferInfo offer = DaoTestUtils.placeV1OfferWhenReady(() -> aliceClient.createFixedPricedOffer(SELL.name(),
                USD, 1_250_000L, 1_250_000L, "50000",
                defaultBuyerSecurityDepositPct.get(), alicesF2F.getId(), BTC));
        TradeInfo trade = driveUpToDepositConfirmed(offer);
        assertFailUnfailCycle(trade.getTradeId());
    }

    private TradeInfo driveUpToDepositConfirmed(OfferInfo offer) {
        if (!offer.getOfferFeePaymentTxId().isEmpty()) {
            chain.confirmTx(aliceClient, offer.getOfferFeePaymentTxId());
        }
        DaoTestUtils.await(() -> bobClient.getOffers("BUY", USD).stream()
                        .anyMatch(o -> o.getId().equals(offer.getId()))
                        || bobClient.getOffers("SELL", USD).stream()
                        .anyMatch(o -> o.getId().equals(offer.getId())),
                60_000, "bob sees offer " + offer.getId());
        TradeInfo trade = bobClient.takeOffer(offer.getId(), bobsF2F.getId(), BTC, offer.getAmount());
        assertNotNull(trade);
        DaoTestUtils.await(() -> !bobClient.getTrade(trade.getTradeId()).getTakerFeeTxId().isEmpty(),
                30_000, "bob has taker_fee_tx_id");
        chain.confirmTx(bobClient, bobClient.getTrade(trade.getTradeId()).getTakerFeeTxId());
        DaoTestUtils.await(() -> !bobClient.getTrade(trade.getTradeId()).getDepositTxId().isEmpty(),
                30_000, "bob has deposit_tx_id");
        chain.confirmTx(bobClient, bobClient.getTrade(trade.getTradeId()).getDepositTxId());
        DaoTestUtils.await(() -> bobClient.getTrade(trade.getTradeId()).getIsDepositConfirmed(),
                30_000, "deposit confirmed");
        return trade;
    }

    private void assertFailUnfailCycle(String tradeId) {
        aliceClient.failTrade(tradeId);
        Throwable ex = assertThrows(StatusRuntimeException.class, () -> aliceClient.getTrade(tradeId));
        assertEquals(format("NOT_FOUND: trade with id '%s' not found", tradeId), ex.getMessage());

        assertDoesNotThrow(() -> {
            aliceClient.unFailTrade(tradeId);
            aliceClient.getTrade(tradeId);
        });
    }
}
