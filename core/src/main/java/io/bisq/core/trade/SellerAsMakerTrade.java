/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.trade;

import io.bisq.common.app.Version;
import io.bisq.common.storage.Storage;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.offer.Offer;
import io.bisq.core.trade.messages.TradeMsg;
import io.bisq.core.trade.protocol.MakerProtocol;
import io.bisq.core.trade.protocol.SellerAsMakerProtocol;
import io.bisq.network.p2p.NodeAddress;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;

@Slf4j
public final class SellerAsMakerTrade extends SellerTrade implements MakerTrade {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerAsMakerTrade(Offer offer,
                              Coin txFee,
                              Coin takeOfferFee,
                              boolean isCurrencyForTakerFeeBtc,
                              Storage<? extends TradableList> storage,
                              BtcWalletService btcWalletService) {
        super(offer, txFee, takeOfferFee, isCurrencyForTakerFeeBtc, storage, btcWalletService);
    }

    @Override
    protected void createProtocol() {
        tradeProtocol = new SellerAsMakerProtocol(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void handleTakeOfferRequest(TradeMsg message, NodeAddress taker) {
        ((MakerProtocol) tradeProtocol).handleTakeOfferRequest(message, taker);
    }
}