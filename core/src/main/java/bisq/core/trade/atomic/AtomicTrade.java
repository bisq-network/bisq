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

package bisq.core.trade.atomic;

import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;
import bisq.core.proto.CoreProtoResolver;
import bisq.core.trade.TradeModel;
import bisq.core.trade.protocol.BsqSwapProtocolModel;
import bisq.core.trade.protocol.TradeProtocolModel;

import bisq.network.p2p.NodeAddress;

import bisq.common.proto.ProtoUtil;
import bisq.common.util.Utilities;

import com.google.protobuf.Message;

import org.bitcoinj.core.Coin;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.Date;
import java.util.Optional;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public abstract class AtomicTrade extends TradeModel {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enums
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum State {
        PREPARATION,
        TX_PUBLISHED,
        TX_CONFIRMED,
        FAILED;

        public static AtomicTrade.State fromProto(protobuf.AtomicTrade.State state) {
            return ProtoUtil.enumFromProto(AtomicTrade.State.class, state.name());
        }

        public static protobuf.AtomicTrade.State toProtoMessage(AtomicTrade.State state) {
            return protobuf.AtomicTrade.State.valueOf(state.name());
        }
    }

    // Protobuf fields
    @Getter
    @Setter
    private String uid;
    @Getter
    private final Offer offer;
    @Getter
    @Setter
    @Nullable
    protected String txId;
    @Getter
    @Setter
    private Coin amount;
    private final long price;
    @Getter
    private final long takeOfferDate;
    @Nullable
    @Getter
    @Setter
    private NodeAddress peerNodeAddress;
    @Getter
    private final long miningFeePerByte;
    @Getter
    private final long makerFee;
    @Getter
    private final long takerFee;
    @Getter
    private final BsqSwapProtocolModel bsqSwapProtocolModel;
    @Nullable
    private String errorMessage;
    @Getter
    private State state;

    transient final private ObjectProperty<AtomicTrade.State> stateProperty = new SimpleObjectProperty<>(state);
    transient final private StringProperty errorMessageProperty = new SimpleStringProperty();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected AtomicTrade(String uid,
                          Offer offer,
                          Coin amount,
                          long price,
                          long takeOfferDate,
                          @Nullable NodeAddress peerNodeAddress,
                          long miningFeePerByte,
                          long makerFee,
                          long takerFee,
                          BsqSwapProtocolModel bsqSwapProtocolModel,
                          @Nullable String errorMessage,
                          State state) {
        this.uid = uid;
        this.offer = offer;
        this.amount = amount;
        this.price = price;
        this.takeOfferDate = takeOfferDate;
        this.peerNodeAddress = peerNodeAddress;
        this.miningFeePerByte = miningFeePerByte;
        this.makerFee = makerFee;
        this.takerFee = takerFee;
        this.bsqSwapProtocolModel = bsqSwapProtocolModel;
        this.errorMessage = errorMessage;
        this.state = state;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Message toProtoMessage() {
        protobuf.AtomicTrade.Builder builder = protobuf.AtomicTrade.newBuilder()
                .setUid(uid)
                .setOffer(offer.toProtoMessage())
                .setAmount(amount.getValue())
                .setPrice(price)
                .setTakeOfferDate(takeOfferDate)
                .setMiningFeePerByte(miningFeePerByte)
                .setMakerFee(makerFee)
                .setTakerFee(takerFee)
                .setAtomicProcessModel(bsqSwapProtocolModel.toProtoMessage())
                .setState(State.toProtoMessage(state));
        Optional.ofNullable(txId).ifPresent(builder::setTxId);
        Optional.ofNullable(peerNodeAddress).ifPresent(e -> builder.setPeerNodeAddress(
                peerNodeAddress.toProtoMessage()));
        Optional.ofNullable(errorMessage).ifPresent(builder::setErrorMessage);

        return builder.build();
    }

    public static AtomicTrade fromProto(AtomicTrade atomicTrade, protobuf.AtomicTrade proto,
                                        CoreProtoResolver coreProtoResolver) {
        return atomicTrade;
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
    public TradeProtocolModel getTradeProtocolModel() {
        return bsqSwapProtocolModel;
    }

    @Override
    public NodeAddress getTradingPeerNodeAddress() {
        return getPeerNodeAddress();
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
        return state == State.TX_CONFIRMED;
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
        return Price.valueOf(offer.getCurrencyCode(), price);
    }

    @Nullable
    public Volume getTradeVolume() {
        try {
            if (getAmount() != null && getPrice() != null) {
                return getPrice().getVolumeByAmount(getAmount());
            } else {
                return null;
            }
        } catch (Throwable ignore) {
            return null;
        }
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

}
