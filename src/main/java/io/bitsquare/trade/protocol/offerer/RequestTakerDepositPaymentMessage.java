package io.bitsquare.trade.protocol.offerer;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.trade.protocol.TradeMessage;
import java.io.Serializable;

public class RequestTakerDepositPaymentMessage implements Serializable, TradeMessage
{
    private static final long serialVersionUID = -3988720410493712913L;

    private final String tradeId;
    private BankAccount bankAccount;
    private String accountID;
    private String offererPubKey;
    private String preparedOffererDepositTxAsHex;
    private long offererTxOutIndex;

    public RequestTakerDepositPaymentMessage(String tradeId, BankAccount bankAccount, String accountID, String offererPubKey, String preparedOffererDepositTxAsHex, long offererTxOutIndex)
    {
        this.tradeId = tradeId;
        this.bankAccount = bankAccount;
        this.accountID = accountID;
        this.offererPubKey = offererPubKey;
        this.preparedOffererDepositTxAsHex = preparedOffererDepositTxAsHex;
        this.offererTxOutIndex = offererTxOutIndex;
    }

    @Override
    public String getTradeId()
    {
        return tradeId;
    }

    public BankAccount getBankAccount()
    {
        return bankAccount;
    }

    public String getAccountId()
    {
        return accountID;
    }

    public String getOffererPubKey()
    {
        return offererPubKey;
    }

    public String getPreparedOffererDepositTxAsHex()
    {
        return preparedOffererDepositTxAsHex;
    }

    public long getOffererTxOutIndex()
    {
        return offererTxOutIndex;
    }
}
