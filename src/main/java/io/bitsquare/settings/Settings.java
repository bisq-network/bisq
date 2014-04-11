package io.bitsquare.settings;

import com.google.inject.Inject;
import io.bitsquare.storage.IStorage;
import io.bitsquare.trade.orderbook.OrderBookFilter;

import java.util.ArrayList;
import java.util.Currency;
import java.util.Locale;

public class Settings
{
    public static Locale locale = Locale.ENGLISH;
    public static Currency currency = Currency.getInstance("USD");

    private IStorage storage;
    private OrderBookFilter orderBookFilter;

    public static Locale getLocale()
    {
        return Settings.locale;
    }

    public static Currency getCurrency()
    {
        return Settings.currency;
    }

    @Inject
    public Settings(IStorage storage, OrderBookFilter orderBookFilter)
    {

        this.orderBookFilter = orderBookFilter;
        locale = Locale.ENGLISH;
        currency = Currency.getInstance("USD");
        this.storage = storage;

        currency = (Currency) storage.read("Settings.currency");
        if (currency == null)
            currency = Currency.getInstance("USD");
    }


    //TODO remove duplicated entries, insert separators
    public ArrayList<Currency> getAllCurrencies()
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

      /*  Set<Currency> otherCurrenciesSet = Currency.getAvailableCurrencies();
        ArrayList<Currency> otherCurrenciesList = new ArrayList<>();
        otherCurrenciesList.addAll(otherCurrenciesSet);
        Collections.sort(otherCurrenciesList, new CurrencyComparator());

        currencies.addAll(otherCurrenciesList); */
        return currencies;
    }

    public ArrayList<String> getAllBankTransferTypes()
    {
        ArrayList<String> bankTransferTypes = new ArrayList<>();
        bankTransferTypes.add("SEPA");
        bankTransferTypes.add("Wire");
        bankTransferTypes.add("International");
        bankTransferTypes.add("OKPay");
        bankTransferTypes.add("Netteller");
        bankTransferTypes.add("Perfect Money");
        bankTransferTypes.add("Any");
        return bankTransferTypes;
    }

    public ArrayList<String> getAllCountries()
    {
        ArrayList<String> bankTransferTypes = new ArrayList<>();
        bankTransferTypes.add("USA");
        bankTransferTypes.add("GB");
        bankTransferTypes.add("DE");
        bankTransferTypes.add("FR");
        bankTransferTypes.add("ES");
        bankTransferTypes.add("CH");
        bankTransferTypes.add("RUS");
        bankTransferTypes.add("AUS");
        bankTransferTypes.add("CAN");
        bankTransferTypes.add("AT");
        return bankTransferTypes;
    }
    /*
    public ArrayList<String> getAllCountries()
    {
        ArrayList<String> result = new ArrayList<>();
        for (Locale locale : Locale.getAvailableLocales())
        {
            result.add(locale.getDisplayCountry());
        }
        return result;
    }   */

    /*public ArrayList<String> getAllLanguages()
    {
        ArrayList<String> result = new ArrayList<>();
        for (Locale locale : Locale.getAvailableLocales())
        {
            result.add(locale.getDisplayLanguage());
        }

        return result;
    }  */
    public ArrayList<String> getAllLanguages()
    {
        ArrayList<String> bankTransferTypes = new ArrayList<>();
        bankTransferTypes.add("English");
        bankTransferTypes.add("Chinese");
        bankTransferTypes.add("Spanish");
        bankTransferTypes.add("Russian");
        bankTransferTypes.add("French");
        bankTransferTypes.add("Italian");
        return bankTransferTypes;
    }

    public ArrayList<String> getAllArbitrators()
    {
        ArrayList<String> arbitrators = new ArrayList<>();
        arbitrators.add("Paysty pool 1");
        arbitrators.add("Paysty pool 2");
        arbitrators.add("Paysty pool 3");
        arbitrators.add("Paysty pool 4");
        return arbitrators;
    }

    public ArrayList<String> getAllIdentityVerifications()
    {
        ArrayList<String> identityVerifications = new ArrayList<>();
        identityVerifications.add("Passport");
        identityVerifications.add("PGP");
        identityVerifications.add("BTC-OTC");
        identityVerifications.add("Bitcointalk");
        identityVerifications.add("Reddit");
        identityVerifications.add("Skype");
        identityVerifications.add("Google+");
        identityVerifications.add("Twitter");
        identityVerifications.add("Diaspora");
        identityVerifications.add("Facebook");
        identityVerifications.add("Jabber");
        identityVerifications.add("Other");
        identityVerifications.add("Any");
        identityVerifications.add("None");
        return identityVerifications;
    }

    public ArrayList<String> getAllCollaterals()
    {
        ArrayList<String> list = new ArrayList<>();
        list.add("0.01");
        list.add("0.1");
        list.add("0.5");
        list.add("1.0");
        return list;
    }

    public void setCurrency(Currency currency)
    {
        Settings.currency = currency;
        storage.write("Settings.currency", currency);
    }


    public void setLocale(Locale locale)
    {
        Settings.locale = locale;
    }

    public OrderBookFilter getOrderBookFilter()
    {
        return orderBookFilter;
    }

}
