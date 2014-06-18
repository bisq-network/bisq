package io.bitsquare.btc;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Utils;

import java.beans.Transient;
import java.io.Serializable;

public class AddressInfo implements Serializable
{
    private static final long serialVersionUID = 5501603992599920416L;

    private ECKey key;
    private NetworkParameters params;
    private String label;

    public AddressInfo(ECKey key, NetworkParameters params, String label)
    {
        this.key = key;
        this.params = params;
        this.label = label;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }

    public String getLabel()
    {
        return label;
    }

    public String getAddressString()
    {
        return getAddress().toString();
    }

    public String getPubKeyAsHexString()
    {
        return Utils.bytesToHexString(key.getPubKey());
    }

    @Transient
    public ECKey getKey()
    {
        return key;
    }

    @Transient
    public Address getAddress()
    {
        return key.toAddress(params);
    }
}
