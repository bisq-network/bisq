package io.bitsquare.trade.payment.offerer.messages;

import io.bitsquare.msg.TradeMessage;
import java.io.Serializable;

public class RejectTakeOfferRequestMessage implements Serializable, TradeMessage
{
    private static final long serialVersionUID = -8088557759642128139L;
    private final String tradeId;

    public RejectTakeOfferRequestMessage(String tradeId)
    {
        this.tradeId = tradeId;
    }

    @Override
    public String getTradeId()
    {
        return tradeId;
    }
}
