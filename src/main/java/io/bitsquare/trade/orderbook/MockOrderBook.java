package io.bitsquare.trade.orderbook;

import com.google.inject.Inject;
import io.bitsquare.gui.trade.orderbook.OrderBookListItem;
import io.bitsquare.gui.util.Converter;
import io.bitsquare.gui.util.Formatter;
import io.bitsquare.settings.Settings;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.Offer;
import io.bitsquare.user.User;
import io.bitsquare.util.MockData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

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
                MockData.getBankTransferTypeEnums().get(0),
                MockData.getCurrencies().get(0),
                MockData.getLocales().get(0),
                MockData.getArbitrators().get(0),
                MockData.getLocales(),
                MockData.getLocales());

        return offer;
    }
}
