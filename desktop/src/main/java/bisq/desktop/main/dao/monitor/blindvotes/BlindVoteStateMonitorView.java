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

package bisq.desktop.main.dao.monitor.blindvotes;

import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.main.dao.monitor.StateMonitorView;
import bisq.desktop.util.FormBuilder;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.period.CycleService;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.monitoring.BlindVoteStateMonitoringService;
import bisq.core.dao.monitoring.model.BlindVoteStateBlock;
import bisq.core.dao.monitoring.model.BlindVoteStateHash;
import bisq.core.dao.state.DaoStateService;
import bisq.core.locale.Res;

import bisq.network.p2p.seed.SeedNodeRepository;

import bisq.common.config.Config;

import javax.inject.Inject;
import javax.inject.Named;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.util.Callback;

import java.io.File;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

@FxmlView
public class BlindVoteStateMonitorView extends StateMonitorView<BlindVoteStateHash, BlindVoteStateBlock, BlindVoteStateBlockListItem, BlindVoteStateInConflictListItem>
        implements BlindVoteStateMonitoringService.Listener {
    private final BlindVoteStateMonitoringService blindVoteStateMonitoringService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Inject
    private BlindVoteStateMonitorView(DaoStateService daoStateService,
                                      DaoFacade daoFacade,
                                      BlindVoteStateMonitoringService blindVoteStateMonitoringService,
                                      CycleService cycleService,
                                      PeriodService periodService,
                                      SeedNodeRepository seedNodeRepository,
                                      @Named(Config.STORAGE_DIR) File storageDir) {
        super(daoStateService, daoFacade, cycleService, periodService, seedNodeRepository, storageDir);

        this.blindVoteStateMonitoringService = blindVoteStateMonitoringService;
    }

    @Override
    public void initialize() {
        FormBuilder.addTitledGroupBg(root, gridRow, 3, Res.get("dao.monitor.blindVote.headline"));

        statusTextField = FormBuilder.addTopLabelTextField(root, ++gridRow,
                Res.get("dao.monitor.state")).second;
        resyncButton = FormBuilder.addButton(root, ++gridRow, Res.get("dao.monitor.resync"), 10);

        super.initialize();
    }

    @Override
    protected void activate() {
        super.activate();

        blindVoteStateMonitoringService.addListener(this);
    }

    @Override
    protected void deactivate() {
        super.deactivate();
        blindVoteStateMonitoringService.removeListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BlindVoteStateMonitoringService.Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onBlindVoteStateBlockChainChanged() {
        if (daoStateService.isParseBlockChainComplete()) {
            onDataUpdate();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Implementation abstract methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected BlindVoteStateBlockListItem getStateBlockListItem(BlindVoteStateBlock daoStateBlock) {
        int cycleIndex = periodService.getCycle(daoStateBlock.getHeight()).map(cycleService::getCycleIndex).orElse(0);
        return new BlindVoteStateBlockListItem(daoStateBlock, cycleIndex);
    }

    @Override
    protected BlindVoteStateInConflictListItem getStateInConflictListItem(Map.Entry<String, BlindVoteStateHash> mapEntry) {
        BlindVoteStateHash blindVoteStateHash = mapEntry.getValue();
        int cycleIndex = periodService.getCycle(blindVoteStateHash.getHeight()).map(cycleService::getCycleIndex).orElse(0);
        return new BlindVoteStateInConflictListItem(mapEntry.getKey(), mapEntry.getValue(), cycleIndex, seedNodeAddresses);
    }

    @Override
    protected String getTableHeadLine() {
        return Res.get("dao.monitor.blindVote.table.headline");
    }

    @Override
    protected String getConflictTableHeadLine() {
        return Res.get("dao.monitor.blindVote.conflictTable.headline");
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
    protected String getHashTableHeader() {
        return Res.get("dao.monitor.blindVote.table.hash");
    }

    @Override
    protected String getBlockHeightTableHeader() {
        return Res.get("dao.monitor.table.header.cycleBlockHeight");
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
        isInConflictWithSeedNode.set(blindVoteStateMonitoringService.isInConflictWithSeedNode());
        isInConflictWithNonSeedNode.set(blindVoteStateMonitoringService.isInConflictWithNonSeedNode());

        listItems.setAll(blindVoteStateMonitoringService.getBlindVoteStateBlockChain().stream()
                .map(this::getStateBlockListItem)
                .collect(Collectors.toList()));

        super.onDataUpdate();
    }

    @Override
    protected void requestHashesFromGenesisBlockHeight(String peerAddress) {
        blindVoteStateMonitoringService.requestHashesFromGenesisBlockHeight(peerAddress);
    }

    @Override
    protected void createColumns() {
        super.createColumns();

        TableColumn<BlindVoteStateBlockListItem, BlindVoteStateBlockListItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("dao.monitor.blindVote.table.numBlindVotes"));
        column.setMinWidth(90);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<BlindVoteStateBlockListItem, BlindVoteStateBlockListItem> call(
                            TableColumn<BlindVoteStateBlockListItem, BlindVoteStateBlockListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(BlindVoteStateBlockListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getNumBlindVotes());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(e -> e.getStateBlock().getMyStateHash().getNumBlindVotes()));
        tableView.getColumns().add(1, column);
    }

    protected void createConflictColumns() {
        super.createConflictColumns();

        TableColumn<BlindVoteStateInConflictListItem, BlindVoteStateInConflictListItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("dao.monitor.blindVote.table.numBlindVotes"));
        column.setMinWidth(90);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<BlindVoteStateInConflictListItem, BlindVoteStateInConflictListItem> call(
                            TableColumn<BlindVoteStateInConflictListItem, BlindVoteStateInConflictListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(BlindVoteStateInConflictListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getNumBlindVotes());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(e -> e.getStateHash().getNumBlindVotes()));
        conflictTableView.getColumns().add(1, column);
    }
}
