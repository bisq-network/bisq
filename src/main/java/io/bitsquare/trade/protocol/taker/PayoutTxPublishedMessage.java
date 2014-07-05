package io.bitsquare.trade.protocol.taker;

import io.bitsquare.msg.TradeMessage;
import java.io.Serializable;

public class PayoutTxPublishedMessage implements Serializable, TradeMessage
{
    private static final long serialVersionUID = 1288653559218403873L;
    private final String tradeId;
    private String payoutTxAsHex;

    public PayoutTxPublishedMessage(String tradeId, String payoutTxAsHex)
    {
        this.tradeId = tradeId;
        this.payoutTxAsHex = payoutTxAsHex;
    }

    @Override
    public String getTradeId()
    {
        return tradeId;
    }

    public String getPayoutTxAsHex()
    {
        return payoutTxAsHex;
    }
}
