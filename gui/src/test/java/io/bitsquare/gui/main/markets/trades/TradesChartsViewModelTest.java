package io.bitsquare.gui.main.markets.trades;

import io.bitsquare.trade.TradeStatistics;
import io.bitsquare.trade.offer.Offer;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class TradesChartsViewModelTest {
    private static final Logger log = LoggerFactory.getLogger(TradesChartsViewModelTest.class);

    @Test
    public void testGetCandleData() {
        TradesChartsViewModel model = new TradesChartsViewModel();

        long low = Fiat.parseFiat("EUR", "500").value;
        long open = Fiat.parseFiat("EUR", "520").value;
        long close = Fiat.parseFiat("EUR", "580").value;
        long high = Fiat.parseFiat("EUR", "600").value;
        long average = Fiat.parseFiat("EUR", "550").value;
        long amount = Coin.parseCoin("4").value;
        long volume = Fiat.parseFiat("EUR", "2200").value;
        boolean isBullish = true;

        Set<TradeStatistics> set = new HashSet<>();
        final Date now = new Date();
        Offer offer = new Offer(null,
                null,
                null,
                null,
                0,
                0,
                false,
                0,
                0,
                "EUR",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        set.add(new TradeStatistics(offer, Fiat.parseFiat("EUR", "500"), Coin.parseCoin("1"), new Date(now.getTime() + 100), null, null, null));
        set.add(new TradeStatistics(offer, Fiat.parseFiat("EUR", "520"), Coin.parseCoin("1"), new Date(now.getTime()), null, null, null));
        set.add(new TradeStatistics(offer, Fiat.parseFiat("EUR", "580"), Coin.parseCoin("1"), new Date(now.getTime() + 300), null, null, null));
        set.add(new TradeStatistics(offer, Fiat.parseFiat("EUR", "600"), Coin.parseCoin("1"), new Date(now.getTime() + 200), null, null, null));

        CandleData candleData = model.getCandleData(model.getTickFromTime(now.getTime(), TradesChartsViewModel.TickUnit.DAY), set);
        assertEquals(open, candleData.open);
        assertEquals(close, candleData.close);
        assertEquals(high, candleData.high);
        assertEquals(low, candleData.low);
        assertEquals(average, candleData.average);
        assertEquals(amount, candleData.amount);
        assertEquals(volume, candleData.volume);
        assertEquals(isBullish, candleData.isBullish);
    }
}
