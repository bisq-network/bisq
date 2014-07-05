package io.bitsquare.trade.protocol.taker;

import io.bitsquare.msg.TradeMessage;
import java.io.Serializable;
import java.math.BigInteger;

public class TakeOfferFeePayedMessage implements Serializable, TradeMessage
{
    private static final long serialVersionUID = -5057935061275354312L;
    private final String tradeId;

    private BigInteger tradeAmount;
    private String takeOfferFeeTxID;
    private String takerPubKey;

    public TakeOfferFeePayedMessage(String tradeId, String takeOfferFeeTxID, BigInteger tradeAmount, String takerPubKey)
    {
        this.tradeId = tradeId;
        this.takeOfferFeeTxID = takeOfferFeeTxID;
        this.tradeAmount = tradeAmount;
        this.takerPubKey = takerPubKey;
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

    public String getTakeOfferFeeTxId()
    {
        return takeOfferFeeTxID;
    }

    public String getTakerPubKey()
    {
        return takerPubKey;
    }

}
