package io.bitsquare.settings;

import com.google.inject.Inject;
import io.bitsquare.bank.BankAccountType;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.orderbook.OrderBookFilter;

import java.util.*;
import java.util.function.Predicate;

public class Settings
{

    public static Locale locale = Locale.ENGLISH;
    public static Currency currency = Currency.getInstance("USD");


    private Storage storage;
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
    public Settings(Storage storage, OrderBookFilter orderBookFilter)
    {
        this.storage = storage;
        this.orderBookFilter = orderBookFilter;


        locale = Locale.ENGLISH;
        currency = Currency.getInstance("USD");

        currency = (Currency) storage.read("Settings.currency");
        if (currency == null)
            currency = Currency.getInstance("USD");
    }

    public ArrayList<Locale> getAllLocales()
    {
        ArrayList<Locale> list = new ArrayList<Locale>(Arrays.asList(Locale.getAvailableLocales()));
        list.removeIf(new Predicate<Locale>()
        {
            @Override
            public boolean test(Locale locale)
            {
                return locale == null || locale.getCountry().equals("") || locale.getLanguage().equals("");
            }
        });

        list.sort(new Comparator<Locale>()
        {
            @Override
            public int compare(Locale locale1, Locale locale2)
            {
                return locale1.getDisplayCountry().compareTo(locale2.getDisplayCountry());
            }
        });

        Locale defaultLocale = Locale.getDefault();
        list.remove(defaultLocale);
        list.add(0, defaultLocale);

        return list;
    }

    public List<Currency> getAllCurrencies()
    {
        ArrayList<Currency> mainCurrencies = new ArrayList<>();
        mainCurrencies.add(Currency.getInstance("USD"));
        mainCurrencies.add(Currency.getInstance("EUR"));
        mainCurrencies.add(Currency.getInstance("CNY"));
        mainCurrencies.add(Currency.getInstance("RUB"));
        mainCurrencies.add(Currency.getInstance("JPY"));
        mainCurrencies.add(Currency.getInstance("GBP"));
        mainCurrencies.add(Currency.getInstance("CAD"));
        mainCurrencies.add(Currency.getInstance("AUD"));
        mainCurrencies.add(Currency.getInstance("CHF"));
        mainCurrencies.add(Currency.getInstance("CNY"));

        Set<Currency> allCurrenciesSet = Currency.getAvailableCurrencies();

        allCurrenciesSet.removeAll(mainCurrencies);
        List<Currency> allCurrenciesList = new ArrayList<>(allCurrenciesSet);
        allCurrenciesList.sort(new Comparator<Currency>()
        {
            @Override
            public int compare(Currency a, Currency b)
            {
                return a.getCurrencyCode().compareTo(b.getCurrencyCode());
            }
        });

        List<Currency> resultList = new ArrayList<>(mainCurrencies);
        resultList.addAll(allCurrenciesList);
        Currency defaultCurrency = Currency.getInstance(Locale.getDefault());
        resultList.remove(defaultCurrency);
        resultList.add(0, defaultCurrency);

        return resultList;
    }

    public ArrayList<BankAccountType.BankAccountTypeEnum> getAllBankAccountTypeEnums()
    {
        ArrayList<BankAccountType.BankAccountTypeEnum> bankAccountTypeEnums = new ArrayList<>();
        bankAccountTypeEnums.add(BankAccountType.BankAccountTypeEnum.SEPA);
        bankAccountTypeEnums.add(BankAccountType.BankAccountTypeEnum.WIRE);
        bankAccountTypeEnums.add(BankAccountType.BankAccountTypeEnum.INTERNATIONAL);
        bankAccountTypeEnums.add(BankAccountType.BankAccountTypeEnum.OK_PAY);
        bankAccountTypeEnums.add(BankAccountType.BankAccountTypeEnum.NET_TELLER);
        bankAccountTypeEnums.add(BankAccountType.BankAccountTypeEnum.PERFECT_MONEY);
        bankAccountTypeEnums.add(BankAccountType.BankAccountTypeEnum.OTHER);
        return bankAccountTypeEnums;
    }

    public ArrayList<BankAccountType> getAllBankAccountTypes()
    {
        ArrayList<BankAccountType> bankTransferTypes = new ArrayList<>();
        bankTransferTypes.add(new BankAccountType(BankAccountType.BankAccountTypeEnum.SEPA, "IBAN", "BIC"));
        bankTransferTypes.add(new BankAccountType(BankAccountType.BankAccountTypeEnum.WIRE, "Prim_todo", "Sec_todo"));
        bankTransferTypes.add(new BankAccountType(BankAccountType.BankAccountTypeEnum.INTERNATIONAL, "Prim_todo", "Sec_todo"));
        bankTransferTypes.add(new BankAccountType(BankAccountType.BankAccountTypeEnum.OK_PAY, "Prim_todo", "Sec_todo"));
        bankTransferTypes.add(new BankAccountType(BankAccountType.BankAccountTypeEnum.NET_TELLER, "Prim_todo", "Sec_todo"));
        bankTransferTypes.add(new BankAccountType(BankAccountType.BankAccountTypeEnum.PERFECT_MONEY, "Prim_todo", "Sec_todo"));
        bankTransferTypes.add(new BankAccountType(BankAccountType.BankAccountTypeEnum.OTHER, "Prim_todo", "Sec_todo"));
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
