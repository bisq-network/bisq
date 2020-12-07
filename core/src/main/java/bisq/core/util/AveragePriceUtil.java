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
import bisq.common.util.Tuple2;

import org.bitcoinj.utils.Fiat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.stream.Collectors;

public class AveragePriceUtil {
    private static final double HOW_MANY_STD_DEVS_CONSTITUTE_OUTLIER = 10;

    public static Tuple2<Price, Price> getAveragePriceTuple(Preferences preferences,
                                                            TradeStatisticsManager tradeStatisticsManager,
                                                            int days) {
        double percentToTrim = Math.max(0, Math.min(49, preferences.getBsqAverageTrimThreshold() * 100));
        Date pastXDays = getPastDate(days);
        List<TradeStatistics3> bsqAllTradePastXDays = tradeStatisticsManager.getObservableTradeStatisticsSet().stream()
                .filter(e -> e.getCurrency().equals("BSQ"))
                .filter(e -> e.getDate().after(pastXDays))
                .collect(Collectors.toList());
        List<TradeStatistics3> bsqTradePastXDays = percentToTrim > 0 ?
                removeOutliers(bsqAllTradePastXDays, percentToTrim) :
                bsqAllTradePastXDays;

        List<TradeStatistics3> usdAllTradePastXDays = tradeStatisticsManager.getObservableTradeStatisticsSet().stream()
                .filter(e -> e.getCurrency().equals("USD"))
                .filter(e -> e.getDate().after(pastXDays))
                .collect(Collectors.toList());
        List<TradeStatistics3> usdTradePastXDays = percentToTrim > 0 ?
                removeOutliers(usdAllTradePastXDays, percentToTrim) :
                usdAllTradePastXDays;

        Price usdPrice = Price.valueOf("USD", getUSDAverage(bsqTradePastXDays, usdTradePastXDays));
        Price bsqPrice = Price.valueOf("BSQ", getBTCAverage(bsqTradePastXDays));
        return new Tuple2<>(usdPrice, bsqPrice);
    }

    private static List<TradeStatistics3> removeOutliers(List<TradeStatistics3> list, double percentToTrim) {
        List<Double> yValues = list.stream()
                .filter(TradeStatistics3::isValid)
                .map(e -> (double) e.getPrice())
                .collect(Collectors.toList());

        Tuple2<Double, Double> tuple = InlierUtil.findInlierRange(yValues, percentToTrim, HOW_MANY_STD_DEVS_CONSTITUTE_OUTLIER);
        double lowerBound = tuple.first;
        double upperBound = tuple.second;
        return list.stream()
                .filter(e -> e.getPrice() > lowerBound)
                .filter(e -> e.getPrice() < upperBound)
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

    private static long getUSDAverage(List<TradeStatistics3> bsqList, List<TradeStatistics3> usdList) {
        // Use next USD/BTC print as price to calculate BSQ/USD rate
        // Store each trade as amount of USD and amount of BSQ traded
        List<Tuple2<Double, Double>> usdBsqList = new ArrayList<>(bsqList.size());
        usdList.sort(Comparator.comparing(TradeStatistics3::getDateAsLong));
        var usdBTCPrice = 10000d; // Default to 10000 USD per BTC if there is no USD feed at all

        for (TradeStatistics3 item : bsqList) {
            // Find usdprice for trade item
            usdBTCPrice = usdList.stream()
                    .filter(usd -> usd.getDateAsLong() > item.getDateAsLong())
                    .map(usd -> MathUtils.scaleDownByPowerOf10((double) usd.getTradePrice().getValue(),
                            Fiat.SMALLEST_UNIT_EXPONENT))
                    .findFirst()
                    .orElse(usdBTCPrice);
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

    private static Date getPastDate(int days) {
        Calendar cal = new GregorianCalendar();
        cal.setTime(new Date());
        cal.add(Calendar.DAY_OF_MONTH, -1 * days);
        return cal.getTime();
    }
}
