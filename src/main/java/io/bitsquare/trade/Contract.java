package io.bitsquare.trade;

import io.bitsquare.user.User;

import java.util.UUID;

public class Contract
{
    private User taker;
    private Trade trade;
    private String takerPubKey;
    private String offererPubKey;

    public Contract(User taker, Trade trade, String takerPubKey)
    {
        this.taker = taker;
        this.trade = trade;
        this.takerPubKey = takerPubKey;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setOffererPubKey(String offererPubKey)
    {
        this.offererPubKey = offererPubKey;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public UUID getUid()
    {
        return trade.getUid();
    }

    public User getTaker()
    {
        return taker;
    }

    public String getTakerPubKey()
    {
        return takerPubKey;
    }

    public Trade getTrade()
    {
        return trade;
    }

    public String getOffererPubKey()
    {
        return offererPubKey;
    }

}
