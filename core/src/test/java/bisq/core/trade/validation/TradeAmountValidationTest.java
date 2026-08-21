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

package bisq.core.trade.validation;

import bisq.core.payment.TradeLimits;

import org.bitcoinj.core.Coin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TradeAmountValidationTest {
    static final Coin OFFER_MIN_AMOUNT = Coin.valueOf(1_000);
    static final Coin OFFER_MAX_AMOUNT = Coin.valueOf(5_000);

    @Test
    void checkTradeAmountAcceptsOfferBoundsAndValuesBetweenThem() {
        Coin tradeAmount = Coin.valueOf(3_000);

        assertSame(OFFER_MIN_AMOUNT,
                TradeAmountValidation.checkTradeAmount(OFFER_MIN_AMOUNT, OFFER_MIN_AMOUNT, OFFER_MAX_AMOUNT));
        assertSame(tradeAmount,
                TradeAmountValidation.checkTradeAmount(tradeAmount, OFFER_MIN_AMOUNT, OFFER_MAX_AMOUNT));
        assertSame(OFFER_MAX_AMOUNT,
                TradeAmountValidation.checkTradeAmount(OFFER_MAX_AMOUNT, OFFER_MIN_AMOUNT, OFFER_MAX_AMOUNT));
    }

    @Test
    void checkTradeAmountRejectsAmountsBelowOfferMinimum() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> TradeAmountValidation.checkTradeAmount(Coin.valueOf(999), OFFER_MIN_AMOUNT, OFFER_MAX_AMOUNT));

        assertEquals("Trade amount must not be less than minimum offer amount. " +
                        "tradeAmount=0.00000999 BTC, offerMinAmount=0.00001 BTC",
                exception.getMessage());
    }

    @Test
    void checkTradeAmountRejectsAmountsAboveOfferMaximum() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> TradeAmountValidation.checkTradeAmount(Coin.valueOf(5_001), OFFER_MIN_AMOUNT, OFFER_MAX_AMOUNT));

        assertEquals("Trade amount must not be higher than maximum offer amount. " +
                        "tradeAmount=0.00005001 BTC, offerMaxAmount=0.00005 BTC",
                exception.getMessage());
    }

    @Test
    void checkTradeAmountRejectsAmountsAboveMaxTradeAmount() {
        // Pre-hotfix offers may persist with offer.getAmount() above MAX_TRADE_AMOUNT.
        // Re-clamp must reject regardless of offer bounds.
        Coin overCap = TradeLimits.MAX_TRADE_AMOUNT.add(Coin.valueOf(1));
        Coin offerMax = overCap.add(Coin.valueOf(1));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> TradeAmountValidation.checkTradeAmount(overCap, OFFER_MIN_AMOUNT, offerMax));

        assertEquals("Trade amount must not exceed the network max trade amount. " +
                        "tradeAmount=" + overCap.toFriendlyString() +
                        ", max=" + TradeLimits.MAX_TRADE_AMOUNT.toFriendlyString(),
                exception.getMessage());
    }

    @Test
    void checkTradeAmountAcceptsMaxTradeAmount() {
        Coin offerMax = TradeLimits.MAX_TRADE_AMOUNT;
        assertSame(offerMax,
                TradeAmountValidation.checkTradeAmount(offerMax, OFFER_MIN_AMOUNT, offerMax));
    }

    @Test
    void checkTradeAmountAcceptsPartialTakeOfLegacyOverCapOffer() {
        // Legacy pre-hotfix offer with offer.getAmount() above the cap. Taker requesting
        // an amount at-or-below the cap must still succeed (partial take of legacy offer).
        Coin offerMax = TradeLimits.MAX_TRADE_AMOUNT.add(Coin.valueOf(1_000_000));
        Coin underCap = TradeLimits.MAX_TRADE_AMOUNT.subtract(Coin.valueOf(1));

        assertSame(underCap,
                TradeAmountValidation.checkTradeAmount(underCap, OFFER_MIN_AMOUNT, offerMax));
        assertSame(TradeLimits.MAX_TRADE_AMOUNT,
                TradeAmountValidation.checkTradeAmount(TradeLimits.MAX_TRADE_AMOUNT, OFFER_MIN_AMOUNT, offerMax));
    }

    @Test
    void checkTradeAmountRejectsExactlyOneSatoshiAboveMaxTradeAmount() {
        // Boundary: MAX + 1 sat must be rejected even when offer bounds permit it.
        Coin overCapByOneSat = TradeLimits.MAX_TRADE_AMOUNT.add(Coin.valueOf(1));
        Coin offerMax = overCapByOneSat.add(Coin.valueOf(1));

        assertThrows(IllegalArgumentException.class,
                () -> TradeAmountValidation.checkTradeAmount(overCapByOneSat, OFFER_MIN_AMOUNT, offerMax));
    }

    @Test
    void checkTradeAmountChecksOfferMaxBeforeNetworkCap() {
        // Precedence: when both per-offer max and network cap would reject, the per-offer
        // message wins (more specific). Guards against future reordering breaking UX.
        Coin offerMax = TradeLimits.MAX_TRADE_AMOUNT.add(Coin.valueOf(1_000));
        Coin tradeAmount = offerMax.add(Coin.valueOf(1));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> TradeAmountValidation.checkTradeAmount(tradeAmount, OFFER_MIN_AMOUNT, offerMax));

        assertEquals("Trade amount must not be higher than maximum offer amount. " +
                        "tradeAmount=" + tradeAmount.toFriendlyString() +
                        ", offerMaxAmount=" + offerMax.toFriendlyString(),
                exception.getMessage());
    }

    @Test
    void checkTradeAmountRejectsInvalidOfferBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> TradeAmountValidation.checkTradeAmount(Coin.valueOf(3_000), OFFER_MAX_AMOUNT, OFFER_MIN_AMOUNT));
    }

    @Test
    void checkTradeAmountRejectsNullAndNonPositiveAmounts() {
        assertThrows(NullPointerException.class,
                () -> TradeAmountValidation.checkTradeAmount(null, OFFER_MIN_AMOUNT, OFFER_MAX_AMOUNT));
        assertThrows(NullPointerException.class,
                () -> TradeAmountValidation.checkTradeAmount(Coin.valueOf(3_000), null, OFFER_MAX_AMOUNT));
        assertThrows(NullPointerException.class,
                () -> TradeAmountValidation.checkTradeAmount(Coin.valueOf(3_000), OFFER_MIN_AMOUNT, null));
        assertThrows(IllegalArgumentException.class,
                () -> TradeAmountValidation.checkTradeAmount(Coin.ZERO, OFFER_MIN_AMOUNT, OFFER_MAX_AMOUNT));
        assertThrows(IllegalArgumentException.class,
                () -> TradeAmountValidation.checkTradeAmount(Coin.valueOf(3_000), Coin.ZERO, OFFER_MAX_AMOUNT));
        assertThrows(IllegalArgumentException.class,
                () -> TradeAmountValidation.checkTradeAmount(Coin.valueOf(3_000), OFFER_MIN_AMOUNT, Coin.ZERO));
    }

}
