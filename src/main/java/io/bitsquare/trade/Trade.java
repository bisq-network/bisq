package io.bitsquare.trade;

import com.google.bitcoin.core.Transaction;
import javafx.beans.property.SimpleBooleanProperty;

import java.io.Serializable;
import java.math.BigInteger;

public class Trade implements Serializable
{
    private static final long serialVersionUID = -8275323072940974077L;

    transient private final SimpleBooleanProperty depositTxChangedProperty = new SimpleBooleanProperty();
    transient private final SimpleBooleanProperty contractChangedProperty = new SimpleBooleanProperty();

    private Offer offer;
    private String takeOfferFeeTxID;
    private BigInteger tradeAmount;
    private Contract contract;
    private String contractAsJson;
    private String takerSignature;
    private Transaction depositTransaction;

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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getUid()
    {
        return offer.getUid();
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

    public SimpleBooleanProperty getDepositTxChangedProperty()
    {
        return depositTxChangedProperty;
    }

    public SimpleBooleanProperty getContractChangedProperty()
    {
        return contractChangedProperty;
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
                '}';
    }


}
