package io.bitsquare.btc;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Utils;

import java.io.Serializable;

public class AddressEntry implements Serializable
{
    private static final long serialVersionUID = 5501603992599920416L;

    public static enum AddressContext
    {
        REGISTRATION_FEE,
        TRADE,
        ARBITRATOR_DEPOSIT
    }

    private ECKey key;
    private NetworkParameters params;
    private String tradeId = null;

    private AddressContext addressContext;

    public AddressEntry(ECKey key, NetworkParameters params, AddressContext addressContext)
    {
        this.key = key;
        this.params = params;
        this.addressContext = addressContext;
    }

    public void setTradeId(String tradeId)
    {
        this.tradeId = tradeId;
    }

    public String getTradeId()
    {
        return tradeId;
    }

    public AddressContext getAddressContext()
    {
        return addressContext;
    }

    public String getAddressString()
    {
        return getAddress().toString();
    }

    public String getPubKeyAsHexString()
    {
        return Utils.bytesToHexString(key.getPubKey());
    }

    public ECKey getKey()
    {
        return key;
    }

    public Address getAddress()
    {
        return key.toAddress(params);
    }
}
