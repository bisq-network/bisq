package io.bitsquare.currency;

import io.bitsquare.gui.util.BitSquareFormatter;
import java.util.Currency;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Fiat
{
    private static final Logger log = LoggerFactory.getLogger(Fiat.class);
    private double value;
    private Currency currency;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Fiat(double value)
    {
        this.value = value;
        this.currency = Currency.getInstance(Locale.getDefault());
    }

    public Fiat(double value, Currency currency)
    {
        this.value = value;
        this.currency = currency;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////


    public String getFormattedValue()
    {
        return BitSquareFormatter.formatPrice(value);
    }

    public String getCurrencyCode()
    {
        return currency.getCurrencyCode();
    }

    public Currency getCurrency()
    {
        return currency;
    }
}
