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

import java.math.BigDecimal;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static bisq.apitest.config.ApiTestConfig.BSQ;
import static bisq.apitest.config.ApiTestConfig.USD;
import static bisq.apitest.config.ApiTestConfig.XMR;
import static bisq.core.btc.wallet.Restrictions.getMinBuyerSecurityDepositAsCoin;
import static bisq.core.btc.wallet.Restrictions.getMinBuyerSecurityDepositAsPercent;
import static bisq.core.btc.wallet.Restrictions.getMinSellerSecurityDepositAsCoin;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static protobuf.OfferDirection.BUY;
import static protobuf.OfferDirection.SELL;

/**
 * Fresh-stack test for offer-min-deposit-still-takeable. Since #260 a taker refuses an offer
 * whose buyer or seller security deposit is below the minimum. An honest offer with a small
 * amount and the minimum deposit percentage gets exactly that minimum, so the refusal must
 * not hit it: the offer must still trade to completion. The check reads both deposits of
 * every v1 offer, so one fiat offer (Alice buys BTC) and one altcoin offer (Alice sells BTC)
 * cover both directions.
 */
@Slf4j
@Tag("freshstack")
public class TakeMinDepositOfferTest extends DockerTradeTest {

    // 15 % of this amount is below the floor, so both deposits are raised to the floor.
    private static final long SMALL_AMOUNT = 100_000L;

    @Test
    public void testFiatOfferWithMinimumDepositIsTakeable() {
        ensureF2FAccounts("US");
        OfferInfo offer = DaoTestUtils.placeV1OfferWhenReady(() -> aliceClient.createFixedPricedOffer(BUY.name(),
                USD, SMALL_AMOUNT, SMALL_AMOUNT, "50000",
                minDepositPct(), alicesF2F.getId(), BSQ));
        assertDepositsAtFloor(offer);
        mineBlocks(1);
        awaitOfferActivated(offer.getId());

        runV1Trade(offer, /* bobIsBtcBuyer = */ false, bobsF2F.getId(), USD, SMALL_AMOUNT);
    }

    @Test
    public void testAltcoinOfferWithMinimumDepositIsTakeable() {
        ensureXmrAccounts();
        OfferInfo offer = DaoTestUtils.placeV1OfferWhenReady(() -> aliceClient.createFixedPricedOffer(SELL.name(),
                XMR, SMALL_AMOUNT, SMALL_AMOUNT, "0.00455500",
                minDepositPct(), alicesXmrAcct.getId(), BSQ));
        assertDepositsAtFloor(offer);
        mineBlocks(1);
        awaitOfferActivated(offer.getId());

        runV1Trade(offer, /* bobIsBtcBuyer = */ true, bobsXmrAcct.getId(), XMR, SMALL_AMOUNT);
    }

    private static double minDepositPct() {
        // BigDecimal keeps 0.15 at 15.0; a double multiplication gives 15.000000000000002.
        return BigDecimal.valueOf(getMinBuyerSecurityDepositAsPercent()).movePointRight(2).doubleValue();
    }

    private static void assertDepositsAtFloor(OfferInfo offer) {
        assertEquals(getMinBuyerSecurityDepositAsCoin().value, offer.getBuyerSecurityDeposit(),
                "buyer deposit must be the minimum");
        assertEquals(getMinSellerSecurityDepositAsCoin().value, offer.getSellerSecurityDeposit(),
                "seller deposit must be the minimum");
    }
}
