package io.bitsquare.gui.funds;

import com.google.bitcoin.core.Address;
import io.bitsquare.btc.AddressInfo;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class AddressListItem
{
    private final StringProperty addressString = new SimpleStringProperty();
    private AddressInfo addressInfo;

    public AddressListItem(AddressInfo addressInfo)
    {
        this.addressInfo = addressInfo;
        this.addressString.set(getAddress().toString());

    }

    public final String getLabel()
    {
        switch (addressInfo.getAddressContext())
        {
            case REGISTRATION_FEE:
                return "Registration fee";
            case CREATE_OFFER_FEE:
                return "Create offer fee";
            case TAKE_OFFER_FEE:
                return "Take offer fee";
            case TRADE:
                if (addressInfo.getTradeId() != null)
                    return "Trade ID: " + addressInfo.getTradeId();
                else
                    return "Unused";
            case ARBITRATOR_DEPOSIT:
                return "Arbitration deposit";
        }
        return "";
    }

    public final StringProperty addressStringProperty()
    {
        return this.addressString;
    }


    public Address getAddress()
    {
        return addressInfo.getAddress();
    }

    public AddressInfo getAddressInfo()
    {
        return addressInfo;
    }
}
