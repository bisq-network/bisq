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

package bisq.core.trade.model.bisq_v1;

import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.OfferMaker;

import org.bitcoinj.core.Coin;

import org.junit.jupiter.api.Test;

import static com.natpryce.makeiteasy.MakeItEasy.a;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StoredTradeTxFeeTest {

    // fromProto loads a trade through this constructor. Makers up to v1.10.11 accepted trade tx fees down to
    // 250 sat, below the 424 sat that a new take offer request needs, and one trade that fails the check makes
    // the whole trade file unreadable. Such a trade must still load.
    @Test
    void tradeWithTheMinimumFeeOfEarlierVersionsStillLoads() {
        assertEquals(250, sellerAsMakerTrade(Coin.valueOf(250)).getTradeTxFee().value);
        assertThrows(IllegalArgumentException.class, () -> sellerAsMakerTrade(Coin.valueOf(249)));
    }

    private static SellerAsMakerTrade sellerAsMakerTrade(Coin tradeTxFee) {
        Offer offer = make(a(OfferMaker.Offer, with(OfferMaker.direction, OfferDirection.SELL)));
        return new SellerAsMakerTrade(offer,
                tradeTxFee,
                Coin.valueOf(100) /* takerFee */,
                true,
                null /* arbitratorNodeAddress */,
                null /* mediatorNodeAddress */,
                null /* refundAgentNodeAddress */,
                null /* btcWalletService, unused by the constructor */,
                null /* processModel, unused by the constructor */,
                "uid");
    }
}
