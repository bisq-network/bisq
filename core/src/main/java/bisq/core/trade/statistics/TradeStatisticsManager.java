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

import bisq.core.locale.CurrencyTuple;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.provider.price.PriceFeedService;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import bisq.common.config.Config;
import bisq.common.file.JsonFileManager;
import bisq.common.util.Utilities;

import com.google.inject.Inject;

import javax.inject.Named;
import javax.inject.Singleton;

import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;

import java.io.File;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class TradeStatisticsManager {
    private final P2PService p2PService;
    private final PriceFeedService priceFeedService;
    private final TradeStatistics3StorageService tradeStatistics3StorageService;
    private final TradeStatisticsConverter tradeStatisticsConverter;
    private final File storageDir;
    private final boolean dumpStatistics;
    private final ObservableSet<TradeStatistics3> observableTradeStatisticsSet = FXCollections.observableSet();
    private JsonFileManager jsonFileManager;

    @Inject
    public TradeStatisticsManager(P2PService p2PService,
                                  PriceFeedService priceFeedService,
                                  TradeStatistics3StorageService tradeStatistics3StorageService,
                                  AppendOnlyDataStoreService appendOnlyDataStoreService,
                                  TradeStatisticsConverter tradeStatisticsConverter,
                                  @Named(Config.STORAGE_DIR) File storageDir,
                                  @Named(Config.DUMP_STATISTICS) boolean dumpStatistics) {
        this.p2PService = p2PService;
        this.priceFeedService = priceFeedService;
        this.tradeStatistics3StorageService = tradeStatistics3StorageService;
        this.tradeStatisticsConverter = tradeStatisticsConverter;
        this.storageDir = storageDir;
        this.dumpStatistics = dumpStatistics;


        appendOnlyDataStoreService.addService(tradeStatistics3StorageService);
    }

    public void shutDown() {
        tradeStatisticsConverter.shutDown();
        if (jsonFileManager != null) {
            jsonFileManager.shutDown();
        }
    }

    public void onAllServicesInitialized() {
        p2PService.getP2PDataStorage().addAppendOnlyDataStoreListener(payload -> {
            if (payload instanceof TradeStatistics3) {
                TradeStatistics3 tradeStatistics = (TradeStatistics3) payload;
                if (!tradeStatistics.isValid()) {
                    return;
                }
                observableTradeStatisticsSet.add(tradeStatistics);
                priceFeedService.applyLatestBisqMarketPrice(observableTradeStatisticsSet);
                maybeDumpStatistics();
            }
        });

        Set<TradeStatistics3> set = tradeStatistics3StorageService.getMapOfAllData().values().stream()
                .filter(e -> e instanceof TradeStatistics3)
                .map(e -> (TradeStatistics3) e)
                .filter(TradeStatistics3::isValid)
                .collect(Collectors.toSet());
        observableTradeStatisticsSet.addAll(set);
        priceFeedService.applyLatestBisqMarketPrice(observableTradeStatisticsSet);
        maybeDumpStatistics();
    }

    public ObservableSet<TradeStatistics3> getObservableTradeStatisticsSet() {
        return observableTradeStatisticsSet;
    }

    private void maybeDumpStatistics() {
        if (!dumpStatistics) {
            return;
        }

        if (jsonFileManager == null) {
            jsonFileManager = new JsonFileManager(storageDir);

            // We only dump once the currencies as they do not change during runtime
            ArrayList<CurrencyTuple> fiatCurrencyList = CurrencyUtil.getAllSortedFiatCurrencies().stream()
                    .map(e -> new CurrencyTuple(e.getCode(), e.getName(), 8))
                    .collect(Collectors.toCollection(ArrayList::new));
            jsonFileManager.writeToDiscThreaded(Utilities.objectToJson(fiatCurrencyList), "fiat_currency_list");

            ArrayList<CurrencyTuple> cryptoCurrencyList = CurrencyUtil.getAllSortedCryptoCurrencies().stream()
                    .map(e -> new CurrencyTuple(e.getCode(), e.getName(), 8))
                    .collect(Collectors.toCollection(ArrayList::new));
            cryptoCurrencyList.add(0, new CurrencyTuple(Res.getBaseCurrencyCode(), Res.getBaseCurrencyName(), 8));
            jsonFileManager.writeToDiscThreaded(Utilities.objectToJson(cryptoCurrencyList), "crypto_currency_list");
        }

        List<TradeStatisticsForJson> list = observableTradeStatisticsSet.stream()
                .map(TradeStatisticsForJson::new)
                .sorted((o1, o2) -> (Long.compare(o2.tradeDate, o1.tradeDate)))
                .collect(Collectors.toList());
        TradeStatisticsForJson[] array = new TradeStatisticsForJson[list.size()];
        list.toArray(array);
        jsonFileManager.writeToDiscThreaded(Utilities.objectToJson(array), "trade_statistics");
    }
}
