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

package bisq.core.trade.model.bsq_swap;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;
import bisq.core.trade.bsq_swap.BsqSwapCalculation;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.TradePhase;
import bisq.core.trade.model.TradeState;
import bisq.core.trade.protocol.ProtocolModel;
import bisq.core.trade.protocol.bsq_swap.model.BsqSwapProtocolModel;
import bisq.core.trade.protocol.bsq_swap.model.BsqSwapTradePeer;

import bisq.network.p2p.NodeAddress;

import bisq.common.proto.ProtoUtil;

import com.google.protobuf.Message;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Optional;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public abstract class BsqSwapTrade extends TradeModel {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enums
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum State implements TradeState {
        PREPARATION,
        COMPLETED,
        FAILED;

        public static State fromProto(protobuf.BsqSwapTrade.State state) {
            return ProtoUtil.enumFromProto(State.class, state.name());
        }

        public static protobuf.BsqSwapTrade.State toProtoMessage(State state) {
            return protobuf.BsqSwapTrade.State.valueOf(state.name());
        }
    }


    private final long amountAsLong;
    @Getter
    private final long txFeePerVbyte;
    private final long makerFeeAsLong;
    @Getter
    private final long takerFeeAsLong;
    @Getter
    private final BsqSwapProtocolModel bsqSwapProtocolModel;

    @Getter
    private State state;

    @Getter
    @Nullable
    private String txId;

    @Nullable
    transient private Volume volume;
    @Nullable
    transient private Transaction transaction;
    transient final private ObjectProperty<State> stateProperty = new SimpleObjectProperty<>(state);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected BsqSwapTrade(String uid,
                           Offer offer,
                           Coin amount,
                           long takeOfferDate,
                           NodeAddress tradingPeerNodeAddress,
                           long txFeePerVbyte,
                           long makerFeeAsLong,
                           long takerFeeAsLong,
                           BsqSwapProtocolModel bsqSwapProtocolModel,
                           @Nullable String errorMessage,
                           State state,
                           @Nullable String txId) {
        super(uid, offer, takeOfferDate, tradingPeerNodeAddress, errorMessage);
        this.amountAsLong = amount.value;
        this.txFeePerVbyte = txFeePerVbyte;
        this.makerFeeAsLong = makerFeeAsLong;
        this.takerFeeAsLong = takerFeeAsLong;
        this.bsqSwapProtocolModel = bsqSwapProtocolModel;
        this.state = state;
        this.txId = txId;

        stateProperty.set(state);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Message toProtoMessage() {
        protobuf.BsqSwapTrade.Builder builder = protobuf.BsqSwapTrade.newBuilder()
                .setUid(uid)
                .setOffer(offer.toProtoMessage())
                .setAmount(amountAsLong)
                .setTakeOfferDate(takeOfferDate)
                .setMiningFeePerByte(txFeePerVbyte)
                .setMakerFee(makerFeeAsLong)
                .setTakerFee(takerFeeAsLong)
                .setBsqSwapProtocolModel(bsqSwapProtocolModel.toProtoMessage())
                .setState(State.toProtoMessage(state))
                .setPeerNodeAddress(tradingPeerNodeAddress.toProtoMessage());
        Optional.ofNullable(errorMessage).ifPresent(builder::setErrorMessage);
        Optional.ofNullable(txId).ifPresent(builder::setTxId);
        return builder.build();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Model implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onComplete() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TradeModel implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ProtocolModel<BsqSwapTradePeer> getTradeProtocolModel() {
        return bsqSwapProtocolModel;
    }

    @Override
    public boolean isCompleted() {
        return state == State.COMPLETED;
    }

    @Override
    public BsqSwapTrade.State getTradeState() {
        return state;
    }

    @Override
    public TradePhase getTradePhase() {
        return state.getTradePhase();
    }

    @Override
    public long getAmountAsLong() {
        return amountAsLong;
    }

    @Override
    public Coin getAmount() {
        return Coin.valueOf(amountAsLong);
    }

    @Override
    @Nullable
    public Volume getVolume() {
        if (volume == null) {
            try {
                volume = getPrice().getVolumeByAmount(Coin.valueOf(amountAsLong));
            } catch (Throwable e) {
                log.error(e.toString());
                return null;
            }
        }
        return volume;
    }

    @Override
    public Price getPrice() {
        return Price.valueOf(offer.getCurrencyCode(), offer.getFixedPrice());
    }

    @Override
    public Coin getTxFee() {
        return Coin.valueOf(bsqSwapProtocolModel.getTxFee());
    }

    public long getMakerFeeAsLong() {
        return makerFeeAsLong;
    }

    @Override
    public Coin getTakerFee() {
        return Coin.valueOf(takerFeeAsLong);
    }

    @Override
    public Coin getMakerFee() {
        return Coin.valueOf(makerFeeAsLong);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setState(State state) {
        if (state.ordinal() < this.state.ordinal()) {
            String message = "Unexpected state change to a previous state.\n" +
                    "Old state is: " + this.state + ". New state is: " + state;
            log.warn(message);
        }

        this.state = state;
        stateProperty.set(state);
    }

    public void applyTransaction(Transaction transaction) {
        this.transaction = transaction;
        txId = transaction.getTxId().toString();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ReadOnlyObjectProperty<State> stateProperty() {
        return stateProperty;
    }

    public boolean hasFailed() {
        return errorMessageProperty().get() != null;
    }

    public long getBsqTradeAmount() {
        Volume volume = getVolume();
        if (volume == null) {
            return 0L;
        }
        return BsqSwapCalculation.getBsqTradeAmount(volume).getValue();
    }

    @Nullable
    public Transaction getTransaction(BsqWalletService bsqWalletService) {
        if (txId == null) {
            return null;
        }
        if (transaction == null) {
            transaction = bsqWalletService.getTransaction(txId);
        }
        return transaction;
    }
}
