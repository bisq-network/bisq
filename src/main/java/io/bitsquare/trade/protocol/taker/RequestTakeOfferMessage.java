package io.bitsquare.trade.protocol.taker;

import io.bitsquare.trade.protocol.TradeMessage;
import java.io.Serializable;

public class RequestTakeOfferMessage implements Serializable, TradeMessage
{
    private static final long serialVersionUID = 4660151440192191798L;
    private final String tradeId;

    public RequestTakeOfferMessage(String tradeId)
    {
        this.tradeId = tradeId;
    }

    @Override
    public String getTradeId()
    {
        return tradeId;
    }


}
