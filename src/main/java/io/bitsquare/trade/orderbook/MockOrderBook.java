package io.bitsquare.trade.orderbook;

import com.google.inject.Inject;
import io.bitsquare.bank.BankAccountType;
import io.bitsquare.gui.trade.orderbook.OrderBookListItem;
import io.bitsquare.gui.util.Converter;
import io.bitsquare.gui.util.Formatter;
import io.bitsquare.settings.Settings;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.Offer;
import io.bitsquare.user.User;
import io.bitsquare.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class MockOrderBook extends OrderBook
{
    private static final Logger log = LoggerFactory.getLogger(MockOrderBook.class);

    @Inject
    public MockOrderBook(Settings settings, User user)
    {
        super(settings, user);
        for (int i = 0; i < 1000; i++)
        {
            allOffers.add(new OrderBookListItem(getOffer()));
        }
    }

    private Offer getOffer()
    {
        double amount = Math.random() * 10 + 0.1;
        amount = Converter.stringToDouble(Formatter.formatAmount(amount));
        double minAmount = Math.random() * amount;
        minAmount = Converter.stringToDouble(Formatter.formatAmount(minAmount));

        Direction direction = Direction.BUY;
        double price = 500 + Math.random() * 50;
        if (Math.random() > 0.5)
        {
            direction = Direction.SELL;
            price = 500 - Math.random() * 50;
        }

        Offer offer = new Offer(UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                direction,
                price,
                amount,
                minAmount,
                getBankTransferTypeEnums().get(0),
                getCurrencies().get(0),
                getLocales().get(0),
                getLocales(),
                getLocales());

        return offer;
    }

    private List<Currency> getCurrencies()
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

    private List<Locale> getLocales()
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


    private List<BankAccountType.BankAccountTypeEnum> getBankTransferTypeEnums()
    {
        return randomizeList(Utils.getAllBankAccountTypeEnums());
    }

    private List randomizeList(List list)
    {
        int e = new Random().nextInt(list.size());
        if (list.size() > 0)
            e = Math.max(e, 1);
        int s = (e == 0) ? 0 : new Random().nextInt(e);
        list = list.subList(s, e);
        return list;
    }

    private List reduce(List list, int count)
    {
        List result = new ArrayList();
        for (int i = 0; i < count && i < list.size(); i++)
        {
            result.add(list.get(i));
        }
        return result;
    }
}
