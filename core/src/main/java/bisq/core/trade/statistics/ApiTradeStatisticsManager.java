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

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import bisq.common.config.Config;
import bisq.common.file.FileUtil;
import bisq.common.util.Utilities;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import javax.inject.Named;

import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;

import java.nio.file.Paths;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;

@Singleton
@Slf4j
public class ApiTradeStatisticsManager {

    private final P2PService p2PService;
    private final ApiTradeStatisticsStorageService apiTradeStatisticsStorageService;
    private final TradeStatistics3StorageService tradeStatistics3StorageService;
    private final TradeStatisticsManager tradeStatisticsManager;
    private final File storageDir;
    private final boolean dumpStatistics;

    @Getter
    private final ObservableSet<ApiTradeStatistics> observableApiTradeStatisticsSet = FXCollections.observableSet();

    @Inject
    public ApiTradeStatisticsManager(P2PService p2PService,
                                     ApiTradeStatisticsStorageService apiTradeStatisticsStorageService,
                                     TradeStatistics3StorageService tradeStatistics3StorageService,
                                     TradeStatisticsManager tradeStatisticsManager,
                                     AppendOnlyDataStoreService appendOnlyDataStoreService,
                                     @Named(Config.STORAGE_DIR) File storageDir,
                                     @Named(Config.DUMP_STATISTICS) boolean dumpStatistics) {
        this.p2PService = p2PService;
        this.apiTradeStatisticsStorageService = apiTradeStatisticsStorageService;
        this.tradeStatistics3StorageService = tradeStatistics3StorageService;
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.storageDir = storageDir;
        this.dumpStatistics = dumpStatistics;

        appendOnlyDataStoreService.addService(apiTradeStatisticsStorageService);
    }


    public void onAllServicesInitialized() {
        log.info("onAllServicesInitialized");

        // First, set up a ApiTradeStatistics listener for new incoming stats.
        // We do not try to join to the matching TradeStatistics3 payloads until
        // we export statistics.
        p2PService.getP2PDataStorage().addAppendOnlyDataStoreListener(payload -> {
            if (payload instanceof ApiTradeStatistics) {
                ApiTradeStatistics apiTradeStatistics = (ApiTradeStatistics) payload;
                observableApiTradeStatisticsSet.add(apiTradeStatistics);
                log.warn("Added api stats item # {} to set: {}",
                        observableApiTradeStatisticsSet.size(),
                        apiTradeStatistics);
            }
        });

        // Load existing ApiTradeStatistics storage.
        Set<ApiTradeStatistics> set = apiTradeStatisticsStorageService.getMapOfAllData().values().stream()
                .filter(e -> e instanceof ApiTradeStatistics)
                .map(e -> (ApiTradeStatistics) e)
                .filter(s -> s.getTradeStatistics3().isValid())
                .collect(Collectors.toSet());
        observableApiTradeStatisticsSet.addAll(set);

        maybeDumpStatistics();
    }

    // Try to keep JFX dependencies out of API.
    public List<ApiTradeStatistics> getApiTradeStatistics() {
        return Arrays.asList(observableApiTradeStatisticsSet.toArray(new ApiTradeStatistics[0]));
    }

    public void maybeRepublishApiTradeStatistics(String whatDoIDoNow) {
        // See TradeManager L 449:
        //  tradeStatisticsManager.maybeRepublishTradeStatistics(allTrades, referralId, isTorNetworkNode);
    }

    public void shutDown() {
        log.info("shutDown");
        maybeDumpStatistics();
    }


    private void maybeDumpStatistics() {
        if (!dumpStatistics) {
            return;
        }
        exportApiTradeStatisticsToCsv();
    }

    private void exportApiTradeStatisticsToCsv() {
        List<ApiTradeStatistics> dateOrderedStats = getDateOrderedStats();

        File tempFile = null;
        File csvFile = Paths.get(storageDir.getAbsolutePath(), "api_trade_statistics.csv").toFile();
        String header = "isMakerApiUser,isTakerApiUser,currency,price,amount,paymentMethod,date,mediator,refundAgent";
        FileWriter fileWriter = null;
        try {
            tempFile = File.createTempFile("temp_api_trade_statistics", null, storageDir);
            tempFile.deleteOnExit();

            fileWriter = new FileWriter(requireNonNull(tempFile), UTF_8);
            fileWriter.append(header).append("\n");
            for (ApiTradeStatistics item : dateOrderedStats) {
                var tradeStatistics3 = item.getTradeStatistics3();
                item.setTradeStatistics3(tradeStatistics3);
                fileWriter.append(String.valueOf(item.isMakerApiUser())).append(",");
                fileWriter.append(String.valueOf(item.isTakerApiUser())).append(",");
                fileWriter.append(tradeStatistics3.getCurrency()).append(",");
                fileWriter.append(String.valueOf(tradeStatistics3.getPrice())).append(",");
                fileWriter.append(String.valueOf(tradeStatistics3.getAmount())).append(",");
                fileWriter.append(String.valueOf(tradeStatistics3.getPaymentMethodId())).append(",");
                fileWriter.append(String.valueOf(tradeStatistics3.getDate())).append(",");
                var mediator = tradeStatistics3.getMediator();
                if (mediator == null) {
                    fileWriter.append(",");
                } else {
                    fileWriter.append(mediator).append(",");
                }
                var refundAgent = tradeStatistics3.getRefundAgent();
                if (refundAgent == null) {
                    fileWriter.append("\n");
                } else {
                    fileWriter.append(refundAgent).append("\n");
                }
            }
            log.info("Exported {} ApiTradeStatistics to {}.", dateOrderedStats.size(), csvFile.getAbsolutePath());
        } catch (Exception ex) {
            throw new RuntimeException("Fatal csv file write error.", ex);
        } finally {
            try {
                requireNonNull(fileWriter).flush();
                fileWriter.close();
                FileUtil.renameFile(tempFile, csvFile);
                if (tempFile.exists()) {
                    tempFile.delete();
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private List<ApiTradeStatistics> getDateOrderedStats() {
        List<ApiTradeStatistics> orderedStats = new ArrayList<>();

        // Find the matching TradeStatistics3 if missing (join on hash).
        getApiTradeStatistics().stream()
                .filter(apiStats -> apiStats.getTradeStatistics3() == null)
                .forEach(apiStats -> {
                    Optional<TradeStatistics3> tradeStatistics = tradeStatisticsManager.findTradeStatistics3WithHash(
                            apiStats.getTradeStatistics3Hash());
                    tradeStatistics.ifPresentOrElse(s -> {
                        if (s.isValid()) {
                            apiStats.setTradeStatistics3(s);
                            orderedStats.add(apiStats);
                        } else {
                            log.error("Invalid tradeStatistics3: {}", s);
                        }
                    }, () -> {
                        //  We cannot depend on the matching TradeStatistics3's arrival &
                        //  storage before the arrival of this ApiTradeStatistics payload.
                        log.error("TradeStatisticsManager could not find TradeStatistics3 with hash {}",
                                Utilities.encodeToHex(apiStats.getTradeStatistics3Hash()));
                    });
                });

        // Sort valid, complete API trading stats by trade creation date.
        orderedStats.sort(comparing(apiTradeStatistics ->
                apiTradeStatistics.getTradeStatistics3().getDateAsLong()));

        if (observableApiTradeStatisticsSet.size() != orderedStats.size()) {
            log.warn("Could not find {} matching TradeStatistics3 payloads in the set of"
                            + " {} valid, complete ApiTradeStatistics payloads.",
                    observableApiTradeStatisticsSet.size() - orderedStats.size(),
                    orderedStats.size());
        }
        return orderedStats;
    }
}
