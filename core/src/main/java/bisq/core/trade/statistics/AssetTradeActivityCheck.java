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

import bisq.core.dao.governance.asset.AssetService;
import bisq.core.filter.FilterManager;
import bisq.core.locale.CryptoCurrency;
import bisq.core.locale.CurrencyUtil;

import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import com.google.common.base.Joiner;

import java.time.Duration;

import java.text.SimpleDateFormat;

import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * This can be removed once the AssetService is activated with the DAO.
 * At the moment it is only used for printing out trade statistics.
 */
@Slf4j
public class AssetTradeActivityCheck {
    private final AssetService assetService;
    private final TradeStatisticsManager tradeStatisticsManager;
    private final FilterManager filterManager;

    @Inject
    public AssetTradeActivityCheck(AssetService assetService, TradeStatisticsManager tradeStatisticsManager, FilterManager filterManager) {
        this.assetService = assetService;
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.filterManager = filterManager;
    }

    public void onAllServicesInitialized() {
        Date compareDate = new Date(new Date().getTime() - Duration.ofDays(120).toMillis());
        long minTradeAmount = Coin.parseCoin("0.01").value;
        long minNumOfTrades = 3;

        Map<String, Tuple2<Long, Integer>> tradeStatMap = new HashMap<>();
        tradeStatisticsManager.getObservableTradeStatisticsSet().stream()
                .filter(e -> !e.getBaseCurrency().equals("BTC"))
                .filter(e -> CurrencyUtil.isCryptoCurrency(e.getBaseCurrency()))
                .filter(e -> e.getTradeDate().getTime() > compareDate.getTime())
                .forEach(e -> {
                    tradeStatMap.putIfAbsent(e.getBaseCurrency(), new Tuple2<>(0L, 0));
                    Tuple2<Long, Integer> tuple2 = tradeStatMap.get(e.getBaseCurrency());
                    long accumulatedTradeAmount = tuple2.first + e.getTradeAmount().getValue();
                    int numTrades = tuple2.second + 1;
                    tradeStatMap.put(e.getBaseCurrency(), new Tuple2<>(accumulatedTradeAmount, numTrades));
                });
        StringBuilder newAssets = new StringBuilder("\nNew assets (in warming up phase):");
        StringBuilder sufficientlyTraded = new StringBuilder("\nSufficiently traded assets:");
        StringBuilder insufficientlyTraded = new StringBuilder("\nInsufficiently traded assets:");
        StringBuilder notTraded = new StringBuilder("\nNot traded assets:");
        List<CryptoCurrency> whiteListedSortedCryptoCurrencies = CurrencyUtil.getActiveSortedCryptoCurrencies(assetService, filterManager);
        Set<CryptoCurrency> assetsToRemove = new HashSet<>(whiteListedSortedCryptoCurrencies);
        whiteListedSortedCryptoCurrencies.forEach(e -> {
            String code = e.getCode();
            String nameAndCode = CurrencyUtil.getNameAndCode(code);
            long tradeAmount = 0;
            int numTrades = 0;
            boolean isInTradeStatMap = tradeStatMap.containsKey(code);
            if (isInTradeStatMap) {
                Tuple2<Long, Integer> tuple = tradeStatMap.get(code);
                tradeAmount = tuple.first;
                numTrades = tuple.second;
            }

            if (isWarmingUp(code)) {
                assetsToRemove.remove(e);
                newAssets.append("\n")
                        .append(nameAndCode)
                        .append(": Trade amount: ")
                        .append(Coin.valueOf(tradeAmount).toFriendlyString())
                        .append(", number of trades: ")
                        .append(numTrades);
            }

            if (!isWarmingUp(code) /*&& !hasPaidBSQFee(code)*/) {
                if (isInTradeStatMap) {
                    if (tradeAmount >= minTradeAmount || numTrades >= minNumOfTrades) {
                        assetsToRemove.remove(e);
                        sufficientlyTraded.append("\n")
                                .append(nameAndCode)
                                .append(": Trade amount: ")
                                .append(Coin.valueOf(tradeAmount).toFriendlyString())
                                .append(", number of trades: ")
                                .append(numTrades);
                    } else {
                        insufficientlyTraded.append("\n")
                                .append(nameAndCode)
                                .append(": Trade amount: ")
                                .append(Coin.valueOf(tradeAmount).toFriendlyString())
                                .append(", number of trades: ")
                                .append(numTrades);
                    }
                } else {
                    notTraded.append("\n").append(nameAndCode);
                }
            }
        });
        List<CryptoCurrency> assetsToRemoveList = assetsToRemove.stream()
                .sorted(Comparator.comparing(CryptoCurrency::getCode))
                .collect(Collectors.toList());

        String result = "Date for checking trade activity: " + new SimpleDateFormat("yyyy-MM-dd'T'").format(compareDate) +
                "\n\nAssets to remove (" + assetsToRemoveList.size() + "):\n" + Joiner.on("\n").join(assetsToRemoveList) +
                "\n\n" + insufficientlyTraded.toString() +
                "\n\n" + notTraded.toString() +
                "\n\n" + newAssets.toString() +
                "\n\n" + sufficientlyTraded.toString();
        // Utilities.copyToClipboard(result);
        log.trace(result);
    }

    private boolean isWarmingUp(String code) {
        Set<String> newlyAdded = new HashSet<>();

        // v0.8.0 Aug 22 2018
        // none added

        // v0.9.0 (3.12.2018)
        newlyAdded.add("ACM");
        newlyAdded.add("BLUR");
        newlyAdded.add("CHA");
        newlyAdded.add("CROAT");
        newlyAdded.add("DRGL");
        newlyAdded.add("ETHS");
        newlyAdded.add("KEK");
        newlyAdded.add("MASK");
        newlyAdded.add("MBGL");
        newlyAdded.add("MOX");
        newlyAdded.add("MUE");
        newlyAdded.add("NEOS");
        newlyAdded.add("PZDC");
        newlyAdded.add("QMCoin");
        newlyAdded.add("RADS");
        newlyAdded.add("RYO");
        newlyAdded.add("SUB1X");
        newlyAdded.add("MAI");
        newlyAdded.add("TRTL");
        newlyAdded.add("ZER");
        newlyAdded.add("XRC");

        // v0.9.2 (Jan 8 2019)
        newlyAdded.add("AEON");
        newlyAdded.add("BEAM");
        newlyAdded.add("BTM");
        newlyAdded.add("DXO");
        newlyAdded.add("FRTY");
        newlyAdded.add("GMCN");
        newlyAdded.add("GRIN");
        newlyAdded.add("ZEN");
        newlyAdded.add("IDA");
        newlyAdded.add("IRD");
        newlyAdded.add("NOR");
        newlyAdded.add("PINK");
        newlyAdded.add("QBS");
        newlyAdded.add("RMX");
        newlyAdded.add("SCP");
        newlyAdded.add("SPACE");
        newlyAdded.add("UCC");
        newlyAdded.add("WEB");
        newlyAdded.add("WRKZ");

        // v0.9.3 - nothing added, was hotfix

        // v0.9.4 (Feb 18 2019)
        newlyAdded.add("ADE");
        newlyAdded.add("ASK");
        newlyAdded.add("AEUR");
        newlyAdded.add("AUS");
        newlyAdded.add("CASH2");
        newlyAdded.add("DARX");
        newlyAdded.add("CRDS");
        newlyAdded.add("CRCL");
        newlyAdded.add("DAI");
        newlyAdded.add("ONION");
        newlyAdded.add("FJC");
        newlyAdded.add("LYTX");
        newlyAdded.add("LTZ");
        newlyAdded.add("MILE");
        newlyAdded.add("PRSN");
        newlyAdded.add("TUSD");
        newlyAdded.add("USDC");
        newlyAdded.add("VXV");
        newlyAdded.add("ZEL");

        return newlyAdded.contains(code);
    }
}
