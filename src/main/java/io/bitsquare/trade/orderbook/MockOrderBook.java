package io.bitsquare.trade.orderbook;

import com.google.inject.Inject;
import io.bitsquare.gui.trade.orderbook.OrderBookListItem;
import io.bitsquare.gui.util.Converter;
import io.bitsquare.gui.util.Formatter;
import io.bitsquare.settings.Settings;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.OfferConstraints;
import io.bitsquare.user.BankDetails;
import io.bitsquare.user.User;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import java.util.*;
import java.util.function.Predicate;

public class MockOrderBook implements IOrderBook
{
    private ObservableList<OrderBookListItem> orderBookListItems;
    private Settings settings;

    @Inject
    public MockOrderBook(Settings settings)
    {
        this.settings = settings;
        orderBookListItems = FXCollections.observableArrayList();
        for (int i = 0; i < 100; i++)
        {
            orderBookListItems.add(getOfferListVO());
        }
    }

    @Override
    public ObservableList<OrderBookListItem> getFilteredList(OrderBookFilter orderBookFilter)
    {
        FilteredList filtered = orderBookListItems.filtered(new Predicate<OrderBookListItem>()
        {
            @Override
            public boolean test(OrderBookListItem offerListVO)
            {
                boolean priceResult;
                boolean amountResult = offerListVO.getOffer().getAmount() >= orderBookFilter.getAmount();
                // swap direction. use who want to buy btc want to see sell offers...
                boolean directionResult = offerListVO.getOffer().getDirection() != orderBookFilter.getDirection();
                boolean currencyResult = offerListVO.getOffer().getCurrency().equals(orderBookFilter.getCurrency());

                if (offerListVO.getOffer().getDirection() == Direction.BUY && orderBookFilter.getPrice() > 0)
                    priceResult = offerListVO.getOffer().getPrice() <= orderBookFilter.getPrice();
                else
                    priceResult = offerListVO.getOffer().getPrice() >= orderBookFilter.getPrice();
                return priceResult && amountResult && directionResult && currencyResult;
            }
        });

        //TODO use FilteredList
        ObservableList<OrderBookListItem> result = FXCollections.observableArrayList();
        result.addAll(filtered);
        return result;
    }

    private OrderBookListItem getOfferListVO()
    {
        Offer i = getOffer();
        return new OrderBookListItem(i);
    }

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

       /* Set<Currency> otherCurrenciesSet = Currency.getAvailableCurrencies();
        ArrayList<Currency> otherCurrenciesList = new ArrayList<>();
        otherCurrenciesList.addAll(otherCurrenciesSet);
        Collections.sort(otherCurrenciesList, new CurrencyComparator());

        currencies.addAll(otherCurrenciesList); */

        return currencies;
    }

    private Offer getOffer()
    {
        double amount = Math.random() * 10 + 0.1;
        amount = Converter.convertToDouble(Formatter.formatAmount(amount));
        double minAmount = Math.random() * amount;
        minAmount = Converter.convertToDouble(Formatter.formatAmount(minAmount));

        String country = getCountries().get(0);
        BankDetails bankDetails = new BankDetails();
        String bankTransferType = getBankTransferTypes().get(0);
        bankDetails.setBankTransferType(bankTransferType);
        User offerer = new User(UUID.randomUUID().toString(), UUID.randomUUID().toString(), country, bankDetails);

        Direction direction = Direction.BUY;
        double price = 500 + Math.random() * 50;
        if (Math.random() > 0.5)
        {
            direction = Direction.SELL;
            price = 500 - Math.random() * 50;
        }
        Currency currency = (randomizeCurrencies(getCurrencies(), false)).get(0);
        return new Offer(UUID.randomUUID(),
                direction,
                price,
                amount,
                minAmount,
                currency,
                offerer,
                getRandomOfferConstraints()
        );
    }

    public OfferConstraints getRandomOfferConstraints()
    {
        OfferConstraints offerConstraints = new OfferConstraints(getCountries(),
                getLanguages(),
                Double.valueOf(getCollaterals().get(0)),
                getBankTransferTypes(),
                getArbitrators().get(0),
                randomizeStrings(settings.getAllIdentityVerifications(), false).get(0));

        return offerConstraints;
    }


    private List<String> getCountries()
    {

        return randomizeStrings(settings.getAllCountries(), false);
    }

    private List<String> getLanguages()
    {
        return randomizeStrings(settings.getAllLanguages(), false);
    }

    private List<String> getBankTransferTypes()
    {
        return randomizeStrings(settings.getAllBankTransferTypes(), false);
    }

    private List<String> getArbitrators()
    {
        return randomizeStrings(settings.getAllArbitrators(), false);
    }

    private List<String> getCollaterals()
    {
        return randomizeStrings(settings.getAllCollaterals(), false);
    }


    private List<String> randomizeStrings(List<String> list, boolean optional)
    {
        int e = new Random().nextInt(list.size());
        if (!optional && list.size() > 0)
            e = Math.max(e, 1);
        int s = (e == 0) ? 0 : new Random().nextInt(e);
        list = list.subList(s, e);
        return list;
    }

    private List<Double> randomizeDouble(List<Double> list, boolean optional)
    {
        int e = new Random().nextInt(list.size());
        if (!optional && list.size() > 0)
            e = Math.max(e, 1);
        int s = (e == 0) ? 0 : new Random().nextInt(e);
        list = list.subList(s, e);
        return list;
    }

    private List<Currency> randomizeCurrencies(List<Currency> list, boolean optional)
    {
        int e = new Random().nextInt(list.size());
        if (!optional && list.size() > 0)
            e = Math.max(e, 1);
        int s = (e == 0) ? 0 : new Random().nextInt(e);
        list = list.subList(s, e);
        return list;
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