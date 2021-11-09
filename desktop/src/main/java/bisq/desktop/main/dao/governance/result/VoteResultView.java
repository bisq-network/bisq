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

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.ExternalHyperlink;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.TableGroupHeadline;
import bisq.desktop.main.dao.governance.PhasesView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.ProposalResultsWindow;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.period.CycleService;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.governance.proposal.ProposalService;
import bisq.core.dao.governance.voteresult.VoteResultException;
import bisq.core.dao.governance.voteresult.VoteResultService;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.governance.Ballot;
import bisq.core.dao.state.model.governance.BondedRoleType;
import bisq.core.dao.state.model.governance.ChangeParamProposal;
import bisq.core.dao.state.model.governance.CompensationProposal;
import bisq.core.dao.state.model.governance.ConfiscateBondProposal;
import bisq.core.dao.state.model.governance.Cycle;
import bisq.core.dao.state.model.governance.DaoPhase;
import bisq.core.dao.state.model.governance.DecryptedBallotsWithMerits;
import bisq.core.dao.state.model.governance.EvaluatedProposal;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.ProposalVoteResult;
import bisq.core.dao.state.model.governance.ReimbursementProposal;
import bisq.core.dao.state.model.governance.RemoveAssetProposal;
import bisq.core.dao.state.model.governance.Role;
import bisq.core.dao.state.model.governance.RoleProposal;
import bisq.core.dao.state.model.governance.Vote;
import bisq.core.locale.Res;
import bisq.core.util.coin.BsqFormatter;

import bisq.common.UserThread;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.inject.Inject;

import de.jensd.fx.fontawesome.AwesomeDude;

import javafx.stage.Stage;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import javafx.geometry.HPos;
import javafx.geometry.Insets;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static bisq.desktop.util.FormBuilder.addButton;

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
    private final BsqFormatter bsqFormatter;
    private final ProposalResultsWindow proposalResultsWindow;

    private Button exportButton;

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
    private boolean isVoteIncludedInResult;
    private final Set<Cycle> cyclesAdded = new HashSet<>();
    private boolean hasCalculatedResult = false;


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
                          BsqFormatter bsqFormatter,
                          ProposalResultsWindow proposalResultsWindow) {
        this.daoFacade = daoFacade;
        this.phasesView = phasesView;
        this.daoStateService = daoStateService;
        this.cycleService = cycleService;
        this.voteResultService = voteResultService;
        this.proposalService = proposalService;
        this.periodService = periodService;
        this.bsqWalletService = bsqWalletService;
        this.bsqFormatter = bsqFormatter;
        this.proposalResultsWindow = proposalResultsWindow;
    }

    @Override
    public void initialize() {
        gridRow = phasesView.addGroup(root, gridRow);

        selectedVoteResultListItemListener = (observable, oldValue, newValue) -> onResultsListItemSelected(newValue);

        createCyclesTable();
        exportButton = addButton(root, ++gridRow, Res.get("shared.exportJSON"));
        exportButton.getStyleClass().add("text-button");
        GridPane.setMargin(exportButton, new Insets(10, -10, -50, 0));
        GridPane.setColumnSpan(exportButton, 2);
        GridPane.setHalignment(exportButton, HPos.RIGHT);

        proposalResultsWindow.onClose(() -> proposalsTableView.getSelectionModel().clearSelection());
    }

    @Override
    protected void activate() {
        super.activate();

        phasesView.activate();

        daoFacade.addBsqStateListener(this);
        cyclesTableView.getSelectionModel().selectedItemProperty().addListener(selectedVoteResultListItemListener);

        if (daoStateService.isParseBlockChainComplete()) {
            checkForResultPhase(daoStateService.getChainHeight());
            fillCycleList();
        }

        exportButton.setOnAction(event -> {
            JsonElement cyclesJsonArray = getVotingHistoryJson();
            GUIUtil.exportJSON("voteResultsHistory.json", cyclesJsonArray, (Stage) root.getScene().getWindow());
        });
        if (proposalsTableView != null) {
            GUIUtil.setFitToRowsForTableView(proposalsTableView, 25, 28, 6, 6);

            selectedProposalSubscription = EasyBind.subscribe(proposalsTableView.getSelectionModel().selectedItemProperty(),
                    this::onSelectProposalResultListItem);
        }
        GUIUtil.setFitToRowsForTableView(cyclesTableView, 25, 28, 6, 6);
    }

    @Override
    protected void deactivate() {
        super.deactivate();

        phasesView.deactivate();

        daoFacade.removeBsqStateListener(this);
        cyclesTableView.getSelectionModel().selectedItemProperty().removeListener(selectedVoteResultListItemListener);

        if (selectedProposalSubscription != null)
            selectedProposalSubscription.unsubscribe();
        exportButton.setOnAction(null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        checkForResultPhase(daoStateService.getChainHeight());
        fillCycleList();
    }

    private void checkForResultPhase(int chainHeight) {
        if (periodService.isInPhase(chainHeight, DaoPhase.Phase.RESULT)) {
            if (!hasCalculatedResult) {
                hasCalculatedResult = true;
                // We had set the cycle initially but at the vote result we want to update it with the actual result.
                // We remove the empty cycle to make space for the one with the result.
                Optional<Cycle> optionalCurrentCycle = cyclesAdded.stream()
                        .filter(cycle -> cycle.isInCycle(chainHeight))
                        .findAny();
                optionalCurrentCycle.ifPresent(cyclesAdded::remove);
                Optional<CycleListItem> optionalCurrentCycleListItem = cycleListItemList.stream()
                        .filter(cycleListItem -> cycleListItem.getResultsOfCycle().getCycle().isInCycle(chainHeight))
                        .findAny();
                optionalCurrentCycleListItem.ifPresent(cycleListItemList::remove);
            }
        } else {
            // Reset to be ready to calculate result for next RESULT phase
            hasCalculatedResult = false;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onResultsListItemSelected(CycleListItem item) {
        if (selectedProposalSubscription != null)
            selectedProposalSubscription.unsubscribe();

        GUIUtil.removeChildrenFromGridPaneRows(root, 3, gridRow);
        gridRow = 2;

        if (item != null) {
            resultsOfCycle = item.getResultsOfCycle();

            // Check if my vote is included in result
            isVoteIncludedInResult = false;
            resultsOfCycle.getEvaluatedProposals().forEach(evProposal -> resultsOfCycle.getDecryptedVotesForCycle()
                    .forEach(decryptedBallotsWithMerits -> {
                        // Iterate through all included votes to see if any of those are ours
                        if (!isVoteIncludedInResult) {
                            isVoteIncludedInResult = bsqWalletService.isWalletTransaction(decryptedBallotsWithMerits
                                    .getVoteRevealTxId()).isPresent();
                        }
                    }));


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
                                .append(Res.getWithCol("shared.blindVoteTxId")).append(" ")
                                .append(e.getBlindVoteTxId()).append("\n")
                                .append(Res.getWithCol("dao.results.votes.table.header.stake")).append(" ")
                                .append(bsqFormatter.formatCoinWithCode(Coin.valueOf(e.getStake()))).append("\n");
                        e.getBallotList().stream().forEach(ballot -> {
                            sb.append(Res.getWithCol("shared.proposal")).append("\n\t")
                                    .append(Res.getWithCol("shared.name")).append(" ")
                                    .append(ballot.getProposal().getName()).append("\n\t");
                            sb.append(Res.getWithCol("dao.bond.table.column.link")).append(" ")
                                    .append(ballot.getProposal().getLink()).append("\n\t");
                            Vote vote = ballot.getVote();
                            String voteString = vote == null ? Res.get("dao.proposal.display.myVote.ignored") :
                                    vote.isAccepted() ?
                                            Res.get("dao.proposal.display.myVote.accepted") :
                                            Res.get("dao.proposal.display.myVote.rejected");
                            sb.append(Res.getWithCol("dao.results.votes.table.header.vote")).append(" ")
                                    .append(voteString).append("\n");

                        });
                    });
            if (sb.length() != 0) {
                new Popup().information(Res.get("dao.results.invalidVotes", sb.toString())).show();
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

        if (selectedProposalListItem != null) {
            EvaluatedProposal evaluatedProposal = selectedProposalListItem.getEvaluatedProposal();
            Optional<Ballot> optionalBallot = daoFacade.getAllValidBallots().stream()
                    .filter(ballot -> ballot.getTxId().equals(evaluatedProposal.getProposalTxId()))
                    .findAny();

            Ballot ballot = optionalBallot.orElse(null);
            voteListItemList.clear();
            resultsOfCycle.getEvaluatedProposals().stream()
                    .filter(evProposal -> evProposal.getProposal().equals(selectedProposalListItem.getEvaluatedProposal().getProposal()))
                    .forEach(evProposal -> resultsOfCycle.getDecryptedVotesForCycle().forEach(decryptedBallotsWithMerits ->
                            voteListItemList.add(new VoteListItem(evProposal.getProposal(), decryptedBallotsWithMerits,
                                    daoStateService, bsqFormatter))));

            voteListItemList.sort(Comparator.comparing(VoteListItem::getBlindVoteTxId));

            showProposalResultWindow(evaluatedProposal, ballot, isVoteIncludedInResult, sortedVoteListItemList);
        }
    }

    private void showProposalResultWindow(EvaluatedProposal evaluatedProposal,
                                          Ballot ballot,
                                          boolean isVoteIncludedInResult,
                                          SortedList<VoteListItem> sortedVoteListItemList) {
        proposalResultsWindow.show(evaluatedProposal, ballot, isVoteIncludedInResult, sortedVoteListItemList);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fill lists: Cycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void fillCycleList() {
        // At data creation we delay a bit so that the UI has a chance to display the placeholder.
        if (cyclesAdded.isEmpty()) {
            UserThread.runAfter(this::doFillCycleList, 50, TimeUnit.MILLISECONDS);
        } else {
            doFillCycleList();
        }
    }

    private void doFillCycleList() {
        // Creating our data structure is a bit expensive so we ensure to only create the CycleListItems once.
        daoStateService.getCycles().stream()
                .filter(cycle -> !cyclesAdded.contains(cycle))
                .forEach(cycle -> {
                    List<Proposal> proposalsForCycle = proposalService.getValidatedProposals().stream()
                            .filter(proposal -> cycleService.isTxInCycle(cycle, proposal.getTxId()))
                            .collect(Collectors.toList());

                    List<EvaluatedProposal> evaluatedProposalsForCycle = daoStateService.getEvaluatedProposalList().stream()
                            .filter(evaluatedProposal -> cycleService.isTxInCycle(cycle, evaluatedProposal.getProposal().getTxId()))
                            .collect(Collectors.toList());

                    AtomicLong stakeAndMerit = new AtomicLong();
                    List<DecryptedBallotsWithMerits> decryptedVotesForCycle = daoStateService.getDecryptedBallotsWithMeritsList().stream()
                            .filter(decryptedBallotsWithMerits -> cycleService.isTxInCycle(cycle, decryptedBallotsWithMerits.getBlindVoteTxId()))
                            .filter(decryptedBallotsWithMerits -> cycleService.isTxInCycle(cycle, decryptedBallotsWithMerits.getVoteRevealTxId()))
                            .peek(decryptedBallotsWithMerits -> stakeAndMerit.getAndAdd(decryptedBallotsWithMerits.getStake() + decryptedBallotsWithMerits.getMerit(daoStateService)))
                            .collect(Collectors.toList());

                    long cycleStartTime = daoStateService.getBlockTimeAtBlockHeight(cycle.getHeightOfFirstBlock());
                    int cycleIndex = cycleService.getCycleIndex(cycle);
                    ResultsOfCycle resultsOfCycle = new ResultsOfCycle(cycle,
                            cycleIndex,
                            cycleStartTime,
                            proposalsForCycle,
                            evaluatedProposalsForCycle,
                            decryptedVotesForCycle,
                            stakeAndMerit.get(),
                            daoStateService);
                    CycleListItem cycleListItem = new CycleListItem(resultsOfCycle, bsqFormatter);
                    cycleListItemList.add(cycleListItem);

                    cyclesAdded.add(resultsOfCycle.getCycle());
                });
        cycleListItemList.sort(Comparator.comparing(e -> ((CycleListItem) e).getResultsOfCycle().getCycleIndex()).reversed());

        GUIUtil.setFitToRowsForTableView(cyclesTableView, 25, 28, 6, 6);
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
        cyclesTableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.processingData")));
        cyclesTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        createCycleColumns(cyclesTableView);

        GridPane.setRowIndex(cyclesTableView, gridRow);
        GridPane.setMargin(cyclesTableView, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE, -10, -15, -10));
        GridPane.setColumnSpan(cyclesTableView, 2);
        GridPane.setVgrow(cyclesTableView, Priority.SOMETIMES);
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
        GridPane.setMargin(proposalsTableView, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE, -10, 0, -10));
        GridPane.setColumnSpan(proposalsTableView, 2);
        GridPane.setVgrow(proposalsTableView, Priority.ALWAYS);
        root.getChildren().add(proposalsTableView);

        proposalsTableView.setItems(sortedProposalList);
        sortedProposalList.comparatorProperty().bind(proposalsTableView.comparatorProperty());

        proposalList.forEach(ProposalListItem::resetTableRow);
        proposalList.clear();

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
                        isVoteIncludedInResult,
                        bsqFormatter))
                .collect(Collectors.toList()));
        GUIUtil.setFitToRowsForTableView(proposalsTableView, 25, 28, 6, 100);
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

        column = new AutoTooltipTableColumn<>(Res.get("shared.votes"));
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
        column.setComparator(Comparator.comparing(CycleListItem::getNumVotesAsString));
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
        column.setComparator(Comparator.comparing(CycleListItem::getMeritAndStake));
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
        column.setComparator(Comparator.comparing(CycleListItem::getIssuance));
        votesTableView.getColumns().add(column);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TableColumns: ProposalListItem
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createProposalsColumns(TableView<ProposalListItem> votesTableView) {
        TableColumn<ProposalListItem, ProposalListItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("shared.dateTime"));
        column.setMinWidth(160);
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
                                    setText(DisplayUtils.formatDateTime(item.getProposal().getCreationDateAsDate()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(item -> item.getProposal().getCreationDateAsDate()));
        column.setSortType(TableColumn.SortType.DESCENDING);
        votesTableView.getColumns().add(column);
        votesTableView.getSortOrder().add(column);


        column = new AutoTooltipTableColumn<>(Res.get("dao.results.proposals.table.header.nameLink"));
        column.setMinWidth(130);
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
                                    Proposal proposal = item.getProposal();
                                    String link = proposal.getLink();
                                    String proposalOwnerName = item.getProposalOwnerName();
                                    String[] tokens = link.split("/");
                                    String nameLink = proposalOwnerName;
                                    if (tokens.length > 0) {
                                        String proposalNr = tokens[tokens.length - 1];
                                        nameLink += " (#" + proposalNr + ")";
                                    }

                                    field = new ExternalHyperlink(nameLink);
                                    field.setOnAction(event -> GUIUtil.openWebPage(link));
                                    field.setTooltip(new Tooltip(proposalOwnerName + " (" + link + ")"));
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
        column.setComparator(Comparator.comparing((item -> item.getProposalOwnerName() + item.getProposal().getLink())));
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
        column.setComparator(Comparator.comparing(o2 -> o2.getProposal().getType().getShortDisplayName()));
        votesTableView.getColumns().add(column);


        column = new AutoTooltipTableColumn<>(Res.get("dao.results.proposals.table.header.details"));
        column.setMinWidth(100);
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
        // We sort by issued amount
        column.setComparator(Comparator.comparing(ProposalListItem::getDetails));
        votesTableView.getColumns().add(column);


        column = new AutoTooltipTableColumn<>(Res.get("dao.results.proposals.table.header.myVote"));
        column.setMinWidth(60);
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
        column.setSortable(false);
        votesTableView.getColumns().add(column);


        column = new AutoTooltipTableColumn<>(Res.get("dao.results.proposals.table.header.threshold"));
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
                                if (item != null)
                                    setText(item.getThresholdAsString());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(ProposalListItem::getThreshold));
        votesTableView.getColumns().add(column);


        column = new AutoTooltipTableColumn<>(Res.get("dao.results.proposals.table.header.quorum"));
        column.setMinWidth(90);
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
                                    setText(item.getQuorumAsString());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(ProposalListItem::getQuorum));
        votesTableView.getColumns().add(column);


        column = new AutoTooltipTableColumn<>(Res.get("dao.results.proposals.table.header.result"));
        column.setMinWidth(80);
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
        column.setComparator(Comparator.comparing(ProposalListItem::isAccepted));
        votesTableView.getColumns().add(column);
    }

    private JsonElement getVotingHistoryJson() {
        JsonArray cyclesArray = new JsonArray();

        sortedCycleListItemList.sorted(Comparator.comparing(CycleListItem::getCycleStartTime)).forEach(cycleListItem -> {
            JsonObject cycleJson = new JsonObject();
            // No domain data, taken from UI model
            // TODO move the data structure needed for UI to core and use as pure domain model and use that here
            cycleJson.addProperty("cycleIndex", cycleListItem.getCycleIndex());
            cycleJson.addProperty("cycleDateTime", cycleListItem.getCycleDateTime(false));
            cycleJson.addProperty("votesCount", cycleListItem.getNumVotesAsString());
            cycleJson.addProperty("voteWeight", cycleListItem.getMeritAndStake());
            cycleJson.addProperty("issuance", cycleListItem.getIssuance());
            cycleJson.addProperty("startTime", cycleListItem.getCycleStartTime());
            cycleJson.addProperty("totalAcceptedVotes", cycleListItem.getResultsOfCycle().getNumAcceptedVotes());
            cycleJson.addProperty("totalRejectedVotes", cycleListItem.getResultsOfCycle().getNumRejectedVotes());

            JsonArray proposalsArray = new JsonArray();
            List<EvaluatedProposal> evaluatedProposals = cycleListItem.getResultsOfCycle().getEvaluatedProposals();
            evaluatedProposals.sort(Comparator.comparingLong(o -> o.getProposal().getCreationDate()));

            evaluatedProposals.forEach(evaluatedProp -> {
                JsonObject proposalJson = new JsonObject();
                proposalJson.addProperty("isAccepted", evaluatedProp.isAccepted() ? "Accepted" : "Rejected");

                // Proposal
                Proposal proposal = evaluatedProp.getProposal();
                proposalJson.addProperty("proposal.name", proposal.getName());
                proposalJson.addProperty("proposal.link", proposal.getLink());
                proposalJson.addProperty("proposal.version", proposal.getVersion());
                proposalJson.addProperty("proposal.creationDate", proposal.getCreationDate());
                proposalJson.addProperty("proposal.txId", proposal.getTxId());
                proposalJson.addProperty("proposal.txType", proposal.getTxType().name());
                proposalJson.addProperty("proposal.quorumParam", proposal.getQuorumParam().name());
                proposalJson.addProperty("proposal.thresholdParam", proposal.getThresholdParam().name());
                proposalJson.addProperty("proposal.proposalType", proposal.getType().name());

                if (proposal.getExtraDataMap() != null)
                    proposalJson.addProperty("proposal.extraDataMap", proposal.getExtraDataMap().toString());

                switch (proposal.getType()) {
                    case UNDEFINED:
                        break;
                    case COMPENSATION_REQUEST:
                        CompensationProposal compensationProposal = (CompensationProposal) proposal;
                        proposalJson.addProperty("proposal.requestedBsq", compensationProposal.getRequestedBsq().getValue());
                        proposalJson.addProperty("proposal.bsqAddress", compensationProposal.getBsqAddress());
                        break;
                    case REIMBURSEMENT_REQUEST:
                        ReimbursementProposal reimbursementProposal = (ReimbursementProposal) proposal;
                        proposalJson.addProperty("proposal.requestedBsq", reimbursementProposal.getRequestedBsq().getValue());
                        proposalJson.addProperty("proposal.bsqAddress", reimbursementProposal.getBsqAddress());
                        break;
                    case CHANGE_PARAM:
                        ChangeParamProposal changeParamProposal = (ChangeParamProposal) proposal;
                        Param param = changeParamProposal.getParam();
                        proposalJson.addProperty("proposal.param", param.name());
                        proposalJson.addProperty("proposal.param.defaultValue", param.getDefaultValue());
                        proposalJson.addProperty("proposal.param.type", param.getParamType().name());
                        proposalJson.addProperty("proposal.param.maxDecrease", param.getMaxDecrease());
                        proposalJson.addProperty("proposal.param.maxIncrease", param.getMaxIncrease());
                        proposalJson.addProperty("proposal.paramValue", changeParamProposal.getParamValue());
                        break;
                    case BONDED_ROLE:
                        RoleProposal roleProposal = (RoleProposal) proposal;
                        Role role = roleProposal.getRole();
                        proposalJson.addProperty("proposal.requiredBondUnit", roleProposal.getRequiredBondUnit());
                        proposalJson.addProperty("proposal.unlockTime", roleProposal.getUnlockTime());
                        proposalJson.addProperty("proposal.role.uid", role.getUid());
                        proposalJson.addProperty("proposal.role.name", role.getName());
                        proposalJson.addProperty("proposal.role.link", role.getLink());
                        BondedRoleType bondedRoleType = role.getBondedRoleType();
                        proposalJson.addProperty("proposal.bondedRoleType", bondedRoleType.name());
                        // bondedRoleType enum must not change anyway so we don't print it
                        break;
                    case CONFISCATE_BOND:
                        ConfiscateBondProposal confiscateBondProposal = (ConfiscateBondProposal) proposal;
                        proposalJson.addProperty("proposal.lockupTxId", confiscateBondProposal.getLockupTxId());
                        break;
                    case GENERIC:
                        // No extra fields
                        break;
                    case REMOVE_ASSET:
                        RemoveAssetProposal removeAssetProposal = (RemoveAssetProposal) proposal;
                        proposalJson.addProperty("proposal.tickerSymbol", removeAssetProposal.getTickerSymbol());
                        break;
                }

                ProposalVoteResult proposalVoteResult = evaluatedProp.getProposalVoteResult();
                proposalJson.addProperty("stakeOfAcceptedVotes", proposalVoteResult.getStakeOfAcceptedVotes());
                proposalJson.addProperty("stakeOfRejectedVotes", proposalVoteResult.getStakeOfRejectedVotes());
                proposalJson.addProperty("numAcceptedVotes", proposalVoteResult.getNumAcceptedVotes());
                proposalJson.addProperty("numRejectedVotes", proposalVoteResult.getNumRejectedVotes());
                proposalJson.addProperty("numIgnoredVotes", proposalVoteResult.getNumIgnoredVotes());
                proposalJson.addProperty("numActiveVotes", proposalVoteResult.getNumActiveVotes());
                proposalJson.addProperty("quorum", proposalVoteResult.getQuorum());
                proposalJson.addProperty("threshold", proposalVoteResult.getThreshold());

                // Not part of pure domain data, but useful to add here
                // required quorum and threshold for cycle for proposal type
                proposalJson.addProperty("requiredQuorum", proposalService.getRequiredQuorum(proposal).value);
                proposalJson.addProperty("requiredThreshold", proposalService.getRequiredThreshold(proposal));

                // TODO provide better domain object as now we loop inside the loop. Use lookup map instead....
                JsonArray votesArray = new JsonArray();
                evaluatedProposals.stream()
                        .filter(evaluatedProposal -> evaluatedProposal.getProposal().equals(proposal))
                        .forEach(evaluatedProposal -> {
                            List<DecryptedBallotsWithMerits> decryptedVotesForCycle = cycleListItem.getResultsOfCycle().getDecryptedVotesForCycle();
                            // Make sure the votes are sorted so we can easier compare json files from different users
                            decryptedVotesForCycle.sort(Comparator.comparing(DecryptedBallotsWithMerits::getBlindVoteTxId));
                            decryptedVotesForCycle.forEach(decryptedBallotsWithMerits -> {
                                JsonObject voteJson = new JsonObject();
                                // Domain data of decryptedBallotsWithMerits
                                voteJson.addProperty("hashOfBlindVoteList", Utilities.bytesAsHexString(decryptedBallotsWithMerits.getHashOfBlindVoteList()));
                                voteJson.addProperty("blindVoteTxId", decryptedBallotsWithMerits.getBlindVoteTxId());
                                voteJson.addProperty("voteRevealTxId", decryptedBallotsWithMerits.getVoteRevealTxId());
                                voteJson.addProperty("stake", decryptedBallotsWithMerits.getStake());

                                voteJson.addProperty("voteWeight", decryptedBallotsWithMerits.getMerit(daoStateService));
                                String voteResult = decryptedBallotsWithMerits.getVote(evaluatedProp.getProposalTxId())
                                        .map(vote -> vote.isAccepted() ? "Accepted" : "Rejected")
                                        .orElse("Ignored");
                                voteJson.addProperty("vote", voteResult);
                                votesArray.add(voteJson);
                            });
                        });

                proposalJson.addProperty("numberOfVotes", votesArray.size());
                proposalJson.add("votes", votesArray);

                proposalsArray.add(proposalJson);
            });
            cycleJson.addProperty("numberOfProposals", proposalsArray.size());
            cycleJson.add("proposals", proposalsArray);
            cyclesArray.add(cycleJson);
        });
        return cyclesArray;
    }
}
