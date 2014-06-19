package io.bitsquare.btc;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Utils;

import java.io.Serializable;

public class AddressInfo implements Serializable
{
    private static final long serialVersionUID = 5501603992599920416L;

    public static enum AddressContext
    {
        REGISTRATION_FEE,
        CREATE_OFFER_FEE,
        TAKE_OFFER_FEE,
        TRADE,
        ARBITRATOR_DEPOSIT
    }

    private ECKey key;
    private NetworkParameters params;
    private String label;
    private String tradeId = null;

    private AddressContext addressContext;

    public AddressInfo(ECKey key, NetworkParameters params, AddressContext addressContext, String label)
    {
        this.key = key;
        this.params = params;
        this.addressContext = addressContext;
        this.label = label;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }

    public void setTradeId(String tradeId)
    {
        this.tradeId = tradeId;
    }

    public String getTradeId()
    {
        return tradeId;
    }


    public String getLabel()
    {
        return label;
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
