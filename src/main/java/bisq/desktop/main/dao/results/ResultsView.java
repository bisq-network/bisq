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

package bisq.desktop.main.dao.results;


import bisq.desktop.common.model.Activatable;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.TableGroupHeadline;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.dao.results.combo.VotesPerProposalTableView;
import bisq.desktop.main.dao.results.model.ResultsOfCycle;
import bisq.desktop.main.dao.results.proposals.ProposalResultsTableView;
import bisq.desktop.main.dao.results.votes.VotesTableView;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.BsqStateListener;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.ext.Param;
import bisq.core.dao.state.period.Cycle;
import bisq.core.dao.state.period.CycleService;
import bisq.core.dao.voting.blindvote.BlindVoteConsensus;
import bisq.core.dao.voting.proposal.Proposal;
import bisq.core.dao.voting.proposal.ProposalConsensus;
import bisq.core.dao.voting.proposal.ProposalService;
import bisq.core.dao.voting.proposal.storage.appendonly.ProposalPayload;
import bisq.core.dao.voting.voteresult.DecryptedVote;
import bisq.core.dao.voting.voteresult.EvaluatedProposal;
import bisq.core.dao.voting.voteresult.VoteResultService;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;
import bisq.core.util.BsqFormatter;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import javafx.fxml.FXML;

import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import javafx.geometry.HPos;
import javafx.geometry.Insets;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static bisq.desktop.util.FormBuilder.addLabelTextField;

@FxmlView
public class ResultsView extends ActivatableViewAndModel<AnchorPane, Activatable> implements BsqStateListener {
    @FXML
    private ScrollPane scrollPane;

    private final DaoFacade daoFacade;
    // TODO use daoFacade once dev work completed
    private final BsqStateService bsqStateService;
    private final CycleService cycleService;
    private final VoteResultService voteResultService;
    private final ProposalService proposalService;
    private final BsqWalletService bsqWalletService;
    private final Preferences preferences;
    private final BsqFormatter bsqFormatter;


    private int gridRow = 0;
    private TableView<ResultsListItem> tableView;

    private final ObservableList<ResultsListItem> itemList = FXCollections.observableArrayList();
    private final SortedList<ResultsListItem> sortedList = new SortedList<>(itemList);
    private ChangeListener<ResultsListItem> selectedItemListener;

    private VotesPerProposalTableView votesPerProposalTableView;
    private ProposalResultsTableView proposalResultsTableView;
    private VotesTableView votesTableView;
    private GridPane gridPane;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ResultsView(DaoFacade daoFacade,
                        BsqStateService bsqStateService,
                        CycleService cycleService,
                        VoteResultService voteResultService,
                        ProposalService proposalService,
                        BsqWalletService bsqWalletService,
                        Preferences preferences,
                        BsqFormatter bsqFormatter) {
        this.daoFacade = daoFacade;
        this.bsqStateService = bsqStateService;
        this.cycleService = cycleService;
        this.voteResultService = voteResultService;
        this.proposalService = proposalService;
        this.bsqWalletService = bsqWalletService;
        this.preferences = preferences;
        this.bsqFormatter = bsqFormatter;
    }

    @Override
    public void initialize() {
        daoFacade.addBsqStateListener(this);

        createCyclesTable();

        votesPerProposalTableView = new VotesPerProposalTableView(gridPane, bsqWalletService, daoFacade, bsqStateService, bsqFormatter);
        proposalResultsTableView = new ProposalResultsTableView(gridPane, bsqWalletService, daoFacade, bsqFormatter,
                bsqStateService,
                cycleService,
                voteResultService,
                proposalService);
        votesTableView = new VotesTableView(gridPane, bsqWalletService, daoFacade, bsqStateService, preferences, bsqFormatter);
        selectedItemListener = (observable, oldValue, newValue) -> onResultsListItemSelected(newValue);
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

    private void onResultsListItemSelected(ResultsListItem item) {
        removeDetailsViews();
        if (item != null) {
            ResultsOfCycle resultsOfCycle = item.getResultsOfCycle();

            gridRow = votesPerProposalTableView.createAllFields(++gridRow, resultsOfCycle);
            gridRow = proposalResultsTableView.createAllFields(++gridRow, resultsOfCycle);
            gridRow = votesTableView.createAllFields(++gridRow, resultsOfCycle);
            addParams(resultsOfCycle);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Create views
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createCyclesTable() {
        gridPane = new GridPane();

        gridPane.setHgap(5);
        gridPane.setVgap(5);

        gridPane.setPadding(new Insets(15, 25, 10, 25));

        ColumnConstraints columnConstraints1 = new ColumnConstraints();
        columnConstraints1.setHalignment(HPos.RIGHT);
        columnConstraints1.setHgrow(Priority.SOMETIMES);

        columnConstraints1.setMinWidth(140);

        ColumnConstraints columnConstraints2 = new ColumnConstraints();
        columnConstraints2.setHgrow(Priority.ALWAYS);
        columnConstraints1.setMinWidth(300);

        gridPane.getColumnConstraints().addAll(columnConstraints1, columnConstraints2);
        scrollPane.setContent(gridPane);

        TableGroupHeadline headline = new TableGroupHeadline(Res.get("dao.results.cycles.header"));
        GridPane.setRowIndex(headline, gridRow);
        GridPane.setMargin(headline, new Insets(0, -10, -10, -10));
        GridPane.setColumnSpan(headline, 2);
        gridPane.getChildren().add(headline);

        tableView = new TableView<>();
        tableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noData")));
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        createColumns(tableView);

        GridPane.setRowIndex(tableView, gridRow);
        GridPane.setMargin(tableView, new Insets(20, -10, 5, -10));
        GridPane.setColumnSpan(tableView, 2);
        gridPane.getChildren().add(tableView);

        tableView.setItems(sortedList);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
    }

    private void addParams(ResultsOfCycle resultsOfCycle) {
        //TODO
        AtomicInteger rowSpan = new AtomicInteger(2);
        TitledGroupBg header = FormBuilder.addTitledGroupBg(gridPane, ++gridRow, rowSpan.get(), Res.get("dao.results.cycle.header"), 20);

        int height = resultsOfCycle.getCycle().getHeightOfFirstBlock();
        gridRow--; // first item use same gridRow as header. as we use a ++ in the loop adjust by --.
        Arrays.stream(Param.values()).forEach(param -> {
            String label = null;
            long paramValue = bsqStateService.getParamValue(param, height);
            boolean isDefaultValue = param.getDefaultValue() == paramValue;
            String value = null;
            int top = (param == Param.BSQ_MAKER_FEE_IN_PERCENT) ? 40 : 0;
            switch (param) {
                case UNDEFINED:
                    // ignore
                    break;

                case BSQ_MAKER_FEE_IN_PERCENT:
                case BSQ_TAKER_FEE_IN_PERCENT:
                case BTC_MAKER_FEE_IN_PERCENT:
                case BTC_TAKER_FEE_IN_PERCENT:
                    label = Res.getWithCol("dao.param." + param.name());
                    value = bsqFormatter.formatToPercentWithSymbol(paramValue / 10000d);
                    break;

                case PROPOSAL_FEE:
                    label = Res.getWithCol("dao.param." + param.name());
                    value = bsqFormatter.formatCoinWithCode(ProposalConsensus.getFee(bsqStateService, height));
                    break;
                case BLIND_VOTE_FEE:
                    label = Res.getWithCol("dao.param." + param.name());
                    value = bsqFormatter.formatCoinWithCode(BlindVoteConsensus.getFee(bsqStateService, height));
                    break;

                case QUORUM_PROPOSAL:
                case QUORUM_COMP_REQUEST:
                case QUORUM_CHANGE_PARAM:
                case QUORUM_REMOVE_ASSET:
                case QUORUM_CONFISCATION:
                    label = Res.getWithCol("dao.param." + param.name());
                    value = bsqFormatter.formatCoinWithCode(Coin.valueOf(paramValue));
                    break;
                case THRESHOLD_PROPOSAL:

                case THRESHOLD_COMP_REQUEST:
                case THRESHOLD_CHANGE_PARAM:
                case THRESHOLD_REMOVE_ASSET:
                case THRESHOLD_CONFISCATION:
                    label = Res.getWithCol("dao.param." + param.name());
                    value = bsqFormatter.formatToPercentWithSymbol(paramValue / 10000d);
                    break;

                case PHASE_UNDEFINED:
                    // ignore
                    break;

                case PHASE_PROPOSAL:
                case PHASE_BREAK1:
                case PHASE_BLIND_VOTE:
                case PHASE_BREAK2:
                case PHASE_VOTE_REVEAL:
                case PHASE_BREAK3:
                case PHASE_RESULT:
                case PHASE_BREAK4:
                    String phase = Res.get("dao.phase." + param.name());
                    label = Res.getWithCol("dao.results.cycle.duration.label", phase);
                    value = Res.get("dao.results.cycle.duration.value", paramValue);
                    break;
            }
            if (value != null) {
                String postFix = isDefaultValue ?
                        Res.get("dao.results.cycle.value.postFix.isDefaultValue") :
                        Res.get("dao.results.cycle.value.postFix.hasChanged");
                value += " " + postFix;
                addLabelTextField(gridPane, ++gridRow, label, value, top);
                rowSpan.getAndIncrement();
            }
        });

        GridPane.setRowSpan(header, rowSpan.get());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void fillCycleList() {
        itemList.clear();
        bsqStateService.getCycles().forEach(this::addCycleListItem);
        Collections.reverse(itemList);
        GUIUtil.setFitToRowsForTableView(tableView, 24, 28, 80);
    }

    private void addCycleListItem(Cycle cycle) {
        List<Proposal> proposalsForCycle = proposalService.getAppendOnlyStoreList().stream()
                .filter(proposalPayload -> cycleService.isTxInCycle(cycle, proposalPayload.getProposal().getTxId()))
                .map(ProposalPayload::getProposal)
                .collect(Collectors.toList());

        List<EvaluatedProposal> evaluatedProposalsForCycle = voteResultService.getAllEvaluatedProposals().stream()
                .filter(evaluatedProposal -> cycleService.isTxInCycle(cycle, evaluatedProposal.getProposal().getTxId()))
                .collect(Collectors.toList());

        List<DecryptedVote> decryptedVotesForCycle = voteResultService.getAllDecryptedVotes().stream()
                .filter(decryptedVote -> cycleService.isTxInCycle(cycle, decryptedVote.getBlindVoteTxId()))
                .filter(decryptedVote -> cycleService.isTxInCycle(cycle, decryptedVote.getVoteRevealTxId()))
                .collect(Collectors.toList());

        long cycleStartTime = bsqStateService.getBlockAtHeight(cycle.getHeightOfFirstBlock())
                .map(e -> e.getTime() * 1000)
                .orElse(0L);
        int cycleIndex = cycleService.getCycleIndex(cycle);
        ResultsOfCycle resultsOfCycle = new ResultsOfCycle(cycle,
                cycleIndex,
                cycleStartTime,
                proposalsForCycle,
                evaluatedProposalsForCycle,
                decryptedVotesForCycle);
        ResultsListItem resultsListItem = new ResultsListItem(resultsOfCycle, bsqStateService, bsqFormatter);
        itemList.add(resultsListItem);
    }

    private void removeDetailsViews() {
        GUIUtil.removeChildrenFromGridPaneRows(gridPane, 1, gridRow);
        gridRow = 0;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TableColumns
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createColumns(TableView<ResultsListItem> tableView) {
        TableColumn<ResultsListItem, ResultsListItem> cycleColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.cycles.table.header.cycle"));
        cycleColumn.setMinWidth(160);
        cycleColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        cycleColumn.setCellFactory(
                new Callback<TableColumn<ResultsListItem, ResultsListItem>, TableCell<ResultsListItem,
                        ResultsListItem>>() {
                    @Override
                    public TableCell<ResultsListItem, ResultsListItem> call(
                            TableColumn<ResultsListItem, ResultsListItem> column) {
                        return new TableCell<ResultsListItem, ResultsListItem>() {
                            @Override
                            public void updateItem(final ResultsListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getCycle());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        cycleColumn.setComparator(Comparator.comparing(ResultsListItem::getCycleStartTime));
        tableView.getColumns().add(cycleColumn);

        TableColumn<ResultsListItem, ResultsListItem> proposalsColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.cycles.table.header.numProposals"));
        proposalsColumn.setMinWidth(90);
        proposalsColumn.setMaxWidth(90);
        proposalsColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        proposalsColumn.setCellFactory(
                new Callback<TableColumn<ResultsListItem, ResultsListItem>, TableCell<ResultsListItem,
                        ResultsListItem>>() {
                    @Override
                    public TableCell<ResultsListItem, ResultsListItem> call(
                            TableColumn<ResultsListItem, ResultsListItem> column) {
                        return new TableCell<ResultsListItem, ResultsListItem>() {
                            @Override
                            public void updateItem(final ResultsListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getNumProposals());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        proposalsColumn.setComparator(Comparator.comparing(ResultsListItem::getNumProposals));
        tableView.getColumns().add(proposalsColumn);

        TableColumn<ResultsListItem, ResultsListItem> votesColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.cycles.table.header.numVotes"));
        votesColumn.setMinWidth(70);
        votesColumn.setMaxWidth(70);
        votesColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        votesColumn.setCellFactory(
                new Callback<TableColumn<ResultsListItem, ResultsListItem>, TableCell<ResultsListItem,
                        ResultsListItem>>() {
                    @Override
                    public TableCell<ResultsListItem, ResultsListItem> call(
                            TableColumn<ResultsListItem, ResultsListItem> column) {
                        return new TableCell<ResultsListItem, ResultsListItem>() {
                            @Override
                            public void updateItem(final ResultsListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getNumVotesAsString());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        votesColumn.setComparator(Comparator.comparing(ResultsListItem::getNumProposals));
        tableView.getColumns().add(votesColumn);

        TableColumn<ResultsListItem, ResultsListItem> stakeColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.cycles.table.header.stake"));
        stakeColumn.setMinWidth(70);
        stakeColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        stakeColumn.setCellFactory(
                new Callback<TableColumn<ResultsListItem, ResultsListItem>, TableCell<ResultsListItem,
                        ResultsListItem>>() {
                    @Override
                    public TableCell<ResultsListItem, ResultsListItem> call(
                            TableColumn<ResultsListItem, ResultsListItem> column) {
                        return new TableCell<ResultsListItem, ResultsListItem>() {
                            @Override
                            public void updateItem(final ResultsListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getStake());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        stakeColumn.setComparator(Comparator.comparing(ResultsListItem::getNumProposals));
        tableView.getColumns().add(stakeColumn);

        TableColumn<ResultsListItem, ResultsListItem> issuanceColumn = new AutoTooltipTableColumn<>(Res.get("dao.results.cycles.table.header.issuance"));
        issuanceColumn.setMinWidth(70);
        issuanceColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        issuanceColumn.setCellFactory(
                new Callback<TableColumn<ResultsListItem, ResultsListItem>, TableCell<ResultsListItem,
                        ResultsListItem>>() {
                    @Override
                    public TableCell<ResultsListItem, ResultsListItem> call(
                            TableColumn<ResultsListItem, ResultsListItem> column) {
                        return new TableCell<ResultsListItem, ResultsListItem>() {
                            @Override
                            public void updateItem(final ResultsListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getIssuance());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        issuanceColumn.setComparator(Comparator.comparing(ResultsListItem::getNumProposals));
        tableView.getColumns().add(issuanceColumn);
    }
}
