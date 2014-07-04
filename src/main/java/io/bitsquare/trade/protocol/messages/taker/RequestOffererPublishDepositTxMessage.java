package io.bitsquare.trade.protocol.messages.taker;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.msg.TradeMessage;
import java.io.Serializable;

public class RequestOffererPublishDepositTxMessage implements Serializable, TradeMessage
{
    private static final long serialVersionUID = 2179683654379803071L;
    private final String tradeId;
    private BankAccount bankAccount;
    private String accountID;
    private String takerMessagePubKey;
    private String signedTakerDepositTxAsHex;
    private String txScriptSigAsHex;
    private String txConnOutAsHex;
    private String contractAsJson;
    private String takerContractSignature;
    private String takerPayoutAddress;
    private long takerTxOutIndex;


    private long offererTxOutIndex;

    public RequestOffererPublishDepositTxMessage(String tradeId,
                                                 BankAccount bankAccount,
                                                 String accountID,
                                                 String takerMessagePubKey,
                                                 String signedTakerDepositTxAsHex,
                                                 String txScriptSigAsHex,
                                                 String txConnOutAsHex,
                                                 String contractAsJson,
                                                 String takerContractSignature,
                                                 String takerPayoutAddress,
                                                 long takerTxOutIndex,
                                                 long offererTxOutIndex)
    {

        this.tradeId = tradeId;
        this.bankAccount = bankAccount;
        this.accountID = accountID;
        this.takerMessagePubKey = takerMessagePubKey;
        this.signedTakerDepositTxAsHex = signedTakerDepositTxAsHex;
        this.txScriptSigAsHex = txScriptSigAsHex;
        this.txConnOutAsHex = txConnOutAsHex;
        this.contractAsJson = contractAsJson;
        this.takerContractSignature = takerContractSignature;
        this.takerPayoutAddress = takerPayoutAddress;
        this.takerTxOutIndex = takerTxOutIndex;
        this.offererTxOutIndex = offererTxOutIndex;
    }


    @Override
    public String getTradeId()
    {
        return tradeId;
    }

    public long getOffererTxOutIndex()
    {
        return offererTxOutIndex;
    }

    public BankAccount getBankAccount()
    {
        return bankAccount;
    }

    public String getAccountId()
    {
        return accountID;
    }

    public String getTakerMessagePubKey()
    {
        return takerMessagePubKey;
    }

    public String getSignedTakerDepositTxAsHex()
    {
        return signedTakerDepositTxAsHex;
    }

    public String getTxScriptSigAsHex()
    {
        return txScriptSigAsHex;
    }

    public String getTxConnOutAsHex()
    {
        return txConnOutAsHex;
    }

    public String getContractAsJson()
    {
        return contractAsJson;
    }

    public String getTakerContractSignature()
    {
        return takerContractSignature;
    }

    public String getTakerPayoutAddress()
    {
        return takerPayoutAddress;
    }

    public long getTakerTxOutIndex()
    {
        return takerTxOutIndex;
    }

}
