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

package bisq.desktop.main.dao.cycles;


import bisq.desktop.common.model.Activatable;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.TableGroupHeadline;
import bisq.desktop.main.dao.cycles.cycle.CycleDisplay;
import bisq.desktop.main.dao.cycles.model.CycleResult;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.BsqStateListener;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.period.Cycle;
import bisq.core.dao.state.period.CycleService;
import bisq.core.dao.voting.proposal.Proposal;
import bisq.core.dao.voting.proposal.ProposalService;
import bisq.core.dao.voting.proposal.storage.appendonly.ProposalPayload;
import bisq.core.dao.voting.voteresult.EvaluatedProposal;
import bisq.core.dao.voting.voteresult.VoteResultService;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import javax.inject.Inject;

import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import javafx.geometry.Insets;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@FxmlView
public class CyclesView extends ActivatableViewAndModel<GridPane, Activatable> implements BsqStateListener {
    private final DaoFacade daoFacade;
    // TODO use daoFacade once dev work completed
    private final BsqStateService bsqStateService;
    private final CycleService cycleService;
    private final VoteResultService voteResultService;
    private final ProposalService proposalService;
    private final BsqWalletService bsqWalletService;
    private final BsqFormatter bsqFormatter;

    private int gridRow = 0;
    private TableView<CyclesListItem> tableView;

    private final ObservableList<CyclesListItem> itemList = FXCollections.observableArrayList();
    private final SortedList<CyclesListItem> sortedList = new SortedList<>(itemList);
    private ChangeListener<CyclesListItem> selectedItemListener;
    private CyclesListItem selectedItem;

    private GridPane resultGridPane;
    private CycleDisplay proposalsDisplay;
    private ScrollPane resultDisplayView;
    private boolean resultDisplayInitialized;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private CyclesView(DaoFacade daoFacade,
                       BsqStateService bsqStateService,
                       CycleService cycleService,
                       VoteResultService voteResultService,
                       ProposalService proposalService,
                       BsqWalletService bsqWalletService,
                       BsqFormatter bsqFormatter) {
        this.daoFacade = daoFacade;
        this.bsqStateService = bsqStateService;
        this.cycleService = cycleService;
        this.voteResultService = voteResultService;
        this.proposalService = proposalService;
        this.bsqWalletService = bsqWalletService;
        this.bsqFormatter = bsqFormatter;
    }

    @Override
    public void initialize() {
        daoFacade.addBsqStateListener(this);

        resultGridPane = new GridPane();
        createResultsTable();
        createResultDisplay();

        selectedItemListener = (observable, oldValue, newValue) -> onCycleListItemSelected(newValue);
    }

    @Override
    protected void activate() {
        tableView.getSelectionModel().selectedItemProperty().addListener(selectedItemListener);
        fillCycleList();
    }

    @Override
    protected void deactivate() {
        tableView.getSelectionModel().selectedItemProperty().removeListener(selectedItemListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onNewBlockHeight(int blockHeight) {
        fillCycleList();
    }

    @Override
    public void onEmptyBlockAdded(Block block) {
    }

    @Override
    public void onParseTxsComplete(Block block) {
    }

    @Override
    public void onParseBlockChainComplete() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onCycleListItemSelected(CyclesListItem item) {
        selectedItem = item;
        if (selectedItem != null)
            createAllFieldsOnResultDisplay(selectedItem);
        else
            hideResultDisplay();

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Create views
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createResultsTable() {
        TableGroupHeadline headline = new TableGroupHeadline(Res.get("dao.results.results.header"));
        GridPane.setRowIndex(headline, ++gridRow);
        GridPane.setMargin(headline, new Insets(0, -10, -10, -10));
        GridPane.setColumnSpan(headline, 2);
        root.getChildren().add(headline);

        tableView = new TableView<>();
        tableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noData")));
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPrefHeight(200);

        createColumns(tableView);
        GridPane.setRowIndex(tableView, gridRow);
        GridPane.setMargin(tableView, new Insets(20, -10, 5, -10));
        GridPane.setColumnSpan(tableView, 2);
        GridPane.setHgrow(tableView, Priority.ALWAYS);
        root.getChildren().add(tableView);

        tableView.setItems(sortedList);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
    }

    private void createResultDisplay() {
        proposalsDisplay = new CycleDisplay(resultGridPane, bsqWalletService, bsqFormatter);
        resultDisplayView = proposalsDisplay.getView();
        GridPane.setMargin(resultDisplayView, new Insets(10, -10, 0, -10));
        GridPane.setRowIndex(resultDisplayView, ++gridRow);
        GridPane.setColumnSpan(resultDisplayView, 2);
        root.getChildren().add(resultDisplayView);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void fillCycleList() {
        itemList.clear();
        bsqStateService.getCycles().forEach(this::addCycleListItem);
        Collections.reverse(itemList);
    }

    private void addCycleListItem(Cycle cycle) {
        List<Proposal> proposalsForCycle = proposalService.getAppendOnlyStoreList().stream()
                .filter(proposalPayload -> cycleService.isTxInCycle(cycle, proposalPayload.getProposal().getTxId()))
                .map(ProposalPayload::getProposal)
                .collect(Collectors.toList());

        List<EvaluatedProposal> evaluatedProposalsForCycle = voteResultService.getAllEvaluatedProposals().stream()
                .filter(evaluatedProposal -> cycleService.isTxInCycle(cycle, evaluatedProposal.getProposal().getTxId()))
                .collect(Collectors.toList());

        long cycleStartTime = bsqStateService.getBlockAtHeight(cycle.getHeightOfFirstBlock())
                .map(e -> e.getTime() * 1000)
                .orElse(0L);
        int cycleIndex = cycleService.getCycleIndex(cycle);
        CycleResult cycleResult = new CycleResult(cycle,
                cycleIndex,
                cycleStartTime,
                proposalsForCycle,
                evaluatedProposalsForCycle);
        CyclesListItem cyclesListItem = new CyclesListItem(cycleResult, bsqFormatter);
        itemList.add(cyclesListItem);
    }

    private void hideResultDisplay() {
        if (resultDisplayInitialized) {
            proposalsDisplay.removeAllFields();
            resultDisplayView.setVisible(false);
            resultDisplayView.setManaged(false);
        }
    }

    private void createAllFieldsOnResultDisplay(CyclesListItem cyclesListItem) {
        resultDisplayView.setVisible(true);
        resultDisplayView.setManaged(true);

        proposalsDisplay.createAllFields(0, cyclesListItem.getCycleResult());
        resultDisplayInitialized = true;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TableColumns
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createColumns(TableView<CyclesListItem> tableView) {
        TableColumn<CyclesListItem, CyclesListItem> cycleColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.results.table.header.cycle"));
        cycleColumn.setMinWidth(160);
        cycleColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        cycleColumn.setCellFactory(
                new Callback<TableColumn<CyclesListItem, CyclesListItem>, TableCell<CyclesListItem,
                        CyclesListItem>>() {
                    @Override
                    public TableCell<CyclesListItem, CyclesListItem> call(
                            TableColumn<CyclesListItem, CyclesListItem> column) {
                        return new TableCell<CyclesListItem, CyclesListItem>() {
                            @Override
                            public void updateItem(final CyclesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getCycle());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        cycleColumn.setComparator(Comparator.comparing(CyclesListItem::getCycleStartTime));
        tableView.getColumns().add(cycleColumn);

        TableColumn<CyclesListItem, CyclesListItem> proposalsColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.results.table.header.numProposals"));
        proposalsColumn.setMinWidth(90);
        proposalsColumn.setMaxWidth(90);
        proposalsColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        proposalsColumn.setCellFactory(
                new Callback<TableColumn<CyclesListItem, CyclesListItem>, TableCell<CyclesListItem,
                        CyclesListItem>>() {
                    @Override
                    public TableCell<CyclesListItem, CyclesListItem> call(
                            TableColumn<CyclesListItem, CyclesListItem> column) {
                        return new TableCell<CyclesListItem, CyclesListItem>() {
                            @Override
                            public void updateItem(final CyclesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getNumProposals());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        proposalsColumn.setComparator(Comparator.comparing(CyclesListItem::getNumProposals));
        tableView.getColumns().add(proposalsColumn);

        TableColumn<CyclesListItem, CyclesListItem> votesColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.results.table.header.numVotes"));
        votesColumn.setMinWidth(70);
        votesColumn.setMaxWidth(70);
        votesColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        votesColumn.setCellFactory(
                new Callback<TableColumn<CyclesListItem, CyclesListItem>, TableCell<CyclesListItem,
                        CyclesListItem>>() {
                    @Override
                    public TableCell<CyclesListItem, CyclesListItem> call(
                            TableColumn<CyclesListItem, CyclesListItem> column) {
                        return new TableCell<CyclesListItem, CyclesListItem>() {
                            @Override
                            public void updateItem(final CyclesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getNumVotesAsString());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        votesColumn.setComparator(Comparator.comparing(CyclesListItem::getNumProposals));
        tableView.getColumns().add(votesColumn);

        TableColumn<CyclesListItem, CyclesListItem> stakeColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.results.table.header.stake"));
        stakeColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        stakeColumn.setCellFactory(
                new Callback<TableColumn<CyclesListItem, CyclesListItem>, TableCell<CyclesListItem,
                        CyclesListItem>>() {
                    @Override
                    public TableCell<CyclesListItem, CyclesListItem> call(
                            TableColumn<CyclesListItem, CyclesListItem> column) {
                        return new TableCell<CyclesListItem, CyclesListItem>() {
                            @Override
                            public void updateItem(final CyclesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getStake());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        stakeColumn.setComparator(Comparator.comparing(CyclesListItem::getNumProposals));
        tableView.getColumns().add(stakeColumn);

        TableColumn<CyclesListItem, CyclesListItem> issuanceColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.results.table.header.issuance"));
        issuanceColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        issuanceColumn.setCellFactory(
                new Callback<TableColumn<CyclesListItem, CyclesListItem>, TableCell<CyclesListItem,
                        CyclesListItem>>() {
                    @Override
                    public TableCell<CyclesListItem, CyclesListItem> call(
                            TableColumn<CyclesListItem, CyclesListItem> column) {
                        return new TableCell<CyclesListItem, CyclesListItem>() {
                            @Override
                            public void updateItem(final CyclesListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getIssuance());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        issuanceColumn.setComparator(Comparator.comparing(CyclesListItem::getNumProposals));
        tableView.getColumns().add(issuanceColumn);
    }
}
