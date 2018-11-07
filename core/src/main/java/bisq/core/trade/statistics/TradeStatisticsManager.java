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

import bisq.core.app.AppOptionKeys;
import bisq.core.dao.governance.asset.AssetService;
import bisq.core.locale.CryptoCurrency;
import bisq.core.locale.CurrencyTuple;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.Trade;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import bisq.common.UserThread;
import bisq.common.storage.JsonFileManager;
import bisq.common.storage.Storage;
import bisq.common.util.Tuple2;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;

import java.time.Duration;

import java.io.File;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class TradeStatisticsManager {

    private final JsonFileManager jsonFileManager;
    private final P2PService p2PService;
    private final PriceFeedService priceFeedService;
    private final ReferralIdService referralIdService;
    private final AssetService assetService;
    private final boolean dumpStatistics;
    private final ObservableSet<TradeStatistics2> observableTradeStatisticsSet = FXCollections.observableSet();

    @Inject
    public TradeStatisticsManager(P2PService p2PService,
                                  PriceFeedService priceFeedService,
                                  TradeStatistics2StorageService tradeStatistics2StorageService,
                                  AppendOnlyDataStoreService appendOnlyDataStoreService,
                                  ReferralIdService referralIdService,
                                  AssetService assetService,
                                  @Named(Storage.STORAGE_DIR) File storageDir,
                                  @Named(AppOptionKeys.DUMP_STATISTICS) boolean dumpStatistics) {
        this.p2PService = p2PService;
        this.priceFeedService = priceFeedService;
        this.referralIdService = referralIdService;
        this.assetService = assetService;
        this.dumpStatistics = dumpStatistics;
        jsonFileManager = new JsonFileManager(storageDir);

        appendOnlyDataStoreService.addService(tradeStatistics2StorageService);
    }

    public void onAllServicesInitialized() {
        if (dumpStatistics) {
            ArrayList<CurrencyTuple> fiatCurrencyList = new ArrayList<>(CurrencyUtil.getAllSortedFiatCurrencies().stream()
                    .map(e -> new CurrencyTuple(e.getCode(), e.getName(), 8))
                    .collect(Collectors.toList()));
            jsonFileManager.writeToDisc(Utilities.objectToJson(fiatCurrencyList), "fiat_currency_list");

            ArrayList<CurrencyTuple> cryptoCurrencyList = new ArrayList<>(CurrencyUtil.getAllSortedCryptoCurrencies().stream()
                    .map(e -> new CurrencyTuple(e.getCode(), e.getName(), 8))
                    .collect(Collectors.toList()));
            cryptoCurrencyList.add(0, new CurrencyTuple(Res.getBaseCurrencyCode(), Res.getBaseCurrencyName(), 8));
            jsonFileManager.writeToDisc(Utilities.objectToJson(cryptoCurrencyList), "crypto_currency_list");
        }

        p2PService.getP2PDataStorage().addAppendOnlyDataStoreListener(payload -> {
            if (payload instanceof TradeStatistics2)
                addToMap((TradeStatistics2) payload, true);
        });

        Map<String, TradeStatistics2> map = new HashMap<>();
        p2PService.getP2PDataStorage().getAppendOnlyDataStoreMap().values().stream()
                .filter(e -> e instanceof TradeStatistics2)
                .forEach(e -> addToMap((TradeStatistics2) e, map));
        observableTradeStatisticsSet.addAll(map.values());

        priceFeedService.applyLatestBisqMarketPrice(observableTradeStatisticsSet);
        dump();

        checkTradeActivity();
    }

    public void publishTradeStatistics(List<Trade> trades) {
        for (int i = 0; i < trades.size(); i++) {
            Trade trade = trades.get(i);

            Map<String, String> extraDataMap = null;
            if (referralIdService.getOptionalReferralId().isPresent()) {
                extraDataMap = new HashMap<>();
                extraDataMap.put(OfferPayload.REFERRAL_ID, referralIdService.getOptionalReferralId().get());
            }
            Offer offer = trade.getOffer();
            checkNotNull(offer, "offer must not ne null");
            checkNotNull(trade.getTradeAmount(), "trade.getTradeAmount() must not ne null");
            TradeStatistics2 tradeStatistics = new TradeStatistics2(offer.getOfferPayload(),
                    trade.getTradePrice(),
                    trade.getTradeAmount(),
                    trade.getDate(),
                    (trade.getDepositTx() != null ? trade.getDepositTx().getHashAsString() : ""),
                    extraDataMap);
            addToMap(tradeStatistics, true);

            // We only republish trades from last 10 days
            if ((new Date().getTime() - trade.getDate().getTime()) < TimeUnit.DAYS.toMillis(10)) {
                long delay = 5000;
                long minDelay = (i + 1) * delay;
                long maxDelay = (i + 2) * delay;
                UserThread.runAfterRandomDelay(() -> {
                    p2PService.addPersistableNetworkPayload(tradeStatistics, true);
                }, minDelay, maxDelay, TimeUnit.MILLISECONDS);
            }
        }
    }

    public void addToMap(TradeStatistics2 tradeStatistics, boolean storeLocally) {
        if (!observableTradeStatisticsSet.contains(tradeStatistics)) {
            boolean itemAlreadyAdded = observableTradeStatisticsSet.stream()
                    .anyMatch(e -> (e.getOfferId().equals(tradeStatistics.getOfferId())));
            if (!itemAlreadyAdded) {
                observableTradeStatisticsSet.add(tradeStatistics);
                if (storeLocally) {
                    priceFeedService.applyLatestBisqMarketPrice(observableTradeStatisticsSet);
                    dump();
                }
            } else {
                log.debug("We have already an item with the same offer ID. That might happen if both the maker and the taker published the tradeStatistics");
            }
        }
    }

    public void addToMap(TradeStatistics2 tradeStatistics, Map<String, TradeStatistics2> map) {
        TradeStatistics2 prevValue = map.putIfAbsent(tradeStatistics.getOfferId(), tradeStatistics);
        if (prevValue != null)
            log.debug("We have already an item with the same offer ID. That might happen if both the maker and the taker published the tradeStatistics");
    }

    public ObservableSet<TradeStatistics2> getObservableTradeStatisticsSet() {
        return observableTradeStatisticsSet;
    }

    private void dump() {
        if (dumpStatistics) {
            // We store the statistics as json so it is easy for further processing (e.g. for web based services)
            // TODO This is just a quick solution for storing to one file.
            // 1 statistic entry has 500 bytes as json.
            // Need a more scalable solution later when we get more volume.
            // The flag will only be activated by dedicated nodes, so it should not be too critical for the moment, but needs to
            // get improved. Maybe a LevelDB like DB...? Could be impl. in a headless version only.
            List<TradeStatisticsForJson> list = observableTradeStatisticsSet.stream().map(TradeStatisticsForJson::new).collect(Collectors.toList());
            list.sort((o1, o2) -> (o1.tradeDate < o2.tradeDate ? 1 : (o1.tradeDate == o2.tradeDate ? 0 : -1)));
            TradeStatisticsForJson[] array = new TradeStatisticsForJson[list.size()];
            list.toArray(array);
            jsonFileManager.writeToDisc(Utilities.objectToJson(array), "trade_statistics");
        }
    }

    private void checkTradeActivity() {
        Date compareDate = new Date(new Date().getTime() - Duration.ofDays(120).toMillis());
        long minTradeAmount = Coin.parseCoin("0.01").value;
        long minNumOfTrades = 3;

        Map<String, Tuple2<Long, Integer>> tradeStatMap = new HashMap<>();
        observableTradeStatisticsSet.stream()
                .filter(e -> CurrencyUtil.isCryptoCurrency(e.getBaseCurrency()))
                .filter(e -> e.getTradeDate().getTime() > compareDate.getTime())
                .forEach(e -> {
                    tradeStatMap.putIfAbsent(e.getBaseCurrency(), new Tuple2<>(0L, 0));
                    Tuple2<Long, Integer> tuple2 = tradeStatMap.get(e.getBaseCurrency());
                    long accumulatedTradeAmount = tuple2.first + e.getTradeAmount().getValue();
                    int numTrades = tuple2.second + 1;
                    tradeStatMap.put(e.getBaseCurrency(), new Tuple2<>(accumulatedTradeAmount, numTrades));
                });
        StringBuilder sufficientlyTraded = new StringBuilder("\nSufficiently traded assets:");
        StringBuilder insufficientlyTraded = new StringBuilder("\nInsufficiently traded assets:");
        StringBuilder notTraded = new StringBuilder("\nNot traded assets:");
        List<CryptoCurrency> whiteListedSortedCryptoCurrencies = CurrencyUtil.getWhiteListedSortedCryptoCurrencies(assetService);
        Set<CryptoCurrency> assetsToRemove = new HashSet<>(whiteListedSortedCryptoCurrencies);
        whiteListedSortedCryptoCurrencies.forEach(e -> {
            String code = e.getCode();
            if (!isWarmingUp(code) && !hasPaidBSQFee(code)) {
                String nameAndCode = CurrencyUtil.getNameAndCode(code);
                if (tradeStatMap.containsKey(code)) {
                    Tuple2<Long, Integer> tuple = tradeStatMap.get(code);
                    Long tradeAmount = tuple.first;
                    Integer numTrades = tuple.second;
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
                    assetsToRemove.remove(e);
                    notTraded.append("\n").append(nameAndCode);
                }
            }
        });

        log.debug(sufficientlyTraded.toString());
        log.debug(insufficientlyTraded.toString());
        log.debug(notTraded.toString());
    }

    private boolean hasPaidBSQFee(String code) {
        return assetService.hasPaidBSQFee(code);
    }

    private boolean isWarmingUp(String code) {
        Set<String> newlyAdded = new HashSet<>();

        // v0.7.1 Jul 4 2018
        newlyAdded.add("ZOC");
        newlyAdded.add("AQUA");
        newlyAdded.add("BTDX");
        newlyAdded.add("BTCC");
        newlyAdded.add("BTI");
        newlyAdded.add("CRDS");
        newlyAdded.add("CNMC");
        newlyAdded.add("TARI");
        newlyAdded.add("DAC");
        newlyAdded.add("DRIP");
        newlyAdded.add("FTO");
        newlyAdded.add("GRFT");
        newlyAdded.add("LIKE");
        newlyAdded.add("LOBS");
        newlyAdded.add("MAX");
        newlyAdded.add("MEC");
        newlyAdded.add("MCC");
        newlyAdded.add("XMN");
        newlyAdded.add("XMY");
        newlyAdded.add("NANO");
        newlyAdded.add("NPW");
        newlyAdded.add("NIM");
        newlyAdded.add("PIX");
        newlyAdded.add("PXL");
        newlyAdded.add("PRIV");
        newlyAdded.add("TRIT");
        newlyAdded.add("WAVI");

        // v0.8.0 Aug 22 2018
        // none added

        return newlyAdded.contains(code);
    }
}
