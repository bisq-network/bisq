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

package bisq.core.dao.burningman;

import bisq.core.dao.DaoSetupService;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.util.JsonUtil;

import bisq.common.config.Config;
import bisq.common.file.JsonFileManager;

import com.google.inject.Inject;

import javax.inject.Named;
import javax.inject.Singleton;

import java.io.File;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class BurningManDataExportService implements DaoSetupService, DaoStateListener {
    private static final String FILE_NAME_FORMAT = BurningManAddressListService.FILE_NAME_PREFIX + "%04d";

    private final DaoStateService daoStateService;
    private final BurningManService burningManService;
    private final BurningManAddressListService burningManAddressListService;
    private final boolean dumpBurningManData;
    private final JsonFileManager jsonFileManager;

    @Inject
    public BurningManDataExportService(DaoStateService daoStateService,
                                       BurningManService burningManService,
                                       BurningManAddressListService burningManAddressListService,
                                       @Named(Config.APP_DATA_DIR) File appDataDir,
                                       @Named(Config.DUMP_BURNING_MAN_DATA) boolean dumpBurningManData) {
        this.daoStateService = daoStateService;
        this.burningManService = burningManService;
        this.burningManAddressListService = burningManAddressListService;
        this.dumpBurningManData = dumpBurningManData;
        jsonFileManager = new JsonFileManager(appDataDir);
    }

    @Override
    public void addListeners() {
        if (dumpBurningManData) {
            daoStateService.addDaoStateListener(this);
        }
    }

    @Override
    public void start() {
        maybeExport();
    }

    @Override
    public void onParseBlockChainComplete() {
        maybeExport();
    }

    private void maybeExport() {
        if (!dumpBurningManData || !daoStateService.isParseBlockChainComplete()) {
            return;
        }

        int chainHeight = daoStateService.getChainHeight();
        int burningManSelectionHeight = DelayedPayoutTxReceiverService.getSnapshotHeight(
                daoStateService.getGenesisBlockHeight(),
                chainHeight,
                DelayedPayoutTxReceiverService.SNAPSHOT_SELECTION_GRID_SIZE,
                DelayedPayoutTxReceiverService.MIN_SNAPSHOT_HEIGHT);
        int listVersion = burningManAddressListService.getNextVersion();
        List<BurningManAddressList.Entry> entries = getEntries(burningManSelectionHeight);
        BurningManAddressList burningManAddressList = new BurningManAddressList(BurningManAddressList.SCHEMA_VERSION,
                listVersion,
                Config.baseCurrencyNetwork().name(),
                chainHeight,
                burningManSelectionHeight,
                burningManService.getLegacyBurningManAddress(burningManSelectionHeight),
                entries);

        String fileName = String.format(FILE_NAME_FORMAT, listVersion);
        jsonFileManager.writeToDisc(JsonUtil.objectToJson(burningManAddressList), fileName);
        log.info("Exported {} Burning Man receiver entries to {}.json", entries.size(), fileName);
    }

    private List<BurningManAddressList.Entry> getEntries(int burningManSelectionHeight) {
        Map<String, Double> shareByAddress = new LinkedHashMap<>();
        burningManService.getActiveBurningManCandidates(burningManSelectionHeight).stream()
                .filter(candidate -> candidate.getReceiverAddress().isPresent())
                .forEach(candidate -> {
                    String address = candidate.getReceiverAddress().get();
                    shareByAddress.merge(address, candidate.getCappedBurnAmountShare(), Double::sum);
                });

        return shareByAddress.entrySet().stream()
                .map(entry -> new BurningManAddressList.Entry(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }
}
