package io.bitsquare.trade;

import com.google.bitcoin.core.Transaction;
import java.io.Serializable;
import java.math.BigInteger;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;

public class Trade implements Serializable
{
    private static final long serialVersionUID = -8275323072940974077L;
    transient private final SimpleBooleanProperty depositTxChangedProperty = new SimpleBooleanProperty();
    transient private final SimpleBooleanProperty payoutTxChangedProperty = new SimpleBooleanProperty();
    transient private final SimpleBooleanProperty contractChangedProperty = new SimpleBooleanProperty();
    transient private final SimpleStringProperty stateChangedProperty = new SimpleStringProperty();
    private final Offer offer;
    private String takeOfferFeeTxID;
    private BigInteger tradeAmount;
    private Contract contract;
    private String contractAsJson;
    private String takerSignature;
    private Transaction depositTransaction;
    private Transaction payoutTransaction;
    private State state = State.NONE;

    public Trade(Offer offer)
    {
        this.offer = offer;
    }

    public void setContractTakerSignature(String takerSignature)
    {
        this.takerSignature = takerSignature;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getId()
    {
        return offer.getId();
    }

    public Offer getOffer()
    {
        return offer;
    }

    public String getTakeOfferFeeTxID()
    {
        return takeOfferFeeTxID;
    }

    public void setTakeOfferFeeTxID(String takeOfferFeeTxID)
    {
        this.takeOfferFeeTxID = takeOfferFeeTxID;
    }

    public BigInteger getTradeAmount()
    {
        return tradeAmount;
    }

    public void setTradeAmount(BigInteger tradeAmount)
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
        contractChangedProperty.set(!contractChangedProperty.get());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getContractAsJson()
    {
        return contractAsJson;
    }

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
        depositTxChangedProperty.set(!depositTxChangedProperty.get());
    }

    public Transaction getPayoutTransaction()
    {
        return payoutTransaction;
    }

    public void setPayoutTransaction(Transaction payoutTransaction)
    {
        this.payoutTransaction = payoutTransaction;
        payoutTxChangedProperty.set(!payoutTxChangedProperty.get());
    }

    public State getState()
    {
        return state;
    }

    public void setState(State state)
    {
        this.state = state;
        stateChangedProperty.set(state.toString());
    }

    public SimpleBooleanProperty getDepositTxChangedProperty()
    {
        return depositTxChangedProperty;
    }

    public SimpleBooleanProperty getContractChangedProperty()
    {
        return contractChangedProperty;
    }

    public SimpleBooleanProperty getPayoutTxChangedProperty()
    {
        return payoutTxChangedProperty;
    }

    public SimpleStringProperty getStateChangedProperty()
    {
        return stateChangedProperty;
    }

    public BigInteger getCollateralAmount()
    {
        return getTradeAmount().multiply(BigInteger.valueOf(getOffer().getCollateral())).divide(BigInteger.valueOf(100));
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
        NONE,
        ACCEPTED,
        COMPLETED
    }
}
