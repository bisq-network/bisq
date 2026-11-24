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

package bisq.core.trade.bisq_v1;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.trade.model.bisq_v1.BuyerAsMakerTrade;
import bisq.core.trade.model.bisq_v1.BuyerAsTakerTrade;
import bisq.core.trade.model.bisq_v1.SellerAsMakerTrade;
import bisq.core.trade.model.bisq_v1.SellerAsTakerTrade;
import bisq.core.trade.model.bisq_v1.Trade;

import bisq.common.crypto.KeyRing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// API clients call 'gettrade' right after 'takeoffer', before the trade protocol has created the contract.
// The role must already be known then, so it must not depend on the contract.
public class TradeUtilTradeRoleTest {
    private final KeyRing keyRing = mock(KeyRing.class);
    private final TradeUtil tradeUtil = new TradeUtil(mock(BtcWalletService.class), keyRing);

    @BeforeEach
    public void setUp() {
        Res.setBaseCurrencyCode("BTC");
        Res.setBaseCurrencyName("Bitcoin");
    }

    // The trade protocol derives isBuyerMakerAndSellerTaker of the contract from these trade types, and
    // TradeManager selects the trade type from the offer direction.
    @Test
    public void buyerAsMakerWithoutContract() {
        Trade trade = tradeWithoutContract(BuyerAsMakerTrade.class, true, true);

        assertEquals(tradeUtil.getRole(true, true, "EUR"), tradeUtil.getRole(trade));
    }

    @Test
    public void sellerAsTakerWithoutContract() {
        Trade trade = tradeWithoutContract(SellerAsTakerTrade.class, true, false);

        assertEquals(tradeUtil.getRole(true, false, "EUR"), tradeUtil.getRole(trade));
    }

    @Test
    public void sellerAsMakerWithoutContract() {
        Trade trade = tradeWithoutContract(SellerAsMakerTrade.class, false, true);

        assertEquals(tradeUtil.getRole(false, true, "EUR"), tradeUtil.getRole(trade));
    }

    @Test
    public void buyerAsTakerWithoutContract() {
        Trade trade = tradeWithoutContract(BuyerAsTakerTrade.class, false, false);

        assertEquals(tradeUtil.getRole(false, false, "EUR"), tradeUtil.getRole(trade));
    }

    @Test
    public void tradeWithoutOfferHasNoRole() {
        Trade trade = mock(BuyerAsTakerTrade.class);

        assertThrows(IllegalStateException.class, () -> tradeUtil.getRole(trade));
    }

    private Trade tradeWithoutContract(Class<? extends Trade> tradeType, boolean isBuyOffer, boolean isMyOffer) {
        Offer offer = mock(Offer.class);
        when(offer.isBuyOffer()).thenReturn(isBuyOffer);
        when(offer.isMyOffer(keyRing)).thenReturn(isMyOffer);
        when(offer.getCurrencyCode()).thenReturn("EUR");
        Trade trade = mock(tradeType);
        when(trade.getOffer()).thenReturn(offer);
        when(trade.getContract()).thenReturn(null);
        return trade;
    }
}
