package io.bitsquare.trade.protocol.offerer;

import io.bitsquare.msg.TradeMessage;
import java.io.Serializable;

public class RespondToTakeOfferRequestMessage implements Serializable, TradeMessage
{
    private static final long serialVersionUID = 6177387534087739018L;
    private final String tradeId;
    private boolean takeOfferRequestAccepted;

    public RespondToTakeOfferRequestMessage(String tradeId, boolean takeOfferRequestAccepted)
    {
        this.tradeId = tradeId;
        this.takeOfferRequestAccepted = takeOfferRequestAccepted;
    }

    @Override
    public String getTradeId()
    {
        return tradeId;
    }

    public boolean isTakeOfferRequestAccepted()
    {
        return takeOfferRequestAccepted;
    }
}
