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

package bisq.core.util;

import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Price;
import bisq.core.trade.statistics.TradeStatistics3;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;

import bisq.common.util.MathUtils;
import bisq.common.util.RangeUtils;
import bisq.common.util.Tuple2;

import org.bitcoinj.utils.Fiat;

import com.google.common.collect.Range;
import com.google.common.primitives.Doubles;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AveragePriceUtil {
    private static final double HOW_MANY_STD_DEVS_CONSTITUTE_OUTLIER = 10;

    public static Tuple2<Price, Price> getAveragePriceTuple(Preferences preferences,
                                                            TradeStatisticsManager tradeStatisticsManager,
                                                            int days) {
        return getAveragePriceTuple(preferences, tradeStatisticsManager, days, new Date());
    }

    public static Tuple2<Price, Price> getAveragePriceTuple(Preferences preferences,
                                                            TradeStatisticsManager tradeStatisticsManager,
                                                            int days,
                                                            Date date) {
        Date pastXDays = getPastDate(days, date);
        return getAveragePriceTuple(preferences, tradeStatisticsManager, pastXDays, date);
    }

    public static Tuple2<Price, Price> getAveragePriceTuple(Preferences preferences,
                                                            TradeStatisticsManager tradeStatisticsManager,
                                                            Date pastXDays,
                                                            Date date) {
        double percentToTrim = Math.max(0, Math.min(49, preferences.getBsqAverageTrimThreshold() * 100));
        Set<TradeStatistics3> allTradePastXDays = RangeUtils.subSet(tradeStatisticsManager.getNavigableTradeStatisticsSet())
                .withKey(TradeStatistics3::getDate)
                .overRange(Range.open(pastXDays, date));

        Map<Boolean, List<TradeStatistics3>> bsqUsdAllTradePastXDays = allTradePastXDays.stream()
                .filter(e -> e.getCurrency().equals("USD") || e.getCurrency().equals("BSQ"))
                .collect(Collectors.partitioningBy(e -> e.getCurrency().equals("USD")));

        List<TradeStatistics3> bsqTradePastXDays = percentToTrim > 0 ?
                removeOutliers(bsqUsdAllTradePastXDays.get(false), percentToTrim) :
                bsqUsdAllTradePastXDays.get(false);

        List<TradeStatistics3> usdTradePastXDays = percentToTrim > 0 ?
                removeOutliers(bsqUsdAllTradePastXDays.get(true), percentToTrim) :
                bsqUsdAllTradePastXDays.get(true);

        Price usdPrice = Price.valueOf("USD", getUSDAverage(bsqTradePastXDays, usdTradePastXDays));
        Price bsqPrice = Price.valueOf("BSQ", getBTCAverage(bsqTradePastXDays));
        return new Tuple2<>(usdPrice, bsqPrice);
    }

    private static List<TradeStatistics3> removeOutliers(List<TradeStatistics3> list, double percentToTrim) {
        List<Double> yValues = Doubles.asList(list.stream()
                .filter(TradeStatistics3::isValid)
                .mapToDouble(TradeStatistics3::getPrice)
                .toArray());

        Tuple2<Double, Double> tuple = InlierUtil.findInlierRange(yValues, percentToTrim, HOW_MANY_STD_DEVS_CONSTITUTE_OUTLIER);
        double lowerBound = tuple.first;
        double upperBound = tuple.second;
        return list.stream()
                .filter(e -> (double) e.getPrice() >= lowerBound)
                .filter(e -> (double) e.getPrice() <= upperBound)
                .collect(Collectors.toList());
    }

    private static long getBTCAverage(List<TradeStatistics3> list) {
        long accumulatedVolume = 0;
        long accumulatedAmount = 0;

        for (TradeStatistics3 item : list) {
            accumulatedVolume += item.getTradeVolume().getValue();
            accumulatedAmount += item.getTradeAmount().getValue(); // Amount of BTC traded
        }
        long averagePrice;
        double accumulatedAmountAsDouble = MathUtils.scaleUpByPowerOf10((double) accumulatedAmount, Altcoin.SMALLEST_UNIT_EXPONENT);
        averagePrice = accumulatedVolume > 0 ? MathUtils.roundDoubleToLong(accumulatedAmountAsDouble / (double) accumulatedVolume) : 0;

        return averagePrice;
    }

    private static long getUSDAverage(List<TradeStatistics3> sortedBsqList, List<TradeStatistics3> sortedUsdList) {
        // Use next USD/BTC print as price to calculate BSQ/USD rate
        // Store each trade as amount of USD and amount of BSQ traded
        List<Tuple2<Double, Double>> usdBsqList = new ArrayList<>(sortedBsqList.size());
        var usdBTCPrice = 10000d; // Default to 10000 USD per BTC if there is no USD feed at all

        int i = 0;
        for (TradeStatistics3 item : sortedBsqList) {
            // Find usd price for trade item
            for (; i < sortedUsdList.size(); i++) {
                TradeStatistics3 usd = sortedUsdList.get(i);
                if (usd.getDateAsLong() > item.getDateAsLong()) {
                    usdBTCPrice = MathUtils.scaleDownByPowerOf10((double) usd.getTradePrice().getValue(),
                            Fiat.SMALLEST_UNIT_EXPONENT);
                    break;
                }
            }
            var bsqAmount = MathUtils.scaleDownByPowerOf10((double) item.getTradeVolume().getValue(),
                    Altcoin.SMALLEST_UNIT_EXPONENT);
            var btcAmount = MathUtils.scaleDownByPowerOf10((double) item.getTradeAmount().getValue(),
                    Altcoin.SMALLEST_UNIT_EXPONENT);
            usdBsqList.add(new Tuple2<>(usdBTCPrice * btcAmount, bsqAmount));
        }
        long averagePrice;
        var usdTraded = usdBsqList.stream()
                .mapToDouble(item -> item.first)
                .sum();
        var bsqTraded = usdBsqList.stream()
                .mapToDouble(item -> item.second)
                .sum();
        var averageAsDouble = bsqTraded > 0 ? usdTraded / bsqTraded : 0d;
        var averageScaledUp = MathUtils.scaleUpByPowerOf10(averageAsDouble, Fiat.SMALLEST_UNIT_EXPONENT);
        averagePrice = bsqTraded > 0 ? MathUtils.roundDoubleToLong(averageScaledUp) : 0;

        return averagePrice;
    }

    private static Date getPastDate(int days, Date date) {
        Calendar cal = new GregorianCalendar();
        cal.setTime(date);
        cal.add(Calendar.DAY_OF_MONTH, -1 * days);
        return cal.getTime();
    }
}
