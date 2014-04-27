package io.bitsquare.settings;

import com.google.inject.Inject;
import io.bitsquare.storage.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class OrderBookFilterSettings
{
    private static final Logger log = LoggerFactory.getLogger(OrderBookFilterSettings.class);

    private Storage storage;
    private Currency currency;
    private ArrayList<Currency> currencies;

    @Inject
    public OrderBookFilterSettings(Storage storage)
    {
        this.storage = storage;

        currencies = getCurrencies();
        currency = (Currency) storage.read("OrderBookFilterSettings.currency");
        if (currency == null)
            setCurrency(currencies.get(0));
    }

    public enum BankTransferTypes
    {
        SEPA, OKPAY, WIRE, PERFECT_MONEY, OTHER, ANY
    }

    public enum Arbitrators
    {
        PAYSTY, TLS_NOTARY, BIT_RATED, OTHER, ANY, NONE
    }

    public enum IdVerifications
    {
        PGP, BTC_OTC, OPEN_ID, NAME_COIN, NAME_ID, PASSPORT, SKYPE, FACEBOOK, GOOGLE_PLUS, TWITTER, OTHER, ANY, NONE
    }


    //TODO remove duplicated entries, insert separators
    public ArrayList<Currency> getCurrencies()
    {
        ArrayList<Currency> currencies = new ArrayList<>();
        currencies.add(Currency.getInstance("USD"));
        currencies.add(Currency.getInstance("EUR"));
        currencies.add(Currency.getInstance("CNY"));
        currencies.add(Currency.getInstance("RUB"));

        currencies.add(Currency.getInstance("JPY"));
        currencies.add(Currency.getInstance("GBP"));
        currencies.add(Currency.getInstance("CAD"));
        currencies.add(Currency.getInstance("AUD"));
        currencies.add(Currency.getInstance("CHF"));
        currencies.add(Currency.getInstance("CNY"));

        Set<Currency> otherCurrenciesSet = Currency.getAvailableCurrencies();
        ArrayList<Currency> otherCurrenciesList = new ArrayList<>();
        otherCurrenciesList.addAll(otherCurrenciesSet);
        Collections.sort(otherCurrenciesList, new CurrencyComparator());

        currencies.addAll(otherCurrenciesList);
        return currencies;
    }

    public Currency getCurrency()
    {
        return currency;
    }

    public void setCurrency(Currency currency)
    {
        this.currency = currency;
        storage.write("OrderBookFilterSettings.currency", currency);
    }
}

class CurrencyComparator implements Comparator<Currency>
{
    @Override
    public int compare(Currency a, Currency b)
    {
        return a.getCurrencyCode().compareTo(b.getCurrencyCode());
    }
}