package io.bitsquare.util;

import io.bitsquare.bank.BankAccountType;
import io.bitsquare.user.Arbitrator;

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
        return randomizeList(list);
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
        return randomizeList(list);
    }

    public static List<Arbitrator> getArbitrators()
    {
        List<Arbitrator> list = new ArrayList<>();
        list.add(new Arbitrator("Charly Shen", UUID.randomUUID().toString(), UUID.randomUUID().toString(), "http://www.arbit.io/Charly_Shen"));
        list.add(new Arbitrator("Tom Shang", UUID.randomUUID().toString(), UUID.randomUUID().toString(), "http://www.arbit.io/Tom_Shang"));
        list.add(new Arbitrator("Edward Swow", UUID.randomUUID().toString(), UUID.randomUUID().toString(), "http://www.arbit.io/Edward_Swow"));
        list.add(new Arbitrator("Julian Sangre", UUID.randomUUID().toString(), UUID.randomUUID().toString(), "http://www.arbit.io/Julian_Sangre"));
        return randomizeList(list);
    }

    public static List<BankAccountType.BankAccountTypeEnum> getBankTransferTypeEnums()
    {
        return randomizeList(Utils.getAllBankAccountTypeEnums());
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
