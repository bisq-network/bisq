package io.bitsquare.btc;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Utils;
import java.io.Serializable;
import org.jetbrains.annotations.Nullable;

public class AddressEntry implements Serializable
{
    private static final long serialVersionUID = 5501603992599920416L;
    private final ECKey key;
    private final NetworkParameters params;
    private final AddressContext addressContext;
    @Nullable
    private String tradeId = null;

    public AddressEntry(ECKey key, NetworkParameters params, AddressContext addressContext)
    {
        this.key = key;
        this.params = params;
        this.addressContext = addressContext;
    }

    @Nullable
    public String getTradeId()
    {
        return tradeId;
    }

    public void setTradeId(@Nullable String tradeId)
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

    public static enum AddressContext
    {
        REGISTRATION_FEE,
        TRADE,
        ARBITRATOR_DEPOSIT
    }
}
