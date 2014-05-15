package io.bitsquare.user;

import java.io.Serializable;
import java.math.BigInteger;

public class Arbitrator implements Serializable
{
    private static final long serialVersionUID = -2625059604136756635L;

    private String name;
    private String pubKey;
    private String messagePubKeyAsHex;
    private String url;
    private int baseFeePercent;      // in percent int 1 for 1%
    private int arbitrationFeePercent;
    private BigInteger minArbitrationAmount; // in Satoshis
    private String uid;

    public Arbitrator(String uid, String name, String pubKey, String messagePubKeyAsHex, String url, int baseFeePercent, int arbitrationFeePercent, BigInteger minArbitrationAmount)
    {
        this.uid = uid;
        this.name = name;
        this.pubKey = pubKey;
        this.messagePubKeyAsHex = messagePubKeyAsHex;
        this.url = url;
        this.baseFeePercent = baseFeePercent;
        this.arbitrationFeePercent = arbitrationFeePercent;
        this.minArbitrationAmount = minArbitrationAmount;
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

    public String getMessagePubKeyAsHex()
    {
        return messagePubKeyAsHex;
    }

    public String getUrl()
    {
        return url;
    }

    public BigInteger getMinArbitrationAmount()
    {
        return minArbitrationAmount;
    }

    public int getBaseFeePercent()
    {
        return baseFeePercent;
    }

    public int getArbitrationFeePercent()
    {
        return arbitrationFeePercent;
    }

    public Object getUid()
    {
        return uid;
    }

    @Override
    public String toString()
    {
        return "Arbitrator{" +
                "name='" + name + '\'' +
                ", pubKey='" + pubKey + '\'' +
                ", messagePubKeyAsHex='" + messagePubKeyAsHex + '\'' +
                ", url='" + url + '\'' +
                ", baseFeePercent=" + baseFeePercent +
                ", arbitrationFeePercent=" + arbitrationFeePercent +
                ", minArbitrationAmount=" + minArbitrationAmount +
                ", uid='" + uid + '\'' +
                '}';
    }
}
