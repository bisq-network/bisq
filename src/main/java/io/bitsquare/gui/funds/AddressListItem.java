package io.bitsquare.gui.funds;

import com.google.bitcoin.core.Address;
import io.bitsquare.btc.AddressEntry;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class AddressListItem
{
    private final StringProperty addressString = new SimpleStringProperty();
    private AddressEntry addressEntry;

    public AddressListItem(AddressEntry addressEntry)
    {
        this.addressEntry = addressEntry;
        this.addressString.set(getAddress().toString());

    }

    public final String getLabel()
    {
        switch (addressEntry.getAddressContext())
        {
            case REGISTRATION_FEE:
                return "Registration fee";
            case TRADE:
                if (addressEntry.getTradeId() != null)
                    return "Trade ID: " + addressEntry.getTradeId();
                else
                    return "Trade (not used yet)";
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
        return addressEntry.getAddress();
    }

    public AddressEntry getAddressEntry()
    {
        return addressEntry;
    }
}
