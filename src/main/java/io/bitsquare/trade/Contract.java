package io.bitsquare.trade;

import io.bitsquare.user.User;

import java.util.UUID;

public class Contract
{
    private User taker;
    private User offerer;
    private String offererPubKey;
    private Trade trade;
    private String takerPubKey;


    public Contract(Trade trade, String takerPubKey)
    {
        this.trade = trade;
        this.takerPubKey = takerPubKey;
    }

    public UUID getUid()
    {
        return trade.getUid();
    }

    public void setTaker(User taker)
    {
        this.taker = taker;
    }

    public User getTaker()
    {
        return taker;
    }

    public User getOfferer()
    {
        return offerer;
    }

    public void setOfferer(User offerer)
    {
        this.offerer = offerer;
    }


    public String getTakerPubKey()
    {
        return takerPubKey;
    }

    public void setTakerPubKey(String takerPubKey)
    {
        this.takerPubKey = takerPubKey;
    }

    public String getOffererPubKey()
    {
        return offererPubKey;
    }

    public void setOffererPubKey(String offererPubKey)
    {
        this.offererPubKey = offererPubKey;
    }


    public Trade getTrade()
    {
        return trade;
    }

    public void setTrade(Trade trade)
    {
        this.trade = trade;
    }
}
