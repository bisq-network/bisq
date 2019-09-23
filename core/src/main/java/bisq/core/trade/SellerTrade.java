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

package bisq.core.trade;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.Offer;
import bisq.core.trade.protocol.SellerProtocol;

import bisq.network.p2p.NodeAddress;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.storage.Storage;

import org.bitcoinj.core.Coin;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

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
                @Nullable NodeAddress arbitratorNodeAddress,
                @Nullable NodeAddress mediatorNodeAddress,
                @Nullable NodeAddress refundAgentNodeAddress,
                Storage<? extends TradableList> storage,
                BtcWalletService btcWalletService) {
        super(offer,
                tradeAmount,
                txFee,
                takerFee,
                isCurrencyForTakerFeeBtc,
                tradePrice,
                tradingPeerNodeAddress,
                arbitratorNodeAddress,
                mediatorNodeAddress,
                refundAgentNodeAddress,
                storage,
                btcWalletService);
    }

    SellerTrade(Offer offer,
                Coin txFee,
                Coin takeOfferFee,
                boolean isCurrencyForTakerFeeBtc,
                @Nullable NodeAddress arbitratorNodeAddress,
                @Nullable NodeAddress mediatorNodeAddress,
                @Nullable NodeAddress refundAgentNodeAddress,
                Storage<? extends TradableList> storage,
                BtcWalletService btcWalletService) {
        super(offer,
                txFee,
                takeOfferFee,
                isCurrencyForTakerFeeBtc,
                arbitratorNodeAddress,
                mediatorNodeAddress,
                refundAgentNodeAddress,
                storage,
                btcWalletService);
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

