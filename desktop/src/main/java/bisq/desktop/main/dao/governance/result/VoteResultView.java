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

package bisq.desktop.main.dao.governance.result;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.TableGroupHeadline;
import bisq.desktop.main.dao.governance.PhasesView;
import bisq.desktop.main.dao.governance.ProposalDisplay;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.period.CycleService;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.ProposalService;
import bisq.core.dao.governance.voteresult.VoteResultException;
import bisq.core.dao.governance.voteresult.VoteResultService;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.governance.Ballot;
import bisq.core.dao.state.model.governance.Cycle;
import bisq.core.dao.state.model.governance.DecryptedBallotsWithMerits;
import bisq.core.dao.state.model.governance.EvaluatedProposal;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.Vote;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;
import bisq.core.util.BsqFormatter;

import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIcon;

import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import javafx.geometry.Insets;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@FxmlView
public class VoteResultView extends ActivatableView<GridPane, Void> implements DaoStateListener {
    private final DaoFacade daoFacade;
    private final PhasesView phasesView;
    private final DaoStateService daoStateService;
    private final CycleService cycleService;
    private final VoteResultService voteResultService;
    private final ProposalService proposalService;
    private final PeriodService periodService;
    private final BsqWalletService bsqWalletService;
    private final Preferences preferences;
    private final BsqFormatter bsqFormatter;
    private final Navigation navigation;

    private int gridRow = 0;

    private TableView<CycleListItem> cyclesTableView;
    private final ObservableList<CycleListItem> cycleListItemList = FXCollections.observableArrayList();
    private final SortedList<CycleListItem> sortedCycleListItemList = new SortedList<>(cycleListItemList);

    private TableView<ProposalListItem> proposalsTableView;
    private final ObservableList<ProposalListItem> proposalList = FXCollections.observableArrayList();
    private final SortedList<ProposalListItem> sortedProposalList = new SortedList<>(proposalList);

    private final ObservableList<VoteListItem> voteListItemList = FXCollections.observableArrayList();
    private final SortedList<VoteListItem> sortedVoteListItemList = new SortedList<>(voteListItemList);

    private Subscription selectedProposalSubscription;
    private ChangeListener<CycleListItem> selectedVoteResultListItemListener;
    private ResultsOfCycle resultsOfCycle;
    private ProposalListItem selectedProposalListItem;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public VoteResultView(DaoFacade daoFacade,
                          PhasesView phasesView,
                          DaoStateService daoStateService,
                          CycleService cycleService,
                          VoteResultService voteResultService,
                          ProposalService proposalService,
                          PeriodService periodService,
                          BsqWalletService bsqWalletService,
                          Preferences preferences,
                          BsqFormatter bsqFormatter,
                          Navigation navigation) {
        this.daoFacade = daoFacade;
        this.phasesView = phasesView;
        this.daoStateService = daoStateService;
        this.cycleService = cycleService;
        this.voteResultService = voteResultService;
        this.proposalService = proposalService;
        this.periodService = periodService;
        this.bsqWalletService = bsqWalletService;
        this.preferences = preferences;
        this.bsqFormatter = bsqFormatter;
        this.navigation = navigation;
    }

    @Override
    public void initialize() {
        gridRow = phasesView.addGroup(root, gridRow);
        selectedVoteResultListItemListener = (observable, oldValue, newValue) -> onResultsListItemSelected(newValue);

        createCyclesTable();
    }

    @Override
    protected void activate() {
        super.activate();

        phasesView.activate();

        daoFacade.addBsqStateListener(this);
        cyclesTableView.getSelectionModel().selectedItemProperty().addListener(selectedVoteResultListItemListener);

        fillCycleList();
    }

    @Override
    protected void deactivate() {
        super.deactivate();

        onResultsListItemSelected(null);

        phasesView.deactivate();

        daoFacade.removeBsqStateListener(this);
        cyclesTableView.getSelectionModel().selectedItemProperty().removeListener(selectedVoteResultListItemListener);

        if (selectedProposalSubscription != null)
            selectedProposalSubscription.unsubscribe();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        fillCycleList();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onResultsListItemSelected(CycleListItem item) {
        if (selectedProposalSubscription != null)
            selectedProposalSubscription.unsubscribe();

        GUIUtil.removeChildrenFromGridPaneRows(root, 2, gridRow);
        gridRow = 1;

        if (item != null) {
            resultsOfCycle = item.getResultsOfCycle();

            maybeShowVoteResultErrors(item.getResultsOfCycle().getCycle());
            createProposalsTable();

            selectedProposalSubscription = EasyBind.subscribe(proposalsTableView.getSelectionModel().selectedItemProperty(),
                    this::onSelectProposalResultListItem);

            StringBuilder sb = new StringBuilder();
            voteResultService.getInvalidDecryptedBallotsWithMeritItems().stream()
                    .filter(e -> periodService.isTxInCorrectCycle(e.getVoteRevealTxId(),
                            item.getResultsOfCycle().getCycle().getHeightOfFirstBlock()))
                    .forEach(e -> {
                        sb.append("\n")
                                .append(Res.getWithCol("dao.proposal.myVote.blindVoteTxId")).append(" ")
                                .append(e.getBlindVoteTxId()).append("\n")
                                .append(Res.getWithCol("dao.results.votes.table.header.stake")).append(" ")
                                .append(bsqFormatter.formatCoinWithCode(Coin.valueOf(e.getStake()))).append("\n");
                        e.getBallotList().stream().forEach(ballot -> {
                            sb.append(Res.getWithCol("shared.name")).append(" ")
                                    .append(ballot.getProposal().getName()).append("\n");
                            sb.append(Res.getWithCol("dao.bond.table.column.link")).append(" ")
                                    .append(ballot.getProposal().getLink()).append("\n");
                            Vote vote = ballot.getVote();
                            String voteString = vote == null ? Res.get("dao.proposal.display.myVote.ignored") :
                                    vote.isAccepted() ?
                                            Res.get("dao.proposal.display.myVote.accepted") :
                                            Res.get("dao.proposal.display.myVote.rejected");
                            sb.append(Res.getWithCol("dao.results.votes.table.header.vote")).append(" ")
                                    .append(voteString).append("\n");

                        });
                    });
            if (!sb.toString().isEmpty()) {
                new Popup<>().information(Res.get("dao.results.invalidVotes", sb.toString())).show();
            }
        }
    }

    private void maybeShowVoteResultErrors(Cycle cycle) {
        List<VoteResultException> exceptions = voteResultService.getVoteResultExceptions().stream()
                .filter(voteResultException -> cycle.getHeightOfFirstBlock() == voteResultException.getHeightOfFirstBlockInCycle())
                .collect(Collectors.toList());
        if (!exceptions.isEmpty()) {
            TextArea textArea = FormBuilder.addTextArea(root, ++gridRow, "");
            GridPane.setMargin(textArea, new Insets(Layout.GROUP_DISTANCE, -15, 0, -10));
            textArea.setPrefHeight(100);

            StringBuilder sb = new StringBuilder(Res.getWithCol("dao.results.exceptions") + "\n");
            exceptions.forEach(exception -> {
                if (exception.getCause() != null)
                    sb.append(exception.getCause().getMessage());
                else
                    sb.append(exception.getMessage());
                sb.append("\n");
            });

            textArea.setText(sb.toString());
        }
    }

    private void onSelectProposalResultListItem(ProposalListItem item) {
        selectedProposalListItem = item;

        GUIUtil.removeChildrenFromGridPaneRows(root, 4, gridRow);
        gridRow = 2;


        if (selectedProposalListItem != null) {
            EvaluatedProposal evaluatedProposal = selectedProposalListItem.getEvaluatedProposal();
            Optional<Ballot> optionalBallot = daoFacade.getAllValidBallots().stream()
                    .filter(ballot -> ballot.getTxId().equals(evaluatedProposal.getProposalTxId()))
                    .findAny();
            Ballot ballot = optionalBallot.orElse(null);
            ProposalDisplay proposalDisplay = createProposalDisplay(evaluatedProposal, ballot);
            createVotesTable();

            // Check if my vote is included in result
            boolean isVoteIncludedInResult = voteListItemList.stream()
                    .anyMatch(voteListItem -> bsqWalletService.getTransaction(voteListItem.getBlindVoteTxId()) != null);
            proposalDisplay.setIsVoteIncludedInResult(isVoteIncludedInResult);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fill lists: Cycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void fillCycleList() {
        cycleListItemList.clear();
        daoStateService.getCycles().forEach(cycle -> {
            List<Proposal> proposalsForCycle = proposalService.getValidatedProposals().stream()
                    .filter(proposal -> cycleService.isTxInCycle(cycle, proposal.getTxId()))
                    .collect(Collectors.toList());

            List<EvaluatedProposal> evaluatedProposalsForCycle = daoStateService.getEvaluatedProposalList().stream()
                    .filter(evaluatedProposal -> cycleService.isTxInCycle(cycle, evaluatedProposal.getProposal().getTxId()))
                    .collect(Collectors.toList());

            List<DecryptedBallotsWithMerits> decryptedVotesForCycle = daoStateService.getDecryptedBallotsWithMeritsList().stream()
                    .filter(decryptedBallotsWithMerits -> cycleService.isTxInCycle(cycle, decryptedBallotsWithMerits.getBlindVoteTxId()))
                    .filter(decryptedBallotsWithMerits -> cycleService.isTxInCycle(cycle, decryptedBallotsWithMerits.getVoteRevealTxId()))
                    .collect(Collectors.toList());

            long cycleStartTime = daoStateService.getBlockAtHeight(cycle.getHeightOfFirstBlock())
                    .map(Block::getTime)
                    .orElse(0L);
            int cycleIndex = cycleService.getCycleIndex(cycle);
            ResultsOfCycle resultsOfCycle = new ResultsOfCycle(cycle,
                    cycleIndex,
                    cycleStartTime,
                    proposalsForCycle,
                    evaluatedProposalsForCycle,
                    decryptedVotesForCycle,
                    daoStateService);
            CycleListItem cycleListItem = new CycleListItem(resultsOfCycle, daoStateService, bsqFormatter);
            cycleListItemList.add(cycleListItem);
        });
        Collections.reverse(cycleListItemList);

        GUIUtil.setFitToRowsForTableView(cyclesTableView, 25, 28, 2, 4);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Create views: cyclesTableView
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createCyclesTable() {
        TableGroupHeadline headline = new TableGroupHeadline(Res.get("dao.results.cycles.header"));
        GridPane.setRowIndex(headline, ++gridRow);
        GridPane.setMargin(headline, new Insets(Layout.GROUP_DISTANCE, -10, -10, -10));
        GridPane.setColumnSpan(headline, 2);
        root.getChildren().add(headline);

        cyclesTableView = new TableView<>();
        cyclesTableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noData")));
        cyclesTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        createCycleColumns(cyclesTableView);

        GridPane.setRowIndex(cyclesTableView, gridRow);
        GridPane.setMargin(cyclesTableView, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE, -10, -15, -10));
        GridPane.setColumnSpan(cyclesTableView, 2);
        root.getChildren().add(cyclesTableView);

        cyclesTableView.setItems(sortedCycleListItemList);
        sortedCycleListItemList.comparatorProperty().bind(cyclesTableView.comparatorProperty());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Create views: proposalsTableView
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createProposalsTable() {
        TableGroupHeadline proposalsTableHeader = new TableGroupHeadline(Res.get("dao.results.proposals.header"));
        GridPane.setRowIndex(proposalsTableHeader, ++gridRow);
        GridPane.setMargin(proposalsTableHeader, new Insets(Layout.GROUP_DISTANCE, -10, -10, -10));
        GridPane.setColumnSpan(proposalsTableHeader, 2);
        root.getChildren().add(proposalsTableHeader);

        proposalsTableView = new TableView<>();
        proposalsTableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noData")));
        proposalsTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        createProposalsColumns(proposalsTableView);

        GridPane.setRowIndex(proposalsTableView, gridRow);
        GridPane.setMargin(proposalsTableView, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE, -10, 5, -10));
        GridPane.setColumnSpan(proposalsTableView, 2);
        root.getChildren().add(proposalsTableView);

        proposalsTableView.setItems(sortedProposalList);
        sortedProposalList.comparatorProperty().bind(proposalsTableView.comparatorProperty());

        proposalList.clear();
        proposalList.forEach(ProposalListItem::resetTableRow);

        Map<String, Ballot> ballotByProposalTxIdMap = daoFacade.getAllValidBallots().stream()
                .collect(Collectors.toMap(Ballot::getTxId, ballot -> ballot));
        proposalList.setAll(resultsOfCycle.getEvaluatedProposals().stream()
                .filter(evaluatedProposal -> {
                    boolean containsKey = ballotByProposalTxIdMap.containsKey(evaluatedProposal.getProposalTxId());

                    // We saw in testing that the ballot was not there for an evaluatedProposal. We could not reproduce that
                    // so far but to avoid a nullPointer we filter out such cases.
                    if (!containsKey)
                        log.warn("ballotByProposalTxIdMap does not contain expected proposalTxId()={}", evaluatedProposal.getProposalTxId());

                    return containsKey;
                })
                .map(evaluatedProposal -> new ProposalListItem(evaluatedProposal,
                        ballotByProposalTxIdMap.get(evaluatedProposal.getProposalTxId()),
                        bsqFormatter))
                .collect(Collectors.toList()));
        GUIUtil.setFitToRowsForTableView(proposalsTableView, 25, 28, 2, 4);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Create views: proposalDisplay
    ///////////////////////////////////////////////////////////////////////////////////////////

    private ProposalDisplay createProposalDisplay(EvaluatedProposal evaluatedProposal, Ballot ballot) {
        Proposal proposal = evaluatedProposal.getProposal();
        ProposalDisplay proposalDisplay = new ProposalDisplay(new GridPane(), bsqFormatter,
                daoFacade, null, navigation);

        ScrollPane proposalDisplayView = proposalDisplay.getView();
        GridPane.setMargin(proposalDisplayView, new Insets(0, -10, -15, -10));
        GridPane.setRowIndex(proposalDisplayView, ++gridRow);
        GridPane.setColumnSpan(proposalDisplayView, 2);
        GridPane.setHgrow(proposalDisplayView, Priority.ALWAYS);
        root.getChildren().add(proposalDisplayView);

        proposalDisplay.createAllFields(Res.get("dao.proposal.selectedProposal"), 0, 0,
                proposal.getType(), false);
        proposalDisplay.setEditable(false);

        proposalDisplay.applyProposalPayload(proposal);

        proposalDisplay.applyEvaluatedProposal(evaluatedProposal);

        Tuple2<Long, Long> meritAndStakeTuple = daoFacade.getMeritAndStakeForProposal(proposal.getTxId());
        long merit = meritAndStakeTuple.first;
        long stake = meritAndStakeTuple.second;
        proposalDisplay.applyBallotAndVoteWeight(ballot, merit, stake);
        return proposalDisplay;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Create views: votesTableView
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createVotesTable() {
        TableGroupHeadline votesTableHeader = new TableGroupHeadline(Res.get("dao.results.proposals.voting.detail.header"));
        GridPane.setRowIndex(votesTableHeader, ++gridRow);
        GridPane.setMargin(votesTableHeader, new Insets(Layout.GROUP_DISTANCE, -10, -10, -10));
        GridPane.setColumnSpan(votesTableHeader, 2);
        root.getChildren().add(votesTableHeader);

        TableView<VoteListItem> votesTableView = new TableView<>();
        votesTableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noData")));
        votesTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        createColumns(votesTableView);
        GridPane.setRowIndex(votesTableView, gridRow);
        GridPane.setMargin(votesTableView, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE, -10, -15, -10));
        GridPane.setColumnSpan(votesTableView, 2);
        root.getChildren().add(votesTableView);

        votesTableView.setItems(sortedVoteListItemList);
        sortedVoteListItemList.comparatorProperty().bind(votesTableView.comparatorProperty());

        voteListItemList.clear();
        resultsOfCycle.getEvaluatedProposals().stream()
                .filter(evaluatedProposal -> evaluatedProposal.getProposal().equals(selectedProposalListItem.getEvaluatedProposal().getProposal()))
                .forEach(evaluatedProposal -> {
                    resultsOfCycle.getDecryptedVotesForCycle().forEach(decryptedBallotsWithMerits -> {
                        voteListItemList.add(new VoteListItem(evaluatedProposal.getProposal(), decryptedBallotsWithMerits,
                                daoStateService, bsqFormatter));
                    });
                });

        voteListItemList.sort(Comparator.comparing(VoteListItem::getBlindVoteTxId));
        GUIUtil.setFitToRowsForTableView(votesTableView, 25, 28, 2, 4);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TableColumns: CycleListItem
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createCycleColumns(TableView<CycleListItem> votesTableView) {
        TableColumn<CycleListItem, CycleListItem> column;
        column = new AutoTooltipTableColumn<>(Res.get("dao.results.cycles.table.header.cycle"));
        column.setMinWidth(160);
        column.getStyleClass().add("first-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<CycleListItem, CycleListItem> call(
                            TableColumn<CycleListItem, CycleListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final CycleListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getCycle());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(CycleListItem::getCycleStartTime));
        votesTableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.results.cycles.table.header.numProposals"));
        column.setMinWidth(90);
        column.setMaxWidth(90);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<CycleListItem, CycleListItem> call(
                            TableColumn<CycleListItem, CycleListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final CycleListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getNumProposals());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(CycleListItem::getNumProposals));
        votesTableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.results.cycles.table.header.numVotes"));
        column.setMinWidth(70);
        column.setMaxWidth(70);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<CycleListItem, CycleListItem> call(
                            TableColumn<CycleListItem, CycleListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final CycleListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getNumVotesAsString());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(CycleListItem::getNumProposals));
        votesTableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.results.cycles.table.header.voteWeight"));
        column.setMinWidth(70);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<CycleListItem, CycleListItem> call(
                            TableColumn<CycleListItem, CycleListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final CycleListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getMeritAndStake());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(CycleListItem::getNumProposals));
        votesTableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.results.cycles.table.header.issuance"));
        column.setMinWidth(70);
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<CycleListItem, CycleListItem> call(
                            TableColumn<CycleListItem, CycleListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final CycleListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getIssuance());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(CycleListItem::getNumProposals));
        votesTableView.getColumns().add(column);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TableColumns: ProposalListItem
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createProposalsColumns(TableView<ProposalListItem> votesTableView) {
        TableColumn<ProposalListItem, ProposalListItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("shared.dateTime"));
        column.setMinWidth(190);
        column.setMaxWidth(column.getMinWidth());
        column.getStyleClass().add("first-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ProposalListItem, ProposalListItem> call(
                            TableColumn<ProposalListItem, ProposalListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final ProposalListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(bsqFormatter.formatDateTime(item.getProposal().getCreationDate()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(o3 -> o3.getProposal().getCreationDate()));
        column.setSortType(TableColumn.SortType.DESCENDING);
        votesTableView.getColumns().add(column);
        votesTableView.getSortOrder().add(column);


        column = new AutoTooltipTableColumn<>(Res.get("dao.results.proposals.table.header.proposalOwnerName"));
        column.setMinWidth(80);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ProposalListItem, ProposalListItem> call(
                            TableColumn<ProposalListItem, ProposalListItem> column) {
                        return new TableCell<>() {

                            @Override
                            public void updateItem(final ProposalListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null) {
                                    item.setTableRow(getTableRow());
                                    setText(item.getProposalOwnerName());
                                } else {
                                    setText("");
                                }
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(ProposalListItem::getProposalOwnerName));
        votesTableView.getColumns().add(column);


        column = new AutoTooltipTableColumn<>(Res.get("dao.proposal.table.header.link"));
        column.setMinWidth(100);
        column.setMaxWidth(column.getMinWidth());
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<ProposalListItem, ProposalListItem> call(TableColumn<ProposalListItem,
                            ProposalListItem> column) {
                        return new TableCell<>() {
                            private HyperlinkWithIcon field;

                            @Override
                            public void updateItem(final ProposalListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    final Proposal proposal = item.getProposal();
                                    field = new HyperlinkWithIcon(proposal.getLink(), MaterialDesignIcon.LINK);
                                    field.setOnAction(event -> GUIUtil.openWebPage(proposal.getLink()));
                                    field.setTooltip(new Tooltip(proposal.getLink()));
                                    setGraphic(field);
                                } else {
                                    setGraphic(null);
                                    if (field != null)
                                        field.setOnAction(null);
                                }
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(o -> o.getProposal().getTxId()));
        votesTableView.getColumns().add(column);


        column = new AutoTooltipTableColumn<>(Res.get("dao.proposal.table.header.proposalType"));
        column.setMinWidth(150);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ProposalListItem, ProposalListItem> call(
                            TableColumn<ProposalListItem, ProposalListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final ProposalListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getProposal().getType().getShortDisplayName());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(o2 -> o2.getProposal().getName()));
        votesTableView.getColumns().add(column);


        column = new AutoTooltipTableColumn<>(Res.get("dao.results.proposals.table.header.details"));
        column.setMinWidth(180);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ProposalListItem, ProposalListItem> call(
                            TableColumn<ProposalListItem, ProposalListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final ProposalListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getDetails());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(ProposalListItem::getDetails));
        votesTableView.getColumns().add(column);


        column = new AutoTooltipTableColumn<>(Res.get("dao.results.proposals.table.header.myVote"));
        column.setMinWidth(70);
        column.setMaxWidth(column.getMinWidth());
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {

            @Override
            public TableCell<ProposalListItem, ProposalListItem> call(TableColumn<ProposalListItem,
                    ProposalListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final ProposalListItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {
                            setGraphic(item.getMyVoteIcon());
                        } else {
                            setGraphic(null);
                        }
                    }
                };
            }
        });
        votesTableView.getColumns().add(column);


        column = new AutoTooltipTableColumn<>(Res.get("dao.results.proposals.table.header.result"));
        column.setMinWidth(90);
        column.setMaxWidth(column.getMinWidth());
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<ProposalListItem, ProposalListItem> call(TableColumn<ProposalListItem,
                    ProposalListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final ProposalListItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {
                            Label icon = new Label();
                            AwesomeDude.setIcon(icon, item.getIcon());
                            icon.getStyleClass().add(item.getColorStyleClass());
                            setGraphic(icon);
                        } else {
                            setGraphic(null);
                        }
                    }
                };
            }
        });
        votesTableView.getColumns().add(column);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TableColumns: VoteListItem
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createColumns(TableView<VoteListItem> votesTableView) {
        TableColumn<VoteListItem, VoteListItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("dao.results.votes.table.header.vote"));
        column.setSortable(false);
        column.setMinWidth(50);
        column.setMaxWidth(column.getMinWidth());
        column.getStyleClass().add("first-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<VoteListItem, VoteListItem> call(
                            TableColumn<VoteListItem, VoteListItem> column) {
                        return new TableCell<>() {
                            private Label icon;

                            @Override
                            public void updateItem(final VoteListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    Tuple2<AwesomeIcon, String> iconStyleTuple = item.getIconStyleTuple();
                                    icon = new Label();
                                    AwesomeDude.setIcon(icon, iconStyleTuple.first);
                                    icon.getStyleClass().add(iconStyleTuple.second);
                                    setGraphic(icon);
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
        votesTableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.results.votes.table.header.stakeAndMerit"));
        column.setSortable(false);
        column.setMinWidth(100);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<VoteListItem, VoteListItem> call(
                            TableColumn<VoteListItem, VoteListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final VoteListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getMeritAndStake());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        votesTableView.getColumns().add(column);
        column = new AutoTooltipTableColumn<>(Res.get("dao.results.votes.table.header.merit"));
        column.setSortable(false);
        column.setMinWidth(100);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<VoteListItem, VoteListItem> call(
                            TableColumn<VoteListItem, VoteListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final VoteListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getMerit());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        votesTableView.getColumns().add(column);

        column = new AutoTooltipTableColumn<>(Res.get("dao.results.votes.table.header.stake"));
        column.setSortable(false);
        column.setMinWidth(100);
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<VoteListItem, VoteListItem> call(
                            TableColumn<VoteListItem, VoteListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final VoteListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getStake());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        votesTableView.getColumns().add(column);
    }
}
