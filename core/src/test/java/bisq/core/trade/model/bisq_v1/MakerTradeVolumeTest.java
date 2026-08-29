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
import bisq.core.trade.validation.TradeAmountValidation;

import org.bitcoinj.core.Coin;

import org.junit.jupiter.api.Test;

import static com.natpryce.makeiteasy.MakeItEasy.a;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MakerTradeVolumeTest {

    // 0.0001 BTC, the minimum trade amount.
    private static final Coin TRADE_AMOUNT = Coin.valueOf(10_000);

    private static SellerAsMakerTrade makerTrade() {
        Offer offer = make(a(OfferMaker.Offer,
                with(OfferMaker.baseCurrencyCode, "USDC"),
                with(OfferMaker.counterCurrencyCode, "BTC"),
                with(OfferMaker.direction, OfferDirection.SELL)));
        return new SellerAsMakerTrade(offer,
                Coin.valueOf(1_000) /* txFee */,
                Coin.valueOf(100) /* takerFee */,
                true,
                null /* arbitratorNodeAddress */,
                null /* mediatorNodeAddress */,
                null /* refundAgentNodeAddress */,
                null /* btcWalletService, unused by getVolume */,
                null /* processModel, unused by getVolume */,
                "uid");
    }

    @Test
    void makerTradeHasNoVolumeUntilTakersPriceIsApplied() {
        SellerAsMakerTrade trade = makerTrade();
        trade.setAmount(TRADE_AMOUNT);

        // The maker constructor does not set a price; it is applied from the take
        // request, so the trade volume can only be validated after that point.
        assertNull(trade.getVolume());
        assertThrows(NullPointerException.class,
                () -> TradeAmountValidation.checkTradeVolume(trade.getVolume()));

        // 0.01 BTC per USDC: 0.0001 BTC buys 0.01 USDC, exactly on the 6-decimal grid.
        trade.setPriceAsLong(1_000_000);
        assertEquals(1_000_000,
                TradeAmountValidation.checkTradeVolume(trade.getVolume()).getValue());
    }

    @Test
    void makerRejectsTradeAmountThatRoundsToZeroVolume() {
        SellerAsMakerTrade trade = makerTrade();
        trade.setAmount(TRADE_AMOUNT);

        // 250 BTC per USDC: 0.0001 BTC buys 0.0000004 USDC, below half of one
        // on-chain unit of the 6-decimal coin, so the volume rounds to zero.
        trade.setPriceAsLong(25_000_000_000L);
        assertEquals("Trade volume must not be zero. currencyCode=USDC",
                assertThrows(IllegalArgumentException.class,
                        () -> TradeAmountValidation.checkTradeVolume(trade.getVolume())).getMessage());
    }
}
