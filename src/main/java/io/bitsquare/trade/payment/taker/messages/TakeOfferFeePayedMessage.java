package io.bitsquare.trade.payment.taker.messages;

import io.bitsquare.msg.TradeMessage;
import java.io.Serializable;
import java.math.BigInteger;

public class TakeOfferFeePayedMessage implements Serializable, TradeMessage
{
    private static final long serialVersionUID = -5057935061275354312L;
    private final String tradeId;

    private BigInteger tradeAmount;
    private String takeOfferFeeTxID;
    private String takerMultiSigPubKey;

    public TakeOfferFeePayedMessage(String tradeId, String takeOfferFeeTxID, BigInteger tradeAmount, String takerMultiSigPubKey)
    {
        this.tradeId = tradeId;
        this.takeOfferFeeTxID = takeOfferFeeTxID;
        this.tradeAmount = tradeAmount;
        this.takerMultiSigPubKey = takerMultiSigPubKey;
    }

    @Override
    public String getTradeId()
    {
        return tradeId;
    }

    public BigInteger getTradeAmount()
    {
        return tradeAmount;
    }

    public String getTakeOfferFeeTxID()
    {
        return takeOfferFeeTxID;
    }

    public String getTakerMultiSigPubKey()
    {
        return takerMultiSigPubKey;
    }

}
