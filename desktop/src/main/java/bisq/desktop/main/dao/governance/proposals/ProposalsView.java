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

package bisq.desktop.main.dao.governance.proposals;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.TableGroupHeadline;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.components.TxIdTextField;
import bisq.desktop.main.dao.governance.PhasesView;
import bisq.desktop.main.dao.governance.ProposalDisplay;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.DAOTestingFeedbackWindow;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.BsqValidator;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.listeners.BsqBalanceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.blindvote.BlindVoteConsensus;
import bisq.core.dao.governance.myvote.MyVote;
import bisq.core.dao.governance.proposal.param.ChangeParamValidator;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.governance.Ballot;
import bisq.core.dao.state.model.governance.DaoPhase;
import bisq.core.dao.state.model.governance.DecryptedBallotsWithMerits;
import bisq.core.dao.state.model.governance.EvaluatedProposal;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.Vote;
import bisq.core.locale.Res;
import bisq.core.user.DontShowAgainLookup;
import bisq.core.user.Preferences;
import bisq.core.util.BSFormatter;
import bisq.core.util.BsqFormatter;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;
import bisq.common.util.Tuple4;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import com.jfoenix.controls.JFXButton;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import javafx.util.Callback;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.*;

@FxmlView
public class ProposalsView extends ActivatableView<GridPane, Void> implements BsqBalanceListener, DaoStateListener {
    private final DaoFacade daoFacade;
    private final BsqWalletService bsqWalletService;
    private final PhasesView phasesView;
    private final DaoStateService daoStateService;
    private final ChangeParamValidator changeParamValidator;
    private final Preferences preferences;
    private final BsqFormatter bsqFormatter;
    private final BSFormatter btcFormatter;
    private final Navigation navigation;

    private final ObservableList<ProposalsListItem> listItems = FXCollections.observableArrayList();
    private final SortedList<ProposalsListItem> sortedList = new SortedList<>(listItems);
    private final List<Button> voteButtons = new ArrayList<>();
    private final List<Node> voteFields = new ArrayList<>();

    private TableView<ProposalsListItem> tableView;
    private TitledGroupBg voteTitledGroupBg;
    private Label voteButtonInfoLabel;
    private TxIdTextField revealTxIdTextField, blindVoteTxIdTextField;
    private TextField meritTextField;
    private VBox blindVoteTxIdContainer, revealTxIdContainer;
    private Button removeProposalButton, acceptButton, rejectButton, ignoreButton, voteButton;
    private InputTextField stakeInputTextField;
    private ScrollPane proposalDisplayView;
    private GridPane proposalDisplayGridPane;
    private BusyAnimation voteButtonBusyAnimation;
    private ProposalDisplay proposalDisplay;

    private int gridRow = 0;
    private boolean proposalDisplayInitialized;
    private ProposalsListItem selectedItem;
    private DaoPhase.Phase currentPhase;
    private ListChangeListener<Proposal> proposalListChangeListener;
    private ListChangeListener<Ballot> ballotListChangeListener;
    private ChangeListener<String> stakeListener;
    private Subscription selectedProposalSubscription, phaseSubscription;
    private boolean areVoteButtonsVisible;
    private TableColumn<ProposalsListItem, ProposalsListItem> lastColumn;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ProposalsView(DaoFacade daoFacade,
                          BsqWalletService bsqWalletService,
                          PhasesView phasesView,
                          DaoStateService daoStateService,
                          ChangeParamValidator changeParamValidator,
                          Preferences preferences,
                          BsqFormatter bsqFormatter,
                          BSFormatter btcFormatter,
                          Navigation navigation) {
        this.daoFacade = daoFacade;
        this.bsqWalletService = bsqWalletService;
        this.phasesView = phasesView;
        this.daoStateService = daoStateService;
        this.changeParamValidator = changeParamValidator;
        this.preferences = preferences;
        this.bsqFormatter = bsqFormatter;
        this.btcFormatter = btcFormatter;
        this.navigation = navigation;
    }

    @Override
    public void initialize() {
        super.initialize();

        gridRow = phasesView.addGroup(root, gridRow);

        proposalDisplayGridPane = new GridPane();

        createProposalsTableView();
        createEmptyProposalDisplay();
        createVoteView();

        ballotListChangeListener = c -> updateListItems();
        proposalListChangeListener = c -> updateListItems();

        stakeListener = (observable, oldValue, newValue) -> updateViews();
    }

    @Override
    protected void activate() {
        phasesView.activate();

        phaseSubscription = EasyBind.subscribe(daoFacade.phaseProperty(), this::onPhaseChanged);
        selectedProposalSubscription = EasyBind.subscribe(tableView.getSelectionModel().selectedItemProperty(), this::onSelectProposal);

        sortedList.comparatorProperty().bind(tableView.comparatorProperty());

        daoFacade.getActiveOrMyUnconfirmedProposals().addListener(proposalListChangeListener);
        daoFacade.getAllBallots().addListener(ballotListChangeListener);
        daoFacade.addBsqStateListener(this);
        bsqWalletService.addBsqBalanceListener(this);

        stakeInputTextField.textProperty().addListener(stakeListener);
        voteButton.setOnAction(e -> onVote());

        onUpdateBalances(bsqWalletService.getAvailableConfirmedBalance(),
                bsqWalletService.getAvailableNonBsqBalance(),
                bsqWalletService.getUnverifiedBalance(),
                bsqWalletService.getUnconfirmedChangeBalance(),
                bsqWalletService.getLockedForVotingBalance(),
                bsqWalletService.getLockupBondsBalance(),
                bsqWalletService.getUnlockingBondsBalance());

        updateListItems();
        GUIUtil.setFitToRowsForTableView(tableView, 38, 28, 2, 6);
        updateViews();
    }

    @Override
    protected void deactivate() {
        phasesView.deactivate();

        phaseSubscription.unsubscribe();
        selectedProposalSubscription.unsubscribe();

        sortedList.comparatorProperty().unbind();

        daoFacade.getActiveOrMyUnconfirmedProposals().removeListener(proposalListChangeListener);
        daoFacade.getAllBallots().removeListener(ballotListChangeListener);
        daoFacade.removeBsqStateListener(this);
        bsqWalletService.removeBsqBalanceListener(this);

        if (stakeInputTextField != null) {
            stakeInputTextField.textProperty().removeListener(stakeListener);
            stakeInputTextField.clear();
        }
        if (voteButton != null)
            voteButton.setOnAction(null);
        if (removeProposalButton != null)
            removeProposalButton.setOnAction(null);

        listItems.forEach(ProposalsListItem::cleanup);
        tableView.getSelectionModel().clearSelection();
        selectedItem = null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqBalanceListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onUpdateBalances(Coin availableConfirmedBalance,
                                 Coin availableNonBsqBalance,
                                 Coin unverifiedBalance,
                                 Coin unconfirmedChangeBalance,
                                 Coin lockedForVotingBalance,
                                 Coin lockupBondsBalance,
                                 Coin unlockingBondsBalance) {
        Coin blindVoteFee = BlindVoteConsensus.getFee(daoStateService, daoStateService.getChainHeight());
        if (isBlindVotePhaseButNotLastBlock()) {
            Coin availableForVoting = availableConfirmedBalance.subtract(blindVoteFee);
            stakeInputTextField.setPromptText(Res.get("dao.proposal.myVote.stake.prompt",
                    bsqFormatter.formatCoinWithCode(availableForVoting)));
        } else
            stakeInputTextField.setPromptText("");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        updateViews();
    }

    @Override
    public void onParseBlockChainComplete() {
        updateListItems();
        applyMerit();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void fillListItems() {
        if (daoFacade.phaseProperty().get().ordinal() < DaoPhase.Phase.BLIND_VOTE.ordinal()) {
            // proposal phase
            List<Proposal> list = daoFacade.getActiveOrMyUnconfirmedProposals();
            listItems.setAll(list.stream()
                    .map(proposal -> new ProposalsListItem(proposal, daoFacade, bsqFormatter))
                    .collect(Collectors.toSet()));
        } else {
            // blind vote phase
            List<Ballot> ballotList = daoFacade.getBallotsOfCycle();
            listItems.setAll(ballotList.stream()
                    .map(ballot -> new ProposalsListItem(ballot, daoFacade, bsqFormatter))
                    .collect(Collectors.toSet()));
        }

        updateViews();
    }

    private void updateListItems() {
        listItems.forEach(ProposalsListItem::cleanup);
        listItems.clear();

        fillListItems();

        if (listItems.isEmpty())
            hideProposalDisplay();


        if (!tableView.getItems().isEmpty()) {
            onSelectProposal(tableView.getItems().get(0));
            onSelectProposal(null);
        }

        GUIUtil.setFitToRowsForTableView(tableView, 38, 28, 2, 6);
    }

    private void createAllFieldsOnProposalDisplay(Proposal proposal, @Nullable Ballot ballot,
                                                  @Nullable EvaluatedProposal evaluatedProposal) {
        proposalDisplayView.setVisible(true);
        proposalDisplayView.setManaged(true);

        proposalDisplay.createAllFields(Res.get("dao.proposal.selectedProposal"), 0, 0, proposal.getType(),
                false);
        proposalDisplay.setEditable(false);

        proposalDisplay.applyProposalPayload(proposal);

        proposalDisplay.applyEvaluatedProposal(evaluatedProposal);

        Tuple2<Long, Long> meritAndStakeTuple = daoFacade.getMeritAndStakeForProposal(proposal.getTxId());
        long merit = meritAndStakeTuple.first;
        long stake = meritAndStakeTuple.second;
        proposalDisplay.applyBallotAndVoteWeight(ballot, merit, stake);

        proposalDisplayInitialized = true;

        removeProposalButton = addButtonAfterGroup(proposalDisplayGridPane, proposalDisplay.incrementAndGetGridRow(), Res.get("shared.remove"));
        removeProposalButton.setOnAction(event -> onRemoveProposal());
        onPhaseChanged(daoFacade.phaseProperty().get());

        Tuple3<Button, Button, Button> tuple = add3ButtonsAfterGroup(proposalDisplayGridPane,
                proposalDisplay.incrementAndGetGridRow(),
                Res.get("dao.proposal.myVote.accept"),
                Res.get("dao.proposal.myVote.reject"),
                Res.get("dao.proposal.myVote.removeMyVote"));
        acceptButton = tuple.first;
        acceptButton.setDefaultButton(false);
        rejectButton = tuple.second;
        ignoreButton = tuple.third;
        acceptButton.setOnAction(event -> onAccept());
        rejectButton.setOnAction(event -> onReject());
        ignoreButton.setOnAction(event -> onIgnore());

        voteButtons.clear();
        voteButtons.add(voteButton);
        voteButtons.add(acceptButton);
        voteButtons.add(rejectButton);
        voteButtons.add(ignoreButton);
    }

    private void hideProposalDisplay() {
        if (proposalDisplayInitialized) {
            proposalDisplay.removeAllFields();
            proposalDisplayView.setVisible(false);
            proposalDisplayView.setManaged(false);
        }
        if (removeProposalButton != null) {
            removeProposalButton.setManaged(false);
            removeProposalButton.setVisible(false);
        }

        if (acceptButton != null) {
            acceptButton.setManaged(false);
            acceptButton.setVisible(false);
        }
        if (rejectButton != null) {
            rejectButton.setManaged(false);
            rejectButton.setVisible(false);
        }
        if (ignoreButton != null) {
            ignoreButton.setManaged(false);
            ignoreButton.setVisible(false);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onPhaseChanged(DaoPhase.Phase phase) {
        if (phase != null && !phase.equals(currentPhase)) {
            currentPhase = phase;
            stakeInputTextField.clear();
            onSelectProposal(selectedItem);
        }

        if (removeProposalButton != null) {
            boolean doShowRemoveButton = phase == DaoPhase.Phase.PROPOSAL &&
                    selectedItem != null &&
                    daoFacade.isMyProposal(selectedItem.getProposal());
            removeProposalButton.setVisible(doShowRemoveButton);
            removeProposalButton.setManaged(doShowRemoveButton);
        }

        updateViews();
    }

    private void onRemoveProposal() {
        if (daoFacade.phaseProperty().get() == DaoPhase.Phase.PROPOSAL) {
            Proposal proposal = selectedItem.getProposal();
            new Popup<>().warning(Res.get("dao.proposal.active.remove.confirm"))
                    .actionButtonText(Res.get("dao.proposal.active.remove.doRemove"))
                    .onAction(() -> {
                        if (daoFacade.removeMyProposal(proposal)) {
                            hideProposalDisplay();
                        } else {
                            new Popup<>().warning(Res.get("dao.proposal.active.remove.failed")).show();
                        }
                        tableView.getSelectionModel().clearSelection();
                    })
                    .show();
        }
    }

    private void onSelectProposal(ProposalsListItem item) {
        selectedItem = item;
        if (selectedItem != null) {
            EvaluatedProposal evaluatedProposal = daoStateService.getEvaluatedProposalList().stream()
                    .filter(e -> daoFacade.isTxInCorrectCycle(e.getProposal().getTxId(),
                            daoFacade.getChainHeight()))
                    .filter(e -> e.getProposalTxId().equals(selectedItem.getProposal().getTxId()))
                    .findAny()
                    .orElse(null);

            createAllFieldsOnProposalDisplay(selectedItem.getProposal(), selectedItem.getBallot(), evaluatedProposal);
            applyMerit();
        } else {
            hideProposalDisplay();
        }
        onPhaseChanged(daoFacade.phaseProperty().get());

        updateViews();
    }

    private void applyMerit() {
        // We check if we have voted on that proposal. If so we use the merit used in the vote, otherwise we
        // use the merit based on all past issuance with the time decay applied.
        // The merit from the vote stays the same over blocks, the merit from daoFacade.getMeritAndStake()
        // decreases with every block a bit (over 2 years it goes to zero).
        boolean hasConfirmedVoteTxInCycle = daoFacade.getMyVoteListForCycle().stream()
                .map(myVote -> daoFacade.getTx(myVote.getTxId()))
                .findAny()
                .isPresent();
        long merit;
        if (selectedItem != null && hasConfirmedVoteTxInCycle) {
            merit = daoFacade.getMeritAndStakeForProposal(selectedItem.getProposal().getTxId()).first;
        } else {
            merit = daoFacade.getAvailableMerit();
        }

        meritTextField.setText(bsqFormatter.formatCoinWithCode(Coin.valueOf(merit)));
    }

    private void onAccept() {
        daoFacade.setVote(getBallotListItem().getBallot(), new Vote(true));
        proposalDisplay.applyBallot(getBallotListItem().getBallot());
        updateStateAfterVote();
    }

    private void onReject() {
        daoFacade.setVote(getBallotListItem().getBallot(), new Vote(false));
        proposalDisplay.applyBallot(getBallotListItem().getBallot());
        updateStateAfterVote();
    }

    private void onIgnore() {
        daoFacade.setVote(getBallotListItem().getBallot(), null);
        proposalDisplay.applyBallot(getBallotListItem().getBallot());
        updateStateAfterVote();
    }

    private void onVote() {
        // TODO verify stake
        Coin stake = bsqFormatter.parseToCoin(stakeInputTextField.getText());
        try {
            // We create a dummy tx to get the miningFee for displaying it at the confirmation popup
            Tuple2<Coin, Integer> miningFeeAndTxSize = daoFacade.getMiningFeeAndTxSize(stake);
            Coin miningFee = miningFeeAndTxSize.first;
            int txSize = miningFeeAndTxSize.second;
            Coin blindVoteFee = daoFacade.getBlindVoteFeeForCycle();
            if (!DevEnv.isDevMode()) {
                GUIUtil.showBsqFeeInfoPopup(blindVoteFee, miningFee, txSize, bsqFormatter, btcFormatter,
                        Res.get("dao.blindVote"), () -> publishBlindVote(stake));
            } else {
                publishBlindVote(stake);
            }
        } catch (InsufficientMoneyException | WalletException | TransactionVerificationException exception) {
            new Popup<>().warning(exception.toString()).show();
        }
    }

    private void publishBlindVote(Coin stake) {
        voteButtonBusyAnimation.play();
        voteButtonInfoLabel.setText(Res.get("dao.blindVote.startPublishing"));
        daoFacade.publishBlindVote(stake,
                () -> {
                    if (!DevEnv.isDevMode())
                        new Popup<>().feedback(Res.get("dao.blindVote.success")).show();
                }, exception -> {
                    voteButtonBusyAnimation.stop();
                    voteButtonInfoLabel.setText("");
                    updateViews();
                    new Popup<>().warning(exception.toString()).show();
                });

        // We reset UI without waiting for callback as callback might be slow and then the user could click
        // multiple times.
        voteButtonBusyAnimation.stop();
        voteButtonInfoLabel.setText("");
        updateViews();
    }

    private void updateStateAfterVote() {
        updateViews();
        tableView.refresh();
    }

    private ProposalsListItem getBallotListItem() {
        return selectedItem;
    }

    private Optional<Vote> getVote(@Nullable Ballot ballot) {
        if (ballot == null)
            return Optional.empty();
        else
            return ballot.getVoteAsOptional();
    }

    private void updateViews() {
        boolean isBlindVotePhaseButNotLastBlock = isBlindVotePhaseButNotLastBlock();
        boolean hasVotedOnProposal = hasVotedOnProposal();
        voteButton.setDisable(!hasVotedOnProposal ||
                !stakeInputTextField.getValidator().validate(stakeInputTextField.getText()).isValid);

        List<MyVote> myVoteListForCycle = daoFacade.getMyVoteListForCycle();
        boolean hasAlreadyVoted = !myVoteListForCycle.isEmpty();
        if (selectedItem != null && acceptButton != null) {
            Optional<Vote> optionalVote = getVote(selectedItem.getBallot());
            boolean isPresent = optionalVote.isPresent();
            boolean isAccepted = isPresent && optionalVote.get().isAccepted();
            acceptButton.setDisable((isPresent && isAccepted));
            rejectButton.setDisable((isPresent && !isAccepted));
            ignoreButton.setDisable(!isPresent);

            stakeInputTextField.setMouseTransparent(hasAlreadyVoted || !isBlindVotePhaseButNotLastBlock);
        } else {
            stakeInputTextField.setMouseTransparent(true);
        }

        boolean hasProposals = !daoFacade.getActiveOrMyUnconfirmedProposals().isEmpty();
        boolean showVoteFields = (isBlindVotePhaseButNotLastBlock && hasProposals) || hasAlreadyVoted;

        voteFields.forEach(node -> {
            node.setVisible(showVoteFields);
            node.setManaged(showVoteFields);
        });
        areVoteButtonsVisible = hasProposals && isBlindVotePhaseButNotLastBlock && !hasAlreadyVoted;
        voteButtons.forEach(button -> {
            button.setVisible(areVoteButtonsVisible);
            button.setManaged(areVoteButtonsVisible);
        });

        blindVoteTxIdTextField.setup("");
        revealTxIdTextField.setup("");

        blindVoteTxIdContainer.setVisible(false);
        blindVoteTxIdContainer.setManaged(false);
        revealTxIdContainer.setVisible(false);
        revealTxIdContainer.setManaged(false);

        if (hasAlreadyVoted) {
            voteTitledGroupBg.setText(Res.get("dao.proposal.votes.header.voted"));
            if (myVoteListForCycle.size() == 1) {
                Optional<MyVote> optionalMyVote = myVoteListForCycle.stream()
                        .filter(myVote -> daoFacade.isTxInCorrectCycle(myVote.getHeight(), daoFacade.getChainHeight()))
                        .findAny();
                if (optionalMyVote.isPresent()) {
                    MyVote myVote = optionalMyVote.get();
                    Coin stake = Coin.valueOf(myVote.getBlindVote().getStake());
                    stakeInputTextField.setText(bsqFormatter.formatCoinWithCode(stake));

                    if (myVote.getTxId() != null) {
                        blindVoteTxIdTextField.setup(myVote.getTxId());
                        blindVoteTxIdContainer.setVisible(true);
                        blindVoteTxIdContainer.setManaged(true);
                    }

                    if (myVote.getRevealTxId() != null) {
                        revealTxIdTextField.setup(myVote.getRevealTxId());
                        revealTxIdContainer.setVisible(true);
                        revealTxIdContainer.setManaged(true);
                    }
                } else {
                    stakeInputTextField.clear();
                }
            } else {
                String msg = "We found multiple MyVote entries in that cycle. That is not supported by the UI.";
                log.warn(msg);
                String id = "multipleVotes";
                if (preferences.showAgain(id))
                    new Popup<>().warning(msg).dontShowAgainId(id).show();
            }
            voteButton.setVisible(false);
            voteButton.setManaged(false);
        }

        switch (daoFacade.phaseProperty().get()) {
            case PROPOSAL:
                lastColumn.setText(Res.get("dao.proposal.table.header.remove"));
                break;
            case BLIND_VOTE:
                lastColumn.setText(Res.get("dao.proposal.table.header.myVote"));
                break;
            default:
                lastColumn.setText("");
                break;
        }
    }

    private boolean hasVotedOnProposal() {
        return listItems.stream()
                .filter(e -> e.getBallot() != null)
                .map(ProposalsListItem::getBallot)
                .anyMatch(e -> e.getVote() != null);
    }

    private boolean isBlindVotePhaseButNotLastBlock() {
        return daoFacade.isInPhaseButNotLastBlock(DaoPhase.Phase.BLIND_VOTE);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Create views
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createProposalsTableView() {
        TableGroupHeadline proposalsHeadline = new TableGroupHeadline(Res.get("dao.proposal.active.header"));
        GridPane.setRowIndex(proposalsHeadline, ++gridRow);
        GridPane.setMargin(proposalsHeadline, new Insets(Layout.GROUP_DISTANCE, -10, -10, -10));
        root.getChildren().add(proposalsHeadline);

        tableView = new TableView<>();
        tableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noData")));
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        createProposalColumns();
        GridPane.setRowIndex(tableView, gridRow);
        GridPane.setHgrow(tableView, Priority.ALWAYS);
        GridPane.setMargin(tableView, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE, -10, 5, -10));
        root.getChildren().add(tableView);

        tableView.setItems(sortedList);
    }

    private void createEmptyProposalDisplay() {
        proposalDisplay = new ProposalDisplay(proposalDisplayGridPane, bsqFormatter, daoFacade,
                changeParamValidator, navigation, preferences);
        proposalDisplayView = proposalDisplay.getView();
        GridPane.setMargin(proposalDisplayView, new Insets(0, -10, 0, -10));
        GridPane.setRowIndex(proposalDisplayView, ++gridRow);
        GridPane.setHgrow(proposalDisplayView, Priority.ALWAYS);
        root.getChildren().add(proposalDisplayView);
    }

    private void createVoteView() {
        voteTitledGroupBg = addTitledGroupBg(root, ++gridRow, 4,
                Res.get("dao.proposal.votes.header"), 20);
        voteFields.add(voteTitledGroupBg);

        Tuple3<Label, TextField, VBox> meritTuple = addTopLabelTextField(root, gridRow,
                Res.get("dao.proposal.myVote.merit"), 40);
        Label meritLabel = meritTuple.first;
        meritTextField = meritTuple.second;
        meritTextField.setText(bsqFormatter.formatCoinWithCode(Coin.ZERO));
        voteFields.add(meritLabel);
        voteFields.add(meritTextField);
        voteFields.add(meritTuple.third);

        stakeInputTextField = addInputTextField(root, ++gridRow,
                Res.get("dao.proposal.myVote.stake"));
        stakeInputTextField.setValidator(new BsqValidator(bsqFormatter));
        voteFields.add(stakeInputTextField);

        Tuple3<Label, TxIdTextField, VBox> tuple = addTopLabelTxIdTextField(root, ++gridRow,
                Res.get("dao.proposal.myVote.blindVoteTxId"), 0);
        blindVoteTxIdTextField = tuple.second;
        blindVoteTxIdContainer = tuple.third;
        blindVoteTxIdTextField.setBsq(true);
        voteFields.add(blindVoteTxIdContainer);

        tuple = addTopLabelTxIdTextField(root, ++gridRow,
                Res.get("dao.proposal.myVote.revealTxId"), 0);
        revealTxIdTextField = tuple.second;
        revealTxIdTextField.setBsq(true);
        revealTxIdContainer = tuple.third;
        voteFields.add(revealTxIdContainer);

        Tuple4<Button, BusyAnimation, Label, HBox> voteButtonTuple = addButtonBusyAnimationLabelAfterGroup(root, ++gridRow,
                Res.get("dao.proposal.myVote.button"));
        voteButton = voteButtonTuple.first;
        voteButtons.add(voteButton);
        voteFields.add(voteButtonTuple.forth);
        voteButtonBusyAnimation = voteButtonTuple.second;
        voteButtonInfoLabel = voteButtonTuple.third;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TableColumns
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createProposalColumns() {
        TableColumn<ProposalsListItem, ProposalsListItem> column;

        column = new AutoTooltipTableColumn<>(Res.get("shared.dateTime"));
        column.setMinWidth(190);
        column.setMaxWidth(column.getMinWidth());
        column.getStyleClass().add("first-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ProposalsListItem, ProposalsListItem> call(
                            TableColumn<ProposalsListItem, ProposalsListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final ProposalsListItem item, boolean empty) {
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
        tableView.getColumns().add(column);
        tableView.getSortOrder().add(column);


        column = new AutoTooltipTableColumn<>(Res.get("shared.name"));
        column.setMinWidth(60);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ProposalsListItem, ProposalsListItem> call(
                            TableColumn<ProposalsListItem, ProposalsListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final ProposalsListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getProposal().getName());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(o2 -> o2.getProposal().getName()));
        tableView.getColumns().add(column);


        column = new AutoTooltipTableColumn<>(Res.get("dao.proposal.table.header.link"));
        column.setMinWidth(80);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<ProposalsListItem, ProposalsListItem> call(TableColumn<ProposalsListItem,
                            ProposalsListItem> column) {
                        return new TableCell<>() {
                            private HyperlinkWithIcon field;

                            @Override
                            public void updateItem(final ProposalsListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    final Proposal proposal = item.getProposal();
                                    field = new HyperlinkWithIcon(proposal.getLink());
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
        tableView.getColumns().add(column);


        column = new AutoTooltipTableColumn<>(Res.get("dao.proposal.table.header.proposalType"));
        column.setMinWidth(60);
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<ProposalsListItem, ProposalsListItem> call(
                            TableColumn<ProposalsListItem, ProposalsListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final ProposalsListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(Res.get("dao.proposal.type." + item.getProposal().getType().name()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(o2 -> o2.getProposal().getName()));
        tableView.getColumns().add(column);


        column = new TableColumn<>(Res.get("dao.proposal.table.header.myVote"));
        column.setMinWidth(60);
        column.setMaxWidth(column.getMinWidth());
        column.getStyleClass().add("last-column");
        column.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<ProposalsListItem, ProposalsListItem> call(TableColumn<ProposalsListItem,
                    ProposalsListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final ProposalsListItem item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            item.onPhaseChanged(currentPhase);
                            JFXButton iconButton = item.getIconButton();
                            if (iconButton != null) {
                                iconButton.setOnAction(e -> {
                                    onSelectProposal(item);
                                    if (areVoteButtonsVisible) {
                                        if (iconButton.getUserData() == ProposalsListItem.IconButtonTypes.ACCEPT)
                                            onReject();
                                        else if (iconButton.getUserData() == ProposalsListItem.IconButtonTypes.REJECT)
                                            onIgnore();
                                        else if (iconButton.getUserData() == ProposalsListItem.IconButtonTypes.IGNORE)
                                            onAccept();
                                    } else {
                                        if (iconButton.getUserData() == ProposalsListItem.IconButtonTypes.REMOVE_PROPOSAL)
                                            onRemoveProposal();
                                    }
                                });

                                if (!areVoteButtonsVisible && iconButton.getUserData() != ProposalsListItem.IconButtonTypes.REMOVE_PROPOSAL) {
                                    iconButton.setMouseTransparent(true);
                                    iconButton.setStyle("-fx-cursor: default;");
                                }
                                setGraphic(iconButton);
                            }
                        } else {
                            setGraphic(null);
                        }
                    }
                };
            }
        });
        tableView.getColumns().add(column);
        lastColumn = column;
    }
}
