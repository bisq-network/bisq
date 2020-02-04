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

package bisq.core.trade.statistics;

import bisq.core.monetary.Price;
import bisq.core.offer.OfferPayload;

import org.bitcoinj.core.Coin;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;

import com.natpryce.makeiteasy.Instantiator;
import com.natpryce.makeiteasy.Maker;
import com.natpryce.makeiteasy.Property;

import static com.natpryce.makeiteasy.MakeItEasy.a;

public class TradeStatistics2Maker {

    public static final Property<TradeStatistics2, Date> date = new Property<>();
    public static final Property<TradeStatistics2, String> depositTxId = new Property<>();
    public static final Property<TradeStatistics2, Coin> tradeAmount = new Property<>();

    public static final Instantiator<TradeStatistics2> TradeStatistic2 = lookup -> {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2016, 3, 19);

        return new TradeStatistics2(
                new OfferPayload("1234",
                        0L,
                        null,
                        null,
                        OfferPayload.Direction.BUY,
                        100000L,
                        0.0,
                        false,
                        100000L,
                        100000L,
                        "BTC",
                        "USD",
                        null,
                        null,
                        "SEPA",
                        "",
                        null,
                        null,
                        null,
                        null,
                        null,
                        "",
                        0L,
                        0L,
                        0L,
                        false,
                        0L,
                        0L,
                        0L,
                        0L,
                        false,
                        false,
                        0L,
                        0L,
                        false,
                        null,
                        null,
                        0),
                Price.valueOf("BTC", 100000L),
                lookup.valueOf(tradeAmount, Coin.SATOSHI),
                lookup.valueOf(date, new Date(calendar.getTimeInMillis())),
                lookup.valueOf(depositTxId, "123456"),
                Collections.emptyMap());
    };
    public static final Maker<TradeStatistics2> dayZeroTrade = a(TradeStatistic2);
}
