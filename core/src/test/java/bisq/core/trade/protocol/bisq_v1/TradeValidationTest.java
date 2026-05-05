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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.trade.protocol.bisq_v1;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TradeValidationTest {
    private static final Coin OFFER_MIN_AMOUNT = Coin.valueOf(1_000);
    private static final Coin OFFER_MAX_AMOUNT = Coin.valueOf(5_000);

    @Test
    void checkTradeAmountAcceptsOfferBoundsAndValuesBetweenThem() {
        Coin tradeAmount = Coin.valueOf(3_000);

        assertSame(OFFER_MIN_AMOUNT,
                TradeValidation.checkTradeAmount(OFFER_MIN_AMOUNT, OFFER_MIN_AMOUNT, OFFER_MAX_AMOUNT));
        assertSame(tradeAmount,
                TradeValidation.checkTradeAmount(tradeAmount, OFFER_MIN_AMOUNT, OFFER_MAX_AMOUNT));
        assertSame(OFFER_MAX_AMOUNT,
                TradeValidation.checkTradeAmount(OFFER_MAX_AMOUNT, OFFER_MIN_AMOUNT, OFFER_MAX_AMOUNT));
    }

    @Test
    void checkTradeAmountRejectsAmountsBelowOfferMinimum() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkTradeAmount(Coin.valueOf(999), OFFER_MIN_AMOUNT, OFFER_MAX_AMOUNT));

        assertEquals("Trade amount must not be less than minimum offer amount. " +
                        "tradeAmount=0.00000999 BTC, offerMinAmount=0.00001 BTC",
                exception.getMessage());
    }

    @Test
    void checkTradeAmountRejectsAmountsAboveOfferMaximum() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkTradeAmount(Coin.valueOf(5_001), OFFER_MIN_AMOUNT, OFFER_MAX_AMOUNT));

        assertEquals("Trade amount must not be higher than maximum offer amount. " +
                        "tradeAmount=0.00005001 BTC, offerMaxAmount=0.00005 BTC",
                exception.getMessage());
    }

    @Test
    void checkMultiSigPubKeyAcceptsCompressedPublicKey() {
        byte[] multiSigPubKey = new ECKey().getPubKey();

        assertEquals(33, multiSigPubKey.length);
        assertSame(multiSigPubKey, TradeValidation.checkMultiSigPubKey(multiSigPubKey));
    }

    @Test
    void checkMultiSigPubKeyRejectsNullPublicKey() {
        assertThrows(NullPointerException.class, () -> TradeValidation.checkMultiSigPubKey(null));
    }

    @Test
    void checkMultiSigPubKeyRejectsUncompressedPublicKey() {
        byte[] uncompressedPubKey = new ECKey().decompress().getPubKey();

        assertEquals(65, uncompressedPubKey.length);
        assertThrows(IllegalArgumentException.class, () -> TradeValidation.checkMultiSigPubKey(uncompressedPubKey));
    }

    @Test
    void checkMultiSigPubKeyRejectsMalformedCompressedPublicKey() {
        byte[] malformedCompressedPubKey = new byte[33];
        Arrays.fill(malformedCompressedPubKey, (byte) 0xff);
        malformedCompressedPubKey[0] = 0x02;

        assertThrows(IllegalArgumentException.class,
                () -> TradeValidation.checkMultiSigPubKey(malformedCompressedPubKey));
    }
}
