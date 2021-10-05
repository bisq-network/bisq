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

package bisq.core.trade.model.bsqswap;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.TradePhase;
import bisq.core.trade.model.TradeState;
import bisq.core.trade.protocol.ProtocolModel;
import bisq.core.trade.protocol.Provider;
import bisq.core.trade.protocol.bsqswap.BsqSwapProtocolModel;
import bisq.core.trade.protocol.bsqswap.BsqSwapTradePeer;

import bisq.network.p2p.NodeAddress;

import bisq.common.proto.ProtoUtil;
import bisq.common.util.MathUtils;
import bisq.common.util.Utilities;

import com.google.protobuf.Message;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.Date;
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

        @Override
        public TradePhase getTradePhase() {
            // todo: do we want a phase? is null ok to return?
            return null;
        }
    }

    @Getter
    private final String uid;
    @Getter
    private final Offer offer;
    @Getter
    private final long amount;
    @Getter
    private final long takeOfferDate;
    @Getter
    private final NodeAddress peerNodeAddress;
    @Getter
    private final long txFeePerVbyte;
    @Getter
    private final long makerFee;
    @Getter
    private final long takerFee;
    @Getter
    private final BsqSwapProtocolModel bsqSwapProtocolModel;

    @Getter
    private State state;

    @Nullable
    private String errorMessage;
    @Getter
    @Nullable
    private String txId;

    @Nullable
    transient private Volume volume;
    @Nullable
    transient private Transaction transaction;
    transient final private ObjectProperty<State> stateProperty = new SimpleObjectProperty<>(state);
    transient final private StringProperty errorMessageProperty = new SimpleStringProperty();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected BsqSwapTrade(String uid,
                           Offer offer,
                           Coin amount,
                           long takeOfferDate,
                           NodeAddress peerNodeAddress,
                           long txFeePerVbyte,
                           long makerFee,
                           long takerFee,
                           BsqSwapProtocolModel bsqSwapProtocolModel,
                           @Nullable String errorMessage,
                           State state,
                           @Nullable String txId) {
        this.uid = uid;
        this.offer = offer;
        this.amount = amount.value;
        this.takeOfferDate = takeOfferDate;
        this.peerNodeAddress = peerNodeAddress;
        this.txFeePerVbyte = txFeePerVbyte;
        this.makerFee = makerFee;
        this.takerFee = takerFee;
        this.bsqSwapProtocolModel = bsqSwapProtocolModel;
        this.errorMessage = errorMessage;
        this.state = state;
        this.txId = txId;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Message toProtoMessage() {
        protobuf.BsqSwapTrade.Builder builder = protobuf.BsqSwapTrade.newBuilder()
                .setUid(uid)
                .setOffer(offer.toProtoMessage())
                .setAmount(amount)
                .setTakeOfferDate(takeOfferDate)
                .setMiningFeePerByte(txFeePerVbyte)
                .setMakerFee(makerFee)
                .setTakerFee(takerFee)
                .setBsqSwapProtocolModel(bsqSwapProtocolModel.toProtoMessage())
                .setState(State.toProtoMessage(state))
                .setPeerNodeAddress(peerNodeAddress.toProtoMessage());
        Optional.ofNullable(errorMessage).ifPresent(builder::setErrorMessage);
        Optional.ofNullable(txId).ifPresent(builder::setTxId);
        return builder.build();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TradeModel implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onComplete() {
    }

    @Override
    public BsqSwapTrade.State getTradeState() {
        return state;
    }

    @Override
    public TradePhase getTradePhase() {
        return state.getTradePhase();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TradeModel implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(Provider serviceProvider) {
    }

    @Override
    public ProtocolModel<BsqSwapTradePeer> getTradeProtocolModel() {
        return bsqSwapProtocolModel;
    }

    @Override
    public NodeAddress getTradingPeerNodeAddress() {
        return peerNodeAddress;
    }

    @Override
    public String getStateInfo() {
        if (stateProperty.get() == null) {
            return State.FAILED.toString();
        }
        return stateProperty().get().toString();
    }

    @Override
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        errorMessageProperty.set(errorMessage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Date getDate() {
        return new Date(getTakeOfferDate());
    }

    @Override
    public String getId() {
        return offer.getId();
    }

    @Override
    public String getShortId() {
        return Utilities.getShortId(getId());
    }

    @Override
    public boolean isCompleted() {
        return state == State.COMPLETED;
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

    public ReadOnlyStringProperty errorMessageProperty() {
        return errorMessageProperty;
    }

    public boolean hasFailed() {
        return errorMessageProperty().get() != null;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessageProperty.get();
    }

    public Price getPrice() {
        return Price.valueOf(offer.getCurrencyCode(), offer.getFixedPrice());
    }

    //todo Not sure if that delivers the value as expected... -> getBsqTradeAmount
    public Volume getTradeVolume() {
        if (volume == null) {
            try {
                volume = getPrice().getVolumeByAmount(Coin.valueOf(amount));
            } catch (Throwable e) {
                log.error(e.toString());
                return null;
            }
        }
        return volume;
    }

    public long getBsqTradeAmount() {
        // We treat BSQ as altcoin with smallest unit exponent 8 but we use 2 instead.
        // To avoid a larger refactoring of the monetary domain we just hack in the conversion here
        // by removing the last 6 digits.
        return MathUtils.roundDoubleToLong(MathUtils.scaleDownByPowerOf10(getTradeVolume().getValue(), 6));
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
