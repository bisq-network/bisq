package io.bitsquare.trade;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.Transaction;
import java.io.Serializable;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;

public class Trade implements Serializable
{
    private static final long serialVersionUID = -8275323072940974077L;

    private final Offer offer;
    private String takeOfferFeeTxID;
    private Coin tradeAmount;
    private Contract contract;
    private String contractAsJson;
    private String takerSignature;
    private Transaction depositTransaction;
    private Transaction payoutTransaction;
    private State state = State.OPEN;

    // The Property fields are not serialized and therefore not initialized when read from disc.
    // We need to access then with the getter to be sure it is not null.
    // Don't access them directly, use the getter method
    transient private SimpleBooleanProperty _depositTxChangedProperty;
    transient private SimpleBooleanProperty _payoutTxChangedProperty;
    transient private SimpleBooleanProperty _contractChangedProperty;
    transient private SimpleStringProperty _stateChangedProperty;

    public Trade(Offer offer)
    {
        this.offer = offer;

        _depositTxChangedProperty = new SimpleBooleanProperty();
        _payoutTxChangedProperty = new SimpleBooleanProperty();
        _contractChangedProperty = new SimpleBooleanProperty();
        _stateChangedProperty = new SimpleStringProperty();
    }

    public double getTradeVolume()
    {
        return offer.getVolumeForCoin(tradeAmount);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setContractTakerSignature(String takerSignature)
    {
        this.takerSignature = takerSignature;
    }

    public void setTakeOfferFeeTxID(String takeOfferFeeTxID)
    {
        this.takeOfferFeeTxID = takeOfferFeeTxID;
    }

    public Coin getTradeAmount()
    {
        return tradeAmount;
    }

    public void setTradeAmount(Coin tradeAmount)
    {
        this.tradeAmount = tradeAmount;
    }

    public Contract getContract()
    {
        return contract;
    }

    public void setContract(Contract contract)
    {
        this.contract = contract;
        _contractChangedProperty.set(!_contractChangedProperty.get());
    }

    public String getId()
    {
        return offer.getId();
    }

    public Offer getOffer()
    {
        return offer;
    }

    public String getTakeOfferFeeTxId()
    {
        return takeOfferFeeTxID;
    }

    public String getContractAsJson()
    {
        return contractAsJson;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setContractAsJson(String contractAsJson)
    {
        this.contractAsJson = contractAsJson;
    }

    public String getTakerSignature()
    {
        return takerSignature;
    }

    public Transaction getDepositTransaction()
    {
        return depositTransaction;
    }

    public void setDepositTransaction(Transaction depositTransaction)
    {
        this.depositTransaction = depositTransaction;
        depositTxChangedProperty().set(!depositTxChangedProperty().get());
    }

    public Transaction getPayoutTransaction()
    {
        return payoutTransaction;
    }

    public void setPayoutTransaction(Transaction payoutTransaction)
    {
        this.payoutTransaction = payoutTransaction;
        payoutTxChangedProperty().set(!payoutTxChangedProperty().get());
    }

    public State getState()
    {
        return state;
    }

    public void setState(State state)
    {
        this.state = state;
        stateChangedProperty().set(state.toString());
    }

    // The Property fields are not serialized and therefore not initialized when read from disc.
    // We need to access then with the getter to be sure it is not null.
    public SimpleBooleanProperty depositTxChangedProperty()
    {
        if (_depositTxChangedProperty == null) _depositTxChangedProperty = new SimpleBooleanProperty();

        return _depositTxChangedProperty;
    }


    public SimpleBooleanProperty contractChangedProperty()
    {
        if (_contractChangedProperty == null) _contractChangedProperty = new SimpleBooleanProperty();
        return _contractChangedProperty;
    }


    public SimpleBooleanProperty payoutTxChangedProperty()
    {
        if (_payoutTxChangedProperty == null) _payoutTxChangedProperty = new SimpleBooleanProperty();
        return _payoutTxChangedProperty;
    }


    public SimpleStringProperty stateChangedProperty()
    {
        if (_stateChangedProperty == null) _stateChangedProperty = new SimpleStringProperty();
        return _stateChangedProperty;
    }

    public Coin getCollateralAmount()
    {
        return tradeAmount.divide((long) (1d / offer.getCollateral()));
    }


    @Override
    public String toString()
    {
        return "Trade{" +
                "offer=" + offer +
                ", takeOfferFeeTxID='" + takeOfferFeeTxID + '\'' +
                ", tradeAmount=" + tradeAmount +
                ", contract=" + contract +
                ", contractAsJson='" + contractAsJson + '\'' +
                ", takerSignature='" + takerSignature + '\'' +
                ", depositTransaction=" + depositTransaction +
                ", state=" + state +
                '}';
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // toString
    ///////////////////////////////////////////////////////////////////////////////////////////


    public static enum State
    {
        OPEN,
        ACCEPTED,
        COMPLETED
    }
}
