package io.bitsquare.trade.protocol.offerer;

import io.bitsquare.trade.protocol.TradeMessage;
import java.io.Serializable;

public class DepositTxPublishedMessage implements Serializable, TradeMessage
{

    private static final long serialVersionUID = -1532231540167406581L;
    private final String tradeId;

    private String depositTxAsHex;

    public DepositTxPublishedMessage(String tradeId, String depositTxAsHex)
    {
        this.tradeId = tradeId;
        this.depositTxAsHex = depositTxAsHex;
    }

    @Override
    public String getTradeId()
    {
        return tradeId;
    }

    public String getDepositTxAsHex()
    {
        return depositTxAsHex;
    }
}
