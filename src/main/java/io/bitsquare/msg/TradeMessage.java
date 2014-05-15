package io.bitsquare.msg;

import io.bitsquare.bank.BankAccount;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.UUID;

public class TradeMessage implements Serializable
{
    private static final long serialVersionUID = 7916445031849763995L;

    private String uid;

    private String takerMessagePubKey;
    private String signedTakerDepositTxAsHex;
    private String txScriptSigAsHex;
    private String txConnOutAsHex;
    private String contractAsJson;
    private String takerContractSignature;
    private TradeMessageType type;
    private String depositTxID;
    private BigInteger tradeAmount;
    private String takeOfferFeeTxID;
    private String takerMultiSigPubKey;
    private String offerUID;
    private BankAccount bankAccount;
    private String accountID;
    private String offererPubKey;
    private String preparedOffererDepositTxAsHex;

    public TradeMessage(TradeMessageType type, String offerUID)
    {
        this.offerUID = offerUID;
        this.type = type;

        uid = UUID.randomUUID().toString();
    }

    public TradeMessage(TradeMessageType type, String offerUID, BigInteger tradeAmount, String takeOfferFeeTxID, String takerMultiSigPubKey)
    {
        this.offerUID = offerUID;
        this.type = type;
        this.tradeAmount = tradeAmount;
        this.takeOfferFeeTxID = takeOfferFeeTxID;
        this.takerMultiSigPubKey = takerMultiSigPubKey;

        uid = UUID.randomUUID().toString();
    }

    public TradeMessage(TradeMessageType type, String offerUID, BankAccount bankAccount, String accountID, String offererPubKey, String preparedOffererDepositTxAsHex)
    {
        this.offerUID = offerUID;
        this.type = type;
        this.bankAccount = bankAccount;
        this.accountID = accountID;
        this.offererPubKey = offererPubKey;
        this.preparedOffererDepositTxAsHex = preparedOffererDepositTxAsHex;

        uid = UUID.randomUUID().toString();
    }

    public TradeMessage(TradeMessageType type, String offerUID,
                        BankAccount bankAccount,
                        String accountID,
                        String takerMessagePubKey,
                        String signedTakerDepositTxAsHex,
                        String txScriptSigAsHex,
                        String txConnOutAsHex,
                        String contractAsJson,
                        String takerContractSignature)
    {
        this.offerUID = offerUID;
        this.type = type;
        this.bankAccount = bankAccount;
        this.accountID = accountID;
        this.takerMessagePubKey = takerMessagePubKey;
        this.signedTakerDepositTxAsHex = signedTakerDepositTxAsHex;
        this.txScriptSigAsHex = txScriptSigAsHex;
        this.txConnOutAsHex = txConnOutAsHex;
        this.contractAsJson = contractAsJson;
        this.takerContractSignature = takerContractSignature;

        uid = UUID.randomUUID().toString();
    }

    public TradeMessage(TradeMessageType type, String offerUID, String depositTxID)
    {
        this.offerUID = offerUID;
        this.type = type;
        this.depositTxID = depositTxID;

        uid = UUID.randomUUID().toString();
    }


    public String getUid()
    {
        return uid;
    }

    public TradeMessageType getType()
    {
        return type;
    }

    public String getTakeOfferFeeTxID()
    {
        return takeOfferFeeTxID;
    }

    public String getOfferUID()
    {
        return offerUID;
    }

    public BankAccount getBankAccount()
    {
        return bankAccount;
    }

    public String getAccountID()
    {
        return accountID;
    }

    public String getTakerMultiSigPubKey()
    {
        return takerMultiSigPubKey;
    }

    public String getPreparedOffererDepositTxAsHex()
    {
        return preparedOffererDepositTxAsHex;
    }

    public BigInteger getTradeAmount()
    {
        return tradeAmount;
    }

    public String getTakerMessagePubKey()
    {
        return takerMessagePubKey;
    }

    public String getSignedTakerDepositTxAsHex()
    {
        return signedTakerDepositTxAsHex;
    }

    public String getContractAsJson()
    {
        return contractAsJson;
    }

    public String getTakerContractSignature()
    {
        return takerContractSignature;
    }

    public String getTxScriptSigAsHex()
    {
        return txScriptSigAsHex;
    }

    public String getTxConnOutAsHex()
    {
        return txConnOutAsHex;
    }

    public String getDepositTxID()
    {
        return depositTxID;
    }

    public String getOffererPubKey()
    {
        return offererPubKey;
    }
}
