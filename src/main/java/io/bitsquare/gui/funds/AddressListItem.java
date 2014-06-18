package io.bitsquare.gui.funds;

import com.google.bitcoin.core.Address;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class AddressListItem
{
    private final StringProperty label = new SimpleStringProperty();
    private final StringProperty addressString = new SimpleStringProperty();
    private final BooleanProperty isUsed = new SimpleBooleanProperty();
    private Address address;

    public AddressListItem(String label, Address address, boolean isUsed)
    {
        this.address = address;
        this.label.set(label);
        this.addressString.set(address.toString());

        this.isUsed.set(isUsed);
    }

    // called form table columns
    public final StringProperty labelProperty()
    {
        return this.label;
    }

    public final StringProperty addressStringProperty()
    {
        return this.addressString;
    }

    public final BooleanProperty isUsedProperty()
    {
        return this.isUsed;
    }

    public Address getAddress()
    {
        return address;
    }
}
