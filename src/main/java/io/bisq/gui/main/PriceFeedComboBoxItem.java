package io.bisq.gui.main;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.Getter;
import lombok.Setter;

public class PriceFeedComboBoxItem {
    public final String currencyCode;
    public final StringProperty displayStringProperty = new SimpleStringProperty();
    @Setter
    @Getter
    private boolean isPriceAvailable;
    @Setter
    @Getter
    private boolean isExternallyProvidedPrice;

    public PriceFeedComboBoxItem(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public void setDisplayString(String displayString) {
        this.displayStringProperty.set(displayString);
    }
}
