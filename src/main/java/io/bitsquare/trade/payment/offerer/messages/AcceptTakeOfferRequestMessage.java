package io.bitsquare.trade.payment.offerer.messages;

import io.bitsquare.msg.TradeMessage;
import java.io.Serializable;

public class AcceptTakeOfferRequestMessage implements Serializable, TradeMessage
{
    private static final long serialVersionUID = 6177387534087739018L;
    private final String tradeId;

    public AcceptTakeOfferRequestMessage(String tradeId)
    {
        this.tradeId = tradeId;
    }


    @Override
    public String getTradeId()
    {
        return tradeId;
    }
}
