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

package io.bisq.core.trade;

import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.common.storage.Storage;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.offer.Offer;
import io.bisq.core.trade.protocol.SellerProtocol;
import io.bisq.network.p2p.NodeAddress;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public abstract class SellerTrade extends Trade {
    SellerTrade(Offer offer,
                Coin tradeAmount,
                Coin txFee,
                Coin takerFee,
                boolean isCurrencyForTakerFeeBtc,
                long tradePrice,
                NodeAddress tradingPeerNodeAddress,
                Storage<? extends TradableList> storage,
                BtcWalletService btcWalletService) {
        super(offer, tradeAmount, txFee, takerFee, isCurrencyForTakerFeeBtc, tradePrice,
                tradingPeerNodeAddress, storage, btcWalletService);
    }

    SellerTrade(Offer offer,
                Coin txFee,
                Coin takeOfferFee,
                boolean isCurrencyForTakerFeeBtc,
                Storage<? extends TradableList> storage,
                BtcWalletService btcWalletService) {
        super(offer, txFee, takeOfferFee, isCurrencyForTakerFeeBtc, storage, btcWalletService);
    }

    public void onFiatPaymentReceived(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        checkArgument(tradeProtocol instanceof SellerProtocol, "tradeProtocol NOT instanceof SellerProtocol");
        ((SellerProtocol) tradeProtocol).onFiatPaymentReceived(resultHandler, errorMessageHandler);
    }

    @Override
    public Coin getPayoutAmount() {
        return getOffer().getSellerSecurityDeposit();
    }
}

