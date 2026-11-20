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
import bisq.core.offer.OfferDirection;
import bisq.core.offer.bsq_swap.BsqSwapOfferPayload;
import bisq.core.trade.model.bsq_swap.BsqSwapBuyerAsMakerTrade;
import bisq.core.trade.model.bsq_swap.BsqSwapSellerAsTakerTrade;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.protocol.bsq_swap.model.BsqSwapProtocolModel;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.KeyRing;
import bisq.common.crypto.KeyStorage;

import org.bitcoinj.core.Coin;

import java.io.File;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

// After a restart the protocol model of a completed BSQ swap has no service provider,
// so the role must not depend on it.
public class TradeUtilBsqSwapRoleTest {
    private static final Coin AMOUNT = Coin.valueOf(1_000_000);
    private static final NodeAddress PEER = new NodeAddress("peer.onion:9999");

    @TempDir
    File dir;

    @BeforeEach
    public void setUp() {
        Res.setBaseCurrencyCode("BTC");
        Res.setBaseCurrencyName("Bitcoin");
    }

    @Test
    public void makerRoleOfACompletedBsqSwap() {
        KeyRing keyRing = keyRing("maker");
        Offer offer = bsqSwapBuyOffer(keyRing);
        TradeUtil tradeUtil = new TradeUtil(mock(BtcWalletService.class), keyRing);

        BsqSwapBuyerAsMakerTrade trade = new BsqSwapBuyerAsMakerTrade(offer, AMOUNT, 0, PEER, 10, 0, 0,
                new BsqSwapProtocolModel(keyRing.getPubKeyRing()));
        trade.setState(BsqSwapTrade.State.COMPLETED);

        assertEquals(tradeUtil.getRole(true, true, "BSQ"), tradeUtil.getRole(trade));
    }

    @Test
    public void takerRoleOfACompletedBsqSwap() {
        KeyRing makerKeyRing = keyRing("maker");
        KeyRing takerKeyRing = keyRing("taker");
        Offer offer = bsqSwapBuyOffer(makerKeyRing);
        TradeUtil tradeUtil = new TradeUtil(mock(BtcWalletService.class), takerKeyRing);

        BsqSwapSellerAsTakerTrade trade = new BsqSwapSellerAsTakerTrade(offer, AMOUNT, PEER, 10, 0, 0,
                new BsqSwapProtocolModel(takerKeyRing.getPubKeyRing()));
        trade.setState(BsqSwapTrade.State.COMPLETED);

        assertEquals(tradeUtil.getRole(true, false, "BSQ"), tradeUtil.getRole(trade));
    }

    private KeyRing keyRing(String name) {
        File keyDir = new File(dir, name);
        keyDir.mkdirs();
        return new KeyRing(new KeyStorage(keyDir));
    }

    private static Offer bsqSwapBuyOffer(KeyRing makerKeyRing) {
        return new Offer(new BsqSwapOfferPayload("offer-id", 0, new NodeAddress("maker.onion:9999"),
                makerKeyRing.getPubKeyRing(), OfferDirection.BUY, 5000, AMOUNT.value, AMOUNT.value, null,
                "1.10.9", 1));
    }
}
