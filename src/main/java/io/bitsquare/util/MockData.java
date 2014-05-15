package io.bitsquare.util;

import com.google.bitcoin.core.ECKey;
import io.bitsquare.bank.BankAccountType;
import io.bitsquare.user.Arbitrator;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.*;

public class MockData
{

    public static List<Currency> getCurrencies()
    {
        List<Currency> list = new ArrayList<>();
        list.add(Currency.getInstance("EUR"));
        list.add(Currency.getInstance("USD"));
        list.add(Currency.getInstance("GBP"));
        list.add(Currency.getInstance("RUB"));
        list.add(Currency.getInstance("CAD"));
        list.add(Currency.getInstance("AUD"));
        list.add(Currency.getInstance("JPY"));
        list.add(Currency.getInstance("CNY"));
        list.add(Currency.getInstance("CHF"));
        return list;
    }

    public static List<Currency> getRandomCurrencies()
    {
        return randomizeList(getCurrencies());
    }

    public static List<Locale> getLocales()
    {
        List<Locale> list = new ArrayList<>();
        list.add(new Locale("de", "AT"));
        list.add(new Locale("de", "DE"));
        list.add(new Locale("en", "US"));
        list.add(new Locale("en", "UK"));
        list.add(new Locale("es", "ES"));
        list.add(new Locale("ru", "RU"));
        list.add(new Locale("zh", "CN"));
        list.add(new Locale("en", "AU"));
        list.add(new Locale("it", "IT"));
        list.add(new Locale("en", "CA"));
        return list;
    }

    public static List<Locale> getRandomLocales()
    {
        return randomizeList(getLocales());
    }

    public static List<Arbitrator> getArbitrators()
    {
        List<Arbitrator> list = new ArrayList<>();
        list.add(new Arbitrator("uid_1", "Charlie Boom", com.google.bitcoin.core.Utils.bytesToHexString(new ECKey().getPubKey()),
                getMessagePubKey(), "http://www.arbit.io/Charly_Boom", 1, 10, com.google.bitcoin.core.Utils.toNanoCoins("0.01")));
        list.add(new Arbitrator("uid_2", "Tom Shang", com.google.bitcoin.core.Utils.bytesToHexString(new ECKey().getPubKey()),
                getMessagePubKey(), "http://www.arbit.io/Tom_Shang", 0, 1, com.google.bitcoin.core.Utils.toNanoCoins("0.001")));
        list.add(new Arbitrator("uid_3", "Edward Snow", com.google.bitcoin.core.Utils.bytesToHexString(new ECKey().getPubKey()),
                getMessagePubKey(), "http://www.arbit.io/Edward_Swow", 2, 5, com.google.bitcoin.core.Utils.toNanoCoins("0.05")));
        list.add(new Arbitrator("uid_4", "Julian Sander", com.google.bitcoin.core.Utils.bytesToHexString(new ECKey().getPubKey()),
                getMessagePubKey(), "http://www.arbit.io/Julian_Sander", 0, 20, com.google.bitcoin.core.Utils.toNanoCoins("0.1")));
        return list;
    }

    private static String getMessagePubKey()
    {
        try
        {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DSA");
            keyGen.initialize(1024);
            KeyPair generatedKeyPair = keyGen.genKeyPair();
            PublicKey pubKey = generatedKeyPair.getPublic();
            return DSAKeyUtil.getHexStringFromPublicKey(pubKey);
        } catch (Exception e2)
        {
            return null;
        }
    }

    public static List<Arbitrator> getRandomArbitrators()
    {
        return randomizeList(getArbitrators());
    }


    public static List<BankAccountType.BankAccountTypeEnum> getBankTransferTypeEnums()
    {
        return Utilities.getAllBankAccountTypeEnums();
    }

    public static List<BankAccountType.BankAccountTypeEnum> getRandomBankTransferTypeEnums()
    {
        return randomizeList(Utilities.getAllBankAccountTypeEnums());
    }

    public static List randomizeList(List list)
    {
        int e = new Random().nextInt(list.size());
        if (list.size() > 0)
            e = Math.max(e, 1);
        int s = (e == 0) ? 0 : new Random().nextInt(e);
        list = list.subList(s, e);
        return list;
    }

    public static List reduce(List list, int count)
    {
        List result = new ArrayList();
        for (int i = 0; i < count && i < list.size(); i++)
        {
            result.add(list.get(i));
        }
        return result;
    }


}
