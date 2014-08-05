package io.bitsquare.btc;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.crypto.DeterministicKey;
import java.io.Serializable;

public class AddressEntry implements Serializable
{
    private static final long serialVersionUID = 5501603992599920416L;
    private transient DeterministicKey key;
    private final NetworkParameters params;
    private final AddressContext addressContext;
    private final byte[] pubKeyHash;

    private String tradeId = null;

    public AddressEntry(DeterministicKey key, NetworkParameters params, AddressContext addressContext)
    {
        this.key = key;
        this.params = params;
        this.addressContext = addressContext;

        pubKeyHash = key.getPubOnly().getPubKeyHash();
    }


    public String getTradeId()
    {
        return tradeId;
    }

    public void setTradeId(String tradeId)
    {
        this.tradeId = tradeId;
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
        return Utils.HEX.encode(key.getPubKey());
    }

    public DeterministicKey getKey()
    {
        return key;
    }

    public Address getAddress()
    {
        return key.toAddress(params);
    }

    public void setDeterministicKey(DeterministicKey key)
    {
        this.key = key;
    }

    public byte[] getPubKeyHash()
    {
        return pubKeyHash;
    }

    public static enum AddressContext
    {
        REGISTRATION_FEE,
        TRADE,
        ARBITRATOR_DEPOSIT
    }
}
