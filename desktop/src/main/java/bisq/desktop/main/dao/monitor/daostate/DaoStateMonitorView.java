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

package bisq.desktop.main.dao.monitor.daostate;

import bisq.desktop.common.view.FxmlView;
import bisq.desktop.main.dao.monitor.StateMonitorView;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.FormBuilder;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.period.CycleService;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.monitoring.DaoStateMonitoringService;
import bisq.core.dao.monitoring.model.DaoStateBlock;
import bisq.core.dao.monitoring.model.DaoStateHash;
import bisq.core.dao.monitoring.model.UtxoMismatch;
import bisq.core.dao.state.DaoStateService;
import bisq.core.locale.Res;

import bisq.network.p2p.seed.SeedNodeRepository;

import bisq.common.storage.Storage;
import bisq.common.util.Utilities;

import javax.inject.Inject;
import javax.inject.Named;

import javafx.collections.ListChangeListener;

import java.io.File;

import java.util.Map;
import java.util.stream.Collectors;

@FxmlView
public class DaoStateMonitorView extends StateMonitorView<DaoStateHash, DaoStateBlock, DaoStateBlockListItem, DaoStateInConflictListItem>
        implements DaoStateMonitoringService.Listener {
    private final DaoStateMonitoringService daoStateMonitoringService;
    private ListChangeListener<UtxoMismatch> utxoMismatchListChangeListener;
    private Overlay warningPopup;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Inject
    private DaoStateMonitorView(DaoStateService daoStateService,
                                DaoFacade daoFacade,
                                DaoStateMonitoringService daoStateMonitoringService,
                                CycleService cycleService,
                                PeriodService periodService,
                                SeedNodeRepository seedNodeRepository,
                                @Named(Storage.STORAGE_DIR) File storageDir) {
        super(daoStateService, daoFacade, cycleService, periodService, seedNodeRepository, storageDir);

        this.daoStateMonitoringService = daoStateMonitoringService;
    }

    @Override
    public void initialize() {
        utxoMismatchListChangeListener = c -> updateUtxoMismatches();

        FormBuilder.addTitledGroupBg(root, gridRow, 3, Res.get("dao.monitor.daoState.headline"));

        statusTextField = FormBuilder.addTopLabelTextField(root, ++gridRow,
                Res.get("dao.monitor.state")).second;
        resyncButton = FormBuilder.addButton(root, ++gridRow, Res.get("dao.monitor.resync"), 10);

        super.initialize();
    }

    @Override
    protected void activate() {
        super.activate();

        daoStateMonitoringService.addListener(this);
        daoStateMonitoringService.getUtxoMismatches().addListener(utxoMismatchListChangeListener);

        updateUtxoMismatches();
    }

    @Override
    protected void deactivate() {
        super.deactivate();

        daoStateMonitoringService.removeListener(this);
        daoStateMonitoringService.getUtxoMismatches().removeListener(utxoMismatchListChangeListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateMonitoringService.Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onChangeAfterBatchProcessing() {
        if (daoStateService.isParseBlockChainComplete()) {
            onDataUpdate();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Implementation abstract methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected DaoStateBlockListItem getStateBlockListItem(DaoStateBlock daoStateBlock) {
        int cycleIndex = periodService.getCycle(daoStateBlock.getHeight()).map(cycleService::getCycleIndex).orElse(0);
        return new DaoStateBlockListItem(daoStateBlock, cycleIndex);
    }

    @Override
    protected DaoStateInConflictListItem getStateInConflictListItem(Map.Entry<String, DaoStateHash> mapEntry) {
        DaoStateHash daoStateHash = mapEntry.getValue();
        int cycleIndex = periodService.getCycle(daoStateHash.getHeight()).map(cycleService::getCycleIndex).orElse(0);
        return new DaoStateInConflictListItem(mapEntry.getKey(), daoStateHash, cycleIndex, seedNodeAddresses);
    }

    @Override
    protected String getTableHeadLine() {
        return Res.get("dao.monitor.daoState.table.headline");
    }

    @Override
    protected String getConflictTableHeadLine() {
        return Res.get("dao.monitor.daoState.conflictTable.headline");
    }

    @Override
    protected String getConflictsTableHeader() {
        return Res.get("dao.monitor.table.conflicts");
    }

    @Override
    protected String getPeersTableHeader() {
        return Res.get("dao.monitor.table.peers");
    }

    @Override
    protected String getPrevHashTableHeader() {
        return Res.get("dao.monitor.daoState.table.prev");
    }

    @Override
    protected String getHashTableHeader() {
        return Res.get("dao.monitor.daoState.table.hash");
    }

    @Override
    protected String getBlockHeightTableHeader() {
        return Res.get("dao.monitor.daoState.table.blockHeight");
    }

    @Override
    protected String getRequestHashes() {
        return Res.get("dao.monitor.requestAlHashes");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Override
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onDataUpdate() {
        isInConflictWithSeedNode.set(daoStateMonitoringService.isInConflictWithSeedNode());
        isInConflictWithNonSeedNode.set(daoStateMonitoringService.isInConflictWithNonSeedNode());

        listItems.setAll(daoStateMonitoringService.getDaoStateBlockChain().stream()
                .map(this::getStateBlockListItem)
                .collect(Collectors.toList()));

        super.onDataUpdate();
    }

    @Override
    protected void requestHashesFromGenesisBlockHeight(String peerAddress) {
        daoStateMonitoringService.requestHashesFromGenesisBlockHeight(peerAddress);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateUtxoMismatches() {
        if (!daoStateMonitoringService.getUtxoMismatches().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            daoStateMonitoringService.getUtxoMismatches().forEach(e -> {
                sb.append("\n").append(Res.get("dao.monitor.daoState.utxoConflicts.blockHeight", e.getHeight())).append("\n")
                        .append(Res.get("dao.monitor.daoState.utxoConflicts.sumUtxo", e.getSumUtxo() / 100)).append("\n")
                        .append(Res.get("dao.monitor.daoState.utxoConflicts.sumBsq", e.getSumBsq() / 100));
            });

            if (warningPopup == null) {
                warningPopup = new Popup<>().headLine(Res.get("dao.monitor.daoState.utxoConflicts"))
                        .warning(Utilities.toTruncatedString(sb.toString(), 500, false)).onClose(() -> {
                            warningPopup = null;
                        });
                warningPopup.show();
            }
        }
    }
}
