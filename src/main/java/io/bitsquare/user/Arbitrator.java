package io.bitsquare.user;

import java.io.Serializable;
import java.math.BigInteger;

public class Arbitrator implements Serializable
{
    private static final long serialVersionUID = -2625059604136756635L;

    private String name;
    private String pubKey;
    private String messageID;
    private String url;


    private double baseFeePercent;
    private double arbitrationFeePercent;
    private BigInteger minArbitrationFee;
    private String uid;

    public Arbitrator(String uid, String name, String pubKey, String messageID, String url, double baseFeePercent, double arbitrationFeePercent, BigInteger minArbitrationFee)
    {
        this.uid = uid;
        this.name = name;
        this.pubKey = pubKey;
        this.messageID = messageID;
        this.url = url;
        this.baseFeePercent = baseFeePercent;
        this.arbitrationFeePercent = arbitrationFeePercent;
        this.minArbitrationFee = minArbitrationFee;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getName()
    {
        return name;
    }

    public String getPubKey()
    {
        return pubKey;
    }

    public String getMessageID()
    {
        return messageID;
    }

    public String getUrl()
    {
        return url;
    }

    public BigInteger getMinArbitrationFee()
    {
        return minArbitrationFee;
    }

    public double getBaseFeePercent()
    {
        return baseFeePercent;
    }

    public double getArbitrationFeePercent()
    {
        return arbitrationFeePercent;
    }

    public Object getUID()
    {
        return uid;
    }
}
