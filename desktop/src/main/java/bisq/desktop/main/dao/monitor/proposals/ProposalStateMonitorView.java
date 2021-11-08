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

package bisq.desktop.main.dao.monitor.proposals;

import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.main.dao.monitor.StateMonitorView;
import bisq.desktop.util.FormBuilder;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.period.CycleService;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.monitoring.ProposalStateMonitoringService;
import bisq.core.dao.monitoring.model.ProposalStateBlock;
import bisq.core.dao.monitoring.model.ProposalStateHash;
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
public class ProposalStateMonitorView extends StateMonitorView<ProposalStateHash, ProposalStateBlock, ProposalStateBlockListItem, ProposalStateInConflictListItem>
        implements ProposalStateMonitoringService.Listener {
    private final ProposalStateMonitoringService proposalStateMonitoringService;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Inject
    private ProposalStateMonitorView(DaoStateService daoStateService,
                                     DaoFacade daoFacade,
                                     ProposalStateMonitoringService proposalStateMonitoringService,
                                     CycleService cycleService,
                                     PeriodService periodService,
                                     SeedNodeRepository seedNodeRepository,
                                     @Named(Config.STORAGE_DIR) File storageDir) {
        super(daoStateService, daoFacade, cycleService, periodService, seedNodeRepository, storageDir);

        this.proposalStateMonitoringService = proposalStateMonitoringService;
    }

    @Override
    public void initialize() {
        FormBuilder.addTitledGroupBg(root, gridRow, 3, Res.get("dao.monitor.proposal.headline"));

        statusTextField = FormBuilder.addTopLabelTextField(root, ++gridRow,
                Res.get("dao.monitor.state")).second;
        resyncButton = FormBuilder.addButton(root, ++gridRow, Res.get("dao.monitor.resync"), 10);

        super.initialize();
    }

    @Override
    protected void activate() {
        super.activate();
        proposalStateMonitoringService.addListener(this);
    }

    @Override
    protected void deactivate() {
        super.deactivate();
        proposalStateMonitoringService.removeListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ProposalStateMonitoringService.Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onProposalStateBlockChainChanged() {
        if (daoStateService.isParseBlockChainComplete()) {
            onDataUpdate();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Implementation abstract methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected ProposalStateBlockListItem getStateBlockListItem(ProposalStateBlock daoStateBlock) {
        int cycleIndex = periodService.getCycle(daoStateBlock.getHeight()).map(cycleService::getCycleIndex).orElse(0);
        return new ProposalStateBlockListItem(daoStateBlock, cycleIndex);
    }

    @Override
    protected ProposalStateInConflictListItem getStateInConflictListItem(Map.Entry<String, ProposalStateHash> mapEntry) {
        ProposalStateHash proposalStateHash = mapEntry.getValue();
        int cycleIndex = periodService.getCycle(proposalStateHash.getHeight()).map(cycleService::getCycleIndex).orElse(0);
        return new ProposalStateInConflictListItem(mapEntry.getKey(), mapEntry.getValue(), cycleIndex, seedNodeAddresses);
    }

    @Override
    protected String getTableHeadLine() {
        return Res.get("dao.monitor.proposal.table.headline");
    }

    @Override
    protected String getConflictTableHeadLine() {
        return Res.get("dao.monitor.proposal.conflictTable.headline");
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
        return Res.get("dao.monitor.proposal.table.hash");
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
        isInConflictWithSeedNode.set(proposalStateMonitoringService.isInConflictWithSeedNode());
        isInConflictWithNonSeedNode.set(proposalStateMonitoringService.isInConflictWithNonSeedNode());

        listItems.setAll(proposalStateMonitoringService.getProposalStateBlockChain().stream()
                .map(this::getStateBlockListItem)
                .collect(Collectors.toList()));

        super.onDataUpdate();
    }

    @Override
    protected void requestHashesFromGenesisBlockHeight(String peerAddress) {
        proposalStateMonitoringService.requestHashesFromGenesisBlockHeight(peerAddress);
    }

    @Override
    protected void createColumns() {
        super.createColumns();

        TableColumn<ProposalStateBlockListItem, ProposalStateBlockListItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("dao.monitor.proposal.table.numProposals"));
        column.setMinWidth(110);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ProposalStateBlockListItem, ProposalStateBlockListItem> call(
                            TableColumn<ProposalStateBlockListItem, ProposalStateBlockListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(ProposalStateBlockListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getNumProposals());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(e -> e.getStateBlock().getMyStateHash().getNumProposals()));
        tableView.getColumns().add(1, column);
    }

    protected void createConflictColumns() {
        super.createConflictColumns();

        TableColumn<ProposalStateInConflictListItem, ProposalStateInConflictListItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("dao.monitor.proposal.table.numProposals"));
        column.setMinWidth(110);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ProposalStateInConflictListItem, ProposalStateInConflictListItem> call(
                            TableColumn<ProposalStateInConflictListItem, ProposalStateInConflictListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(ProposalStateInConflictListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getNumProposals());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(e -> e.getStateHash().getNumProposals()));
        conflictTableView.getColumns().add(1, column);
    }
}
