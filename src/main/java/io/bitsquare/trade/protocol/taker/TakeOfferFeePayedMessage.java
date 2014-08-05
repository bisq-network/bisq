package io.bitsquare.trade.protocol.taker;

import com.google.bitcoin.core.Coin;
import io.bitsquare.trade.protocol.TradeMessage;
import java.io.Serializable;

public class TakeOfferFeePayedMessage implements Serializable, TradeMessage
{
    private static final long serialVersionUID = -5057935061275354312L;
    private final String tradeId;

    private Coin tradeAmount;
    private String takeOfferFeeTxID;
    private String takerPubKey;

    public TakeOfferFeePayedMessage(String tradeId, String takeOfferFeeTxID, Coin tradeAmount, String takerPubKey)
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

    public Coin getTradeAmount()
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
