package io.bitsquare.gui.main.market.trades;

import io.bitsquare.gui.main.market.trades.charts.CandleData;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.statistics.TradeStatistics;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

//TODO causes java.lang.NullPointerException
// at io.bitsquare.gui.main.market.trades.TradesChartsViewModel.getCurrencyCode(TradesChartsViewModel.java:209)
@Ignore
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
        set.add(new TradeStatistics(offer, Fiat.parseFiat("EUR", "520"), Coin.parseCoin("1"), new Date(now.getTime()), null, null));
        set.add(new TradeStatistics(offer, Fiat.parseFiat("EUR", "500"), Coin.parseCoin("1"), new Date(now.getTime() + 100), null, null));
        set.add(new TradeStatistics(offer, Fiat.parseFiat("EUR", "600"), Coin.parseCoin("1"), new Date(now.getTime() + 200), null, null));
        set.add(new TradeStatistics(offer, Fiat.parseFiat("EUR", "580"), Coin.parseCoin("1"), new Date(now.getTime() + 300), null, null));

        CandleData candleData = model.getCandleData(model.getTickFromTime(now.getTime(), TradesChartsViewModel.TickUnit.DAY), set);
        assertEquals(open, candleData.open);
        assertEquals(close, candleData.close);
        assertEquals(high, candleData.high);
        assertEquals(low, candleData.low);
        assertEquals(average, candleData.average);
        assertEquals(amount, candleData.accumulatedAmount);
        assertEquals(volume, candleData.accumulatedVolume);
        assertEquals(isBullish, candleData.isBullish);
    }
}
