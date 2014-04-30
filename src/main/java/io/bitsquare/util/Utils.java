package io.bitsquare.util;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.bitsquare.bank.BankAccountType;
import javafx.animation.AnimationTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class Utils
{
    private static final Logger log = LoggerFactory.getLogger(Utils.class);

    public static String convertToJson(Object object)
    {
        Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
        return gson.toJson(object);
    }

    private static long lastTimeStamp = System.currentTimeMillis();

    public static void printElapsedTime(String msg)
    {
        if (msg.length() > 0)
            msg += " / ";
        long timeStamp = System.currentTimeMillis();
        log.debug(msg + "Elapsed: " + String.valueOf(timeStamp - lastTimeStamp));
        lastTimeStamp = timeStamp;
    }

    public static void printElapsedTime()
    {
        printElapsedTime("");
    }


    public static void openURL(String url) throws Exception
    {
        Desktop.getDesktop().browse(new URI(url));
    }


    public static Object copy(Object orig)
    {
        Object obj = null;
        try
        {
            // Write the object out to a byte array
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bos);
            out.writeObject(orig);
            out.flush();
            out.close();

            // Make an input stream from the byte array and read
            // a copy of the object back in.
            ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
            obj = in.readObject();
        } catch (IOException e)
        {
            e.printStackTrace();
        } catch (ClassNotFoundException cnfe)
        {
            cnfe.printStackTrace();
        }
        return obj;
    }

    public static ArrayList<Locale> getAllLocales()
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
        //Locale defaultLocale = new Locale("de", "AT");
        list.remove(defaultLocale);
        list.add(0, defaultLocale);

        return list;
    }

    public static List<Currency> getAllCurrencies()
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

    public static ArrayList<BankAccountType.BankAccountTypeEnum> getAllBankAccountTypeEnums()
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

    public static ArrayList<BankAccountType> getAllBankAccountTypes()
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

    /**
     * @param delay    in milliseconds
     * @param callback
     * @usage Utils.setTimeout(1000, (AnimationTimer animationTimer) -> {
     * doSomething();
     * return null;
     * });
     */
    public static void setTimeout(int delay, Function<AnimationTimer, Void> callback)
    {
        long startTime = System.currentTimeMillis();
        AnimationTimer animationTimer = new AnimationTimer()
        {
            @Override
            public void handle(long arg0)
            {
                if (System.currentTimeMillis() > delay + startTime)
                {
                    callback.apply(this);
                    this.stop();
                }
            }
        };
        animationTimer.start();
    }
}
