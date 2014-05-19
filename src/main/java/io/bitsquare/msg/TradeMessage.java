package io.bitsquare.msg;

import io.bitsquare.bank.BankAccount;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.UUID;

//TODO refactor
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
    private String takerPayoutAddress;
    private TradeMessageType type;
    private String depositTxID;
    private String depositTxAsHex;

    private String offererSignatureR;
    private String offererSignatureS;
    private BigInteger offererPaybackAmount;
    private BigInteger takerPaybackAmount;
    private String offererPayoutAddress;
    private BigInteger tradeAmount;
    private String takeOfferFeeTxID;
    private String takerMultiSigPubKey;
    private String offerUID;
    private BankAccount bankAccount;
    private String accountID;
    private String offererPubKey;
    private String preparedOffererDepositTxAsHex;


    private String payoutTxAsHex;

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
                        String takerContractSignature,
                        String takerPayoutAddress)
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
        this.takerPayoutAddress = takerPayoutAddress;

        uid = UUID.randomUUID().toString();
    }

    public TradeMessage(TradeMessageType type, String offerUID, String depositTxAsHex)
    {
        this.offerUID = offerUID;
        this.type = type;
        this.depositTxAsHex = depositTxAsHex;

        uid = UUID.randomUUID().toString();
    }

    // 3.10

    public TradeMessage(TradeMessageType type, String offerUID,
                        String depositTxAsHex,
                        String offererSignatureR,
                        String offererSignatureS,
                        BigInteger offererPaybackAmount,
                        BigInteger takerPaybackAmount,
                        String offererPayoutAddress)
    {
        this.offerUID = offerUID;
        this.type = type;
        this.depositTxAsHex = depositTxAsHex;
        this.offererSignatureR = offererSignatureR;
        this.offererSignatureS = offererSignatureS;
        this.offererPaybackAmount = offererPaybackAmount;
        this.takerPaybackAmount = takerPaybackAmount;
        this.offererPayoutAddress = offererPayoutAddress;

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

    public String getTakerPayoutAddress()
    {
        return takerPayoutAddress;
    }

    public String getOffererPayoutAddress()
    {
        return offererPayoutAddress;
    }

    public String getOffererSignatureR()
    {
        return offererSignatureR;
    }

    public String getOffererSignatureS()
    {
        return offererSignatureS;
    }

    public BigInteger getOffererPaybackAmount()
    {
        return offererPaybackAmount;
    }

    public BigInteger getTakerPaybackAmount()
    {
        return takerPaybackAmount;
    }

    public String getDepositTxAsHex()
    {
        return depositTxAsHex;
    }

    public void setPayoutTxAsHex(String payoutTxAsHex)
    {
        this.payoutTxAsHex = payoutTxAsHex;
    }

    public String getPayoutTxAsHex()
    {
        return payoutTxAsHex;
    }

}
