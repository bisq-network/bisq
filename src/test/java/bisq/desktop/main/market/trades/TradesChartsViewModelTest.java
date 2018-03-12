/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.market.trades;

import bisq.desktop.Navigation;
import bisq.desktop.main.market.trades.charts.CandleData;
import bisq.desktop.util.BSFormatter;

import bisq.core.offer.OfferPayload;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.statistics.TradeStatistics2;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;

import bisq.common.crypto.KeyRing;
import bisq.common.crypto.KeyStorage;
import bisq.common.locale.FiatCurrency;
import bisq.common.monetary.Price;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;

import java.io.File;
import java.io.IOException;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Mock;
import mockit.MockUp;
import mockit.Tested;
import mockit.integration.junit4.JMockit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(JMockit.class)
public class TradesChartsViewModelTest {
    @Tested
    TradesChartsViewModel model;
    @Injectable
    Preferences preferences;
    @Injectable
    PriceFeedService priceFeedService;
    @Injectable
    Navigation navigation;
    @Injectable
    BSFormatter formatter;
    @Injectable
    TradeStatisticsManager tsm;

    private static final Logger log = LoggerFactory.getLogger(TradesChartsViewModelTest.class);
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private KeyRing keyRing;
    private File dir;
    OfferPayload offer = new OfferPayload(null,
            0,
            null,
            null,
            null,
            0,
            0,
            false,
            0,
            0,
            "BTC",
            "EUR",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            0,
            0,
            false,
            0,
            0,
            0,
            0,
            false,
            false,
            0,
            0,
            false,
            null,
            null,
            1
    );

    @Before
    public void setup() throws IOException {
        Security.addProvider(new BouncyCastleProvider());
        dir = File.createTempFile("temp_tests1", "");
        //noinspection ResultOfMethodCallIgnored
        dir.delete();
        //noinspection ResultOfMethodCallIgnored
        dir.mkdir();
        keyRing = new KeyRing(new KeyStorage(dir));
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testGetCandleData() {
        model.selectedTradeCurrencyProperty.setValue(new FiatCurrency("EUR"));

        long low = Fiat.parseFiat("EUR", "500").value;
        long open = Fiat.parseFiat("EUR", "520").value;
        long close = Fiat.parseFiat("EUR", "580").value;
        long high = Fiat.parseFiat("EUR", "600").value;
        long average = Fiat.parseFiat("EUR", "550").value;
        long amount = Coin.parseCoin("4").value;
        long volume = Fiat.parseFiat("EUR", "2200").value;
        boolean isBullish = true;

        Set<TradeStatistics2> set = new HashSet<>();
        final Date now = new Date();

        set.add(new TradeStatistics2(offer, Price.parse("EUR", "520"), Coin.parseCoin("1"), new Date(now.getTime()), null));
        set.add(new TradeStatistics2(offer, Price.parse("EUR", "500"), Coin.parseCoin("1"), new Date(now.getTime() + 100), null));
        set.add(new TradeStatistics2(offer, Price.parse("EUR", "600"), Coin.parseCoin("1"), new Date(now.getTime() + 200), null));
        set.add(new TradeStatistics2(offer, Price.parse("EUR", "580"), Coin.parseCoin("1"), new Date(now.getTime() + 300), null));

        CandleData candleData = model.getCandleData(model.roundToTick(now, TradesChartsViewModel.TickUnit.DAY).getTime(), set);
        assertEquals(open, candleData.open);
        assertEquals(close, candleData.close);
        assertEquals(high, candleData.high);
        assertEquals(low, candleData.low);
        assertEquals(average, candleData.average);
        assertEquals(amount, candleData.accumulatedAmount);
        assertEquals(volume, candleData.accumulatedVolume);
        assertEquals(isBullish, candleData.isBullish);
    }

    @Test
    public void testItemLists() throws ParseException {
        // Helper class to add historic trades
        class Trade {
            Trade(String date, String size, String price, String cc) {
                try {
                    this.date = dateFormat.parse(date);
                } catch (ParseException p) {
                    this.date = new Date();
                }
                this.size = size;
                this.price = price;
                this.cc = cc;
            }

            Date date;
            String size;
            String price;
            String cc;
        }
        ;

        // Trade EUR
        model.selectedTradeCurrencyProperty.setValue(new FiatCurrency("EUR"));

        ArrayList<Trade> trades = new ArrayList<Trade>();

        // Set predetermined time to use as "now" during test
        Date test_time = dateFormat.parse("2018-01-01T00:00:05");  // Monday
        new MockUp<System>() {
            @Mock
            long currentTimeMillis() {
                return test_time.getTime();
            }
        };

        // Two trades 10 seconds apart, different YEAR, MONTH, WEEK, DAY, HOUR, MINUTE_10
        trades.add(new Trade("2017-12-31T23:59:52", "1", "100", "EUR"));
        trades.add(new Trade("2018-01-01T00:00:02", "1", "110", "EUR"));
        Set<TradeStatistics2> set = new HashSet<>();
        trades.forEach(t ->
                {
                    set.add(new TradeStatistics2(offer, Price.parse(t.cc, t.price), Coin.parseCoin(t.size), t.date, null));
                }
        );
        ObservableSet<TradeStatistics2> tradeStats = FXCollections.observableSet(set);

        // Run test for each tick type
        for (TradesChartsViewModel.TickUnit tick : TradesChartsViewModel.TickUnit.values()) {
            new Expectations() {{
                tsm.getObservableTradeStatisticsSet();
                result = tradeStats;
            }};

            // Trigger chart update
            model.setTickUnit(tick);
            assertEquals(model.selectedTradeCurrencyProperty.get().getCode(), tradeStats.iterator().next().getCurrencyCode());
            assertEquals(2, model.priceItems.size());
            assertEquals(2, model.volumeItems.size());
        }
    }
}
