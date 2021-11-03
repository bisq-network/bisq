package bisq.core.trade.model;

import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;
import bisq.core.trade.protocol.ProtocolModel;
import bisq.core.trade.protocol.Provider;
import bisq.core.trade.protocol.TradePeer;

import bisq.network.p2p.NodeAddress;

import bisq.common.taskrunner.Model;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;

import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;

import javax.annotation.Nullable;

public abstract class TradeModel implements Tradable, Model {
    @Getter
    protected final String uid;
    protected final Offer offer;
    @Getter
    @Setter
    @Nullable
    protected NodeAddress tradingPeerNodeAddress;
    @Getter
    @Setter
    protected long takeOfferDate;
    @Nullable
    @Getter
    protected String errorMessage;
    transient final private StringProperty errorMessageProperty = new SimpleStringProperty();


    public TradeModel(String uid, Offer offer) {
        this(uid, offer, new Date().getTime(), null, null);
    }

    public TradeModel(String uid,
                      Offer offer,
                      long takeOfferDate,
                      @Nullable NodeAddress tradingPeerNodeAddress,
                      @Nullable String errorMessage) {
        this.uid = uid;
        this.offer = offer;
        this.tradingPeerNodeAddress = tradingPeerNodeAddress;
        this.takeOfferDate = takeOfferDate;
        setErrorMessage(errorMessage);
    }

    public void initialize(Provider serviceProvider) {
    }

    public abstract boolean isCompleted();

    public abstract ProtocolModel<? extends TradePeer> getTradeProtocolModel();

    public abstract TradeState getTradeState();

    public abstract TradePhase getTradePhase();

    public abstract long getAmountAsLong();

    public abstract Coin getAmount();

    @Nullable
    public abstract Volume getVolume();

    public abstract Price getPrice();

    public abstract Coin getTxFee();

    public abstract Coin getTakerFee();

    public abstract Coin getMakerFee();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Tradable implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Offer getOffer() {
        return offer;
    }

    @Override
    public Date getDate() {
        return new Date(takeOfferDate);
    }

    @Override
    public String getId() {
        return offer.getId();
    }

    @Override
    public String getShortId() {
        return Utilities.getShortId(getId());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        errorMessageProperty.set(errorMessage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ReadOnlyStringProperty errorMessageProperty() {
        return errorMessageProperty;
    }
}
