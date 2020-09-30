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

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import bisq.common.config.Config;
import bisq.common.storage.FileUtil;

import com.google.inject.Inject;

import javax.inject.Named;

import java.io.File;
import java.io.IOException;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TradeStatisticsConverter {
    private final P2PDataStorage p2PDataStorage;
    private final TradeStatistics2StorageService tradeStatistics2StorageService;
    private final TradeStatistics3StorageService tradeStatistics3StorageService;
    private final AppendOnlyDataStoreService appendOnlyDataStoreService;
    private final File tradeStatistics2Store;

    @Inject
    public TradeStatisticsConverter(P2PDataStorage p2PDataStorage,
                                    TradeStatistics2StorageService tradeStatistics2StorageService,
                                    TradeStatistics3StorageService tradeStatistics3StorageService,
                                    AppendOnlyDataStoreService appendOnlyDataStoreService,
                                    @Named(Config.STORAGE_DIR) File storageDir
    ) {
        this.p2PDataStorage = p2PDataStorage;
        this.tradeStatistics2StorageService = tradeStatistics2StorageService;
        this.tradeStatistics3StorageService = tradeStatistics3StorageService;
        this.appendOnlyDataStoreService = appendOnlyDataStoreService;
        tradeStatistics2Store = new File(storageDir, "TradeStatistics2Store");
        if (tradeStatistics2Store.exists()) {
            appendOnlyDataStoreService.addService(tradeStatistics2StorageService);
        }
    }

    public void onAllServicesInitialized() {
        convertExistingStore();

        // We listen to old TradeStatistics2 objects, convert and store them and rebroadcast.
        p2PDataStorage.addAppendOnlyDataStoreListener(payload -> {
            if (payload instanceof TradeStatistics2) {
                TradeStatistics3 tradeStatistics3 = convertToTradeStatistics3((TradeStatistics2) payload);
                // We add it to the p2PDataStorage, which handles to get the data stored in the maps and maybe
                // re-broadcast as tradeStatistics3 object if not already received.
                p2PDataStorage.addPersistableNetworkPayload(tradeStatistics3, null, true);
            }
        });
    }

    protected void convertExistingStore() {
        if (tradeStatistics2Store.exists()) {
            Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> map = tradeStatistics3StorageService.getMap();
            tradeStatistics2StorageService.getMapOfAllData().values().stream()
                    .filter(e -> e instanceof TradeStatistics2)
                    .map(e -> (TradeStatistics2) e)
                    .map(this::convertToTradeStatistics3)
                    .forEach(e -> appendOnlyDataStoreService.put(new P2PDataStorage.ByteArray(e.getHash()), e));
            try {
                FileUtil.deleteFileIfExists(tradeStatistics2Store);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    protected TradeStatistics3 convertToTradeStatistics3(TradeStatistics2 e) {
        Map<String, String> extraDataMap = e.getExtraDataMap();
        String mediator = extraDataMap != null ? extraDataMap.get(TradeStatistics2.MEDIATOR_ADDRESS) : null;
        String refundAgent = extraDataMap != null ? extraDataMap.get(TradeStatistics2.REFUND_AGENT_ADDRESS) : null;
        return new TradeStatistics3(e.getCurrencyCode(),
                e.getTradePrice().getValue(),
                e.getTradeAmount().getValue(),
                e.getOfferPaymentMethod(),
                e.getTradeDate().getTime(),
                mediator,
                refundAgent,
                null);
    }
}
