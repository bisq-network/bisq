package io.bitsquare.btc;

public class KeyPair
{
    private String pubKey;
    private String privKey;

    public KeyPair(String pubKey, String privKey)
    {
        this.pubKey = pubKey;
        this.privKey = privKey;
    }

    public String getPrivKey()
    {
        return privKey;
    }

    public void setPrivKey(String privKey)
    {
        this.privKey = privKey;
    }

    public String getPubKey()
    {
        return pubKey;
    }

    public void setPubKey(String pubKey)
    {
        this.pubKey = pubKey;
    }
}
