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
import bisq.core.proto.CoreProtoResolver;
import bisq.core.trade.protocol.SellerProtocol;

import bisq.network.p2p.NodeAddress;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.ProtoUtil;
import bisq.common.storage.Storage;

import com.google.protobuf.Message;

import org.bitcoinj.core.Coin;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Optional;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public abstract class SellerTrade extends Trade {

    public enum CancelTradeState {
        RECEIVED_REQUEST,

        REQUEST_ACCEPTED_PAYOUT_TX_PUBLISHED,
        REQUEST_ACCEPTED_MSG_SENT,
        REQUEST_ACCEPTED_MSG_ARRIVED,
        REQUEST_ACCEPTED_MSG_IN_MAILBOX,
        REQUEST_ACCEPTED_MSG_SEND_FAILED,

        REQUEST_REJECTED_MSG_SENT,
        REQUEST_REJECTED_MSG_ARRIVED,
        REQUEST_REJECTED_MSG_IN_MAILBOX,
        REQUEST_REJECTED_MSG_SEND_FAILED
    }

    // Added at v1.3.9
    @Getter
    @Nullable
    public SellerTrade.CancelTradeState cancelTradeState;
    @Getter
    transient final private ObjectProperty<CancelTradeState> cancelTradeStateProperty = new SimpleObjectProperty<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Message toProtoMessage() {
        protobuf.Trade.Builder builder = getBuilder();
        Optional.ofNullable(cancelTradeState).ifPresent(e -> builder.setCancelTradeState(cancelTradeState.name()));
        return builder.build();
    }

    public static Tradable fromProto(SellerTrade trade, protobuf.Trade proto, CoreProtoResolver coreProtoResolver) {
        trade.setCancelTradeState(ProtoUtil.enumFromProto(SellerTrade.CancelTradeState.class, proto.getCancelTradeState()));
        return Trade.fromProto(trade,
                proto,
                coreProtoResolver);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setCancelTradeState(SellerTrade.CancelTradeState cancelTradeState) {
        this.cancelTradeState = cancelTradeState;
        cancelTradeStateProperty.set(cancelTradeState);
        persist();
    }

    public void onFiatPaymentReceived(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        checkArgument(tradeProtocol instanceof SellerProtocol, "tradeProtocol NOT instanceof SellerProtocol");
        ((SellerProtocol) tradeProtocol).onFiatPaymentReceived(resultHandler, errorMessageHandler);
    }

    @Override
    public Coin getPayoutAmount() {
        return getOffer().getSellerSecurityDeposit();
    }

    @Override
    public boolean isCanceled() {
        if (cancelTradeState == null) {
            return false;
        }
        switch (cancelTradeState) {
            case REQUEST_ACCEPTED_PAYOUT_TX_PUBLISHED:
            case REQUEST_ACCEPTED_MSG_SENT:
            case REQUEST_ACCEPTED_MSG_ARRIVED:
            case REQUEST_ACCEPTED_MSG_IN_MAILBOX:
            case REQUEST_ACCEPTED_MSG_SEND_FAILED:
                return true;
            default:
                return false;
        }
    }


    @Override
    public String toString() {
        return "SellerTrade{" +
                "\n     sellersCancelTradeState=" + cancelTradeState +
                ",\n     sellersCancelTradeStateProperty=" + cancelTradeStateProperty +
                "\n} " + super.toString();
    }
}

