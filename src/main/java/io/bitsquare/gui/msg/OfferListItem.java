package io.bitsquare.gui.msg;

import java.io.Serializable;

/**
 * Wrapper for observable properties used by orderbook table view
 */
public class OfferListItem implements Serializable
{

    private static final long serialVersionUID = 7914481258209700131L;

    private String _offer;
    private String pubKey;
    private String currency;


    public OfferListItem(String offer, String pubKey, String currency)
    {
        _offer = offer;
        this.pubKey = pubKey;
        this.currency = currency;

    }

    public String getOffer()
    {
        return _offer;
    }

    public String getPubKey()
    {
        return pubKey;
    }

    public String getCurrency()
    {
        return currency;
    }
}
