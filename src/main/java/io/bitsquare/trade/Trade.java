package io.bitsquare.trade;

import com.google.bitcoin.core.Transaction;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;

import java.io.Serializable;
import java.math.BigInteger;

public class Trade implements Serializable
{
    public static enum State
    {
        NONE,
        ACCEPTED,
        COMPLETED
    }

    private static final long serialVersionUID = -8275323072940974077L;

    transient private final SimpleBooleanProperty depositTxChangedProperty = new SimpleBooleanProperty();
    transient private final SimpleBooleanProperty payoutTxChangedProperty = new SimpleBooleanProperty();
    transient private final SimpleBooleanProperty contractChangedProperty = new SimpleBooleanProperty();
    transient private final SimpleStringProperty stateChangedProperty = new SimpleStringProperty();

    private Offer offer;
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

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////


    public void setTakeOfferFeeTxID(String takeOfferFeeTxID)
    {
        this.takeOfferFeeTxID = takeOfferFeeTxID;
    }

    public void setTradeAmount(BigInteger tradeAmount)
    {
        this.tradeAmount = tradeAmount;
    }

    public void setContract(Contract contract)
    {
        this.contract = contract;
        contractChangedProperty.set(!contractChangedProperty.get());
    }

    public void setContractAsJson(String contractAsJson)
    {
        this.contractAsJson = contractAsJson;
    }

    public void setContractTakerSignature(String takerSignature)
    {
        this.takerSignature = takerSignature;
    }

    public void setDepositTransaction(Transaction depositTransaction)
    {
        this.depositTransaction = depositTransaction;
        depositTxChangedProperty.set(!depositTxChangedProperty.get());
    }

    public void setState(State state)
    {
        this.state = state;
        stateChangedProperty.set(state.toString());
    }


    public void setPayoutTransaction(Transaction payoutTransaction)
    {
        this.payoutTransaction = payoutTransaction;
        payoutTxChangedProperty.set(!payoutTxChangedProperty.get());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
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

    public BigInteger getTradeAmount()
    {
        return tradeAmount;
    }

    public Contract getContract()
    {
        return contract;
    }

    public String getContractAsJson()
    {
        return contractAsJson;
    }

    public String getTakerSignature()
    {
        return takerSignature;
    }

    public Transaction getDepositTransaction()
    {
        return depositTransaction;
    }

    public Transaction getPayoutTransaction()
    {
        return payoutTransaction;
    }

    public State getState()
    {
        return state;
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

    ///////////////////////////////////////////////////////////////////////////////////////////
    // toString
    ///////////////////////////////////////////////////////////////////////////////////////////


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
}
