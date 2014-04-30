package io.bitsquare.user;

import java.io.Serializable;

public class Arbitrator implements Serializable
{
    private static final long serialVersionUID = -2625059604136756635L;

    private String name;
    private String pubKey;
    private String messageID;
    private String url;

    public Arbitrator(String name, String pubKey, String messageID, String url)
    {

        this.name = name;
        this.pubKey = pubKey;
        this.messageID = messageID;
        this.url = url;
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

}
