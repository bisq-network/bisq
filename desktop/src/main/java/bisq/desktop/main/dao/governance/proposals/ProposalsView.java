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
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.SelectProposalWindow;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.BsqValidator;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.listeners.BsqBalanceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.blindvote.BlindVoteConsensus;
import bisq.core.dao.governance.blindvote.MyBlindVoteListService;
import bisq.core.dao.governance.myvote.MyVote;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.governance.Ballot;
import bisq.core.dao.state.model.governance.DaoPhase;
import bisq.core.dao.state.model.governance.EvaluatedProposal;
import bisq.core.dao.state.model.governance.Proposal;
import bisq.core.dao.state.model.governance.Vote;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;
import bisq.core.util.FormattingUtils;
import bisq.core.util.ParsingUtils;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.InputValidator;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;
import bisq.common.util.Tuple4;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;

import javax.inject.Inject;
import javax.inject.Named;

import com.jfoenix.controls.JFXButton;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.*;
import static bisq.desktop.util.Layout.INITIAL_WINDOW_HEIGHT;

@FxmlView
public class ProposalsView extends ActivatableView<GridPane, Void> implements BsqBalanceListener, DaoStateListener {
    private final DaoFacade daoFacade;
    private final BsqWalletService bsqWalletService;
    private final PhasesView phasesView;
    private final DaoStateService daoStateService;
    private final MyBlindVoteListService myBlindVoteListService;
    private final Preferences preferences;
    private final BsqFormatter bsqFormatter;
    private final CoinFormatter btcFormatter;
    private final SelectProposalWindow selectProposalWindow;

    private final ObservableList<ProposalsListItem> listItems = FXCollections.observableArrayList();
    private final SortedList<ProposalsListItem> sortedList = new SortedList<>(listItems);
    private final List<Button> voteButtons = new ArrayList<>();
    private final List<Node> voteFields = new ArrayList<>();

    private TableView<ProposalsListItem> tableView;
    private Label voteButtonInfoLabel;
    private TxIdTextField revealTxIdTextField, blindVoteTxIdTextField;
    private TextField meritTextField;
    private VBox blindVoteTxIdContainer, revealTxIdContainer;
    private Button voteButton;
    private InputTextField stakeInputTextField;
    private BusyAnimation voteButtonBusyAnimation;

    private int gridRow = 0;
    @Nullable
    private ProposalsListItem selectedItem;
    private DaoPhase.Phase currentPhase;
    private ListChangeListener<Proposal> proposalListChangeListener;
    private ListChangeListener<Ballot> ballotListChangeListener;
    private ChangeListener<String> stakeListener;
    private Subscription selectedProposalSubscription, phaseSubscription;
    private boolean areVoteButtonsVisible;
    private TableColumn<ProposalsListItem, ProposalsListItem> lastColumn;
    private String shownVoteOnProposalWindowForTxId = "";

    private final Function<Double, Double> proposalTableViewHeight = (screenSize) -> {
        double initialProposalTableViewHeight = 180;
        double pixelsPerProposalTableRow = (initialProposalTableViewHeight - 28) / 4.0;
        int extraRows = screenSize <= INITIAL_WINDOW_HEIGHT ? 0 : (int) ((screenSize - INITIAL_WINDOW_HEIGHT) / pixelsPerProposalTableRow);
        return extraRows == 0 ? initialProposalTableViewHeight : Math.ceil(initialProposalTableViewHeight + (extraRows * pixelsPerProposalTableRow));
    };
    private ChangeListener<Number> sceneHeightListener;
    private TableGroupHeadline proposalsHeadline;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ProposalsView(DaoFacade daoFacade,
                          BsqWalletService bsqWalletService,
                          PhasesView phasesView,
                          DaoStateService daoStateService,
                          MyBlindVoteListService myBlindVoteListService,
                          Preferences preferences,
                          BsqFormatter bsqFormatter,
                          @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter,
                          SelectProposalWindow selectProposalWindow) {
        this.daoFacade = daoFacade;
        this.bsqWalletService = bsqWalletService;
        this.phasesView = phasesView;
        this.daoStateService = daoStateService;
        this.myBlindVoteListService = myBlindVoteListService;
        this.preferences = preferences;
        this.bsqFormatter = bsqFormatter;
        this.btcFormatter = btcFormatter;
        this.selectProposalWindow = selectProposalWindow;
    }

    @Override
    public void initialize() {
        super.initialize();

        gridRow = phasesView.addGroup(root, gridRow);

        createProposalsTableView();
        createVoteView();

        ballotListChangeListener = c -> updateListItems();
        proposalListChangeListener = c -> updateListItems();

        sceneHeightListener = (observable, oldValue, newValue) -> updateTableHeight(newValue.doubleValue());

        stakeListener = (observable, oldValue, newValue) -> updateViews();
    }

    @Override
    protected void activate() {
        phasesView.activate();

        selectedProposalSubscription = EasyBind.subscribe(tableView.getSelectionModel().selectedItemProperty(), this::onSelectProposal);

        daoFacade.addBsqStateListener(this);

        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setPrefHeight(100);
        root.getScene().heightProperty().addListener(sceneHeightListener);
        UserThread.execute(() -> {
            if (root.getScene() != null)
                updateTableHeight(root.getScene().getHeight());
        });

        stakeInputTextField.textProperty().addListener(stakeListener);
        voteButton.setOnAction(e -> onVote());

        onUpdateBalances(bsqWalletService.getAvailableBalance(),
                bsqWalletService.getAvailableNonBsqBalance(),
                bsqWalletService.getUnverifiedBalance(),
                bsqWalletService.getUnconfirmedChangeBalance(),
                bsqWalletService.getLockedForVotingBalance(),
                bsqWalletService.getLockupBondsBalance(),
                bsqWalletService.getUnlockingBondsBalance());

        if (daoStateService.isParseBlockChainComplete()) {
            addListenersAfterParseBlockChainComplete();

            updateListItems();
            applyMerit();
            updateViews();
        }
    }

    @Override
    protected void deactivate() {
        phasesView.deactivate();

        if (phaseSubscription != null)
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

        listItems.forEach(ProposalsListItem::cleanup);
        tableView.getSelectionModel().clearSelection();
        selectedItem = null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqBalanceListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onUpdateBalances(Coin availableBalance,
                                 Coin availableNonBsqBalance,
                                 Coin unverifiedBalance,
                                 Coin unconfirmedChangeBalance,
                                 Coin lockedForVotingBalance,
                                 Coin lockupBondsBalance,
                                 Coin unlockingBondsBalance) {
        Coin blindVoteFee = BlindVoteConsensus.getFee(daoStateService, daoStateService.getChainHeight());
        if (isBlindVotePhaseButNotLastBlock()) {
            Coin availableForVoting = availableBalance.subtract(blindVoteFee);
            if (availableForVoting.isNegative())
                availableForVoting = Coin.valueOf(0);
            stakeInputTextField.setPromptText(Res.get("dao.proposal.myVote.stake.prompt",
                    bsqFormatter.formatCoinWithCode(availableForVoting)));

            BsqValidator stakeInputTextFieldValidator = new BsqValidator(bsqFormatter);
            stakeInputTextFieldValidator.setMaxValue(availableForVoting);

            stakeInputTextField.setValidator(stakeInputTextFieldValidator);
        } else
            stakeInputTextField.setPromptText("");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        updateListItems();
        applyMerit();
    }

    @Override
    public void onParseBlockChainComplete() {
        addListenersAfterParseBlockChainComplete();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addListenersAfterParseBlockChainComplete() {
        daoFacade.getActiveOrMyUnconfirmedProposals().addListener(proposalListChangeListener);
        daoFacade.getAllBallots().addListener(ballotListChangeListener);

        bsqWalletService.addBsqBalanceListener(this);

        phaseSubscription = EasyBind.subscribe(daoFacade.phaseProperty(), this::onPhaseChanged);
    }

    private void updateListItems() {
        listItems.forEach(ProposalsListItem::cleanup);
        listItems.clear();

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

    private void showVoteOnProposalWindow(Proposal proposal, @Nullable Ballot ballot,
                                          @Nullable EvaluatedProposal evaluatedProposal) {
        if (!shownVoteOnProposalWindowForTxId.equals(proposal.getTxId())) {
            shownVoteOnProposalWindowForTxId = proposal.getTxId();

            selectProposalWindow.show(proposal, evaluatedProposal, ballot);
            selectProposalWindow.onAccept(() -> {
                shownVoteOnProposalWindowForTxId = "";
                onAccept();
            });
            selectProposalWindow.onReject(() -> {
                shownVoteOnProposalWindowForTxId = "";
                onReject();
            });
            selectProposalWindow.onIgnore(() -> {
                shownVoteOnProposalWindowForTxId = "";
                onIgnore();
            });
            selectProposalWindow.onRemove(() -> {
                shownVoteOnProposalWindowForTxId = "";
                onRemoveProposal();
            });

            selectProposalWindow.onClose(() -> {
                shownVoteOnProposalWindowForTxId = "";
                tableView.getSelectionModel().clearSelection();
            });
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onPhaseChanged(DaoPhase.Phase phase) {
        if (phase != null && !phase.equals(currentPhase)) {
            currentPhase = phase;
            stakeInputTextField.clear();
        }

        updateViews();
    }

    private void onRemoveProposal() {
        if (daoFacade.phaseProperty().get() == DaoPhase.Phase.PROPOSAL) {
            Proposal proposal = selectedItem.getProposal();
            new Popup().warning(Res.get("dao.proposal.active.remove.confirm"))
                    .actionButtonText(Res.get("dao.proposal.active.remove.doRemove"))
                    .onAction(() -> {
                        if (!daoFacade.removeMyProposal(proposal)) {
                            new Popup().warning(Res.get("dao.proposal.active.remove.failed")).show();
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

            applyMerit();
            showVoteOnProposalWindow(selectedItem.getProposal(), selectedItem.getBallot(), evaluatedProposal);
        }

        onPhaseChanged(daoFacade.phaseProperty().get());
    }

    private void applyMerit() {
        // We check if we have voted on that proposal. If so we use the merit used in the vote, otherwise we
        // use the merit based on all past issuance with the time decay applied.
        // The merit from the vote stays the same over blocks, the merit from daoFacade.getMeritAndStake()
        // decreases with every block a bit (over 2 years it goes to zero).
        Optional<MyVote> optionalMyVote = daoFacade.getMyVoteListForCycle().stream()
                .filter(myVote -> daoFacade.getTx(myVote.getBlindVoteTxId()).isPresent())
                .findAny();
        boolean hasConfirmedMyVoteInCycle = optionalMyVote.isPresent();
        long merit;
        if (selectedItem != null && hasConfirmedMyVoteInCycle) {
            merit = daoFacade.getMeritAndStakeForProposal(selectedItem.getProposal().getTxId()).first;
        } else if (selectedItem == null && hasConfirmedMyVoteInCycle) {
            merit = optionalMyVote.get().getMerit(myBlindVoteListService, daoStateService);
        } else {
            merit = daoFacade.getAvailableMerit();
        }

        meritTextField.setText(bsqFormatter.formatCoinWithCode(Coin.valueOf(merit)));
    }

    private void onAccept() {
        onVoteOnSingleProposal(new Vote(true));
    }

    private void onReject() {
        onVoteOnSingleProposal(new Vote(false));
    }

    private void onIgnore() {
        onVoteOnSingleProposal(null);
    }

    private void onVoteOnSingleProposal(Vote vote) {
        if (selectedItem != null) {
            daoFacade.setVote(selectedItem.getBallot(), vote);
            updateStateAfterVote();
            showHowToSetStakeForVotingPopup();
        }

        tableView.getSelectionModel().clearSelection();
    }

    private void showHowToSetStakeForVotingPopup() {
        String id = "explainHowToSetStakeForVoting";
        if (preferences.showAgain(id))
            new Popup().information(Res.get("dao.proposal.myVote.setStake.description"))
                    .dontShowAgainId(id).show();
    }

    private void onVote() {
        Coin stake = ParsingUtils.parseToCoin(stakeInputTextField.getText(), bsqFormatter);
        try {
            // We create a dummy tx to get the miningFee for displaying it at the confirmation popup
            Tuple2<Coin, Integer> miningFeeAndTxVsize = daoFacade.getBlindVoteMiningFeeAndTxVsize(stake);
            Coin miningFee = miningFeeAndTxVsize.first;
            int txVsize = miningFeeAndTxVsize.second;
            Coin blindVoteFee = daoFacade.getBlindVoteFeeForCycle();
            if (!DevEnv.isDevMode()) {
                GUIUtil.showBsqFeeInfoPopup(blindVoteFee, miningFee, txVsize, bsqFormatter, btcFormatter,
                        Res.get("dao.blindVote"), () -> publishBlindVote(stake));
            } else {
                publishBlindVote(stake);
            }
        } catch (InsufficientMoneyException | WalletException | TransactionVerificationException exception) {
            new Popup().warning(exception.toString()).show();
        }
    }

    private void publishBlindVote(Coin stake) {
        //TODO Starting voteButtonBusyAnimation here does not make sense if we stop it immediately below.
        // Check if voteButtonBusyAnimation should stay running until we hear back from publishing and only disable
        // button so that the user cannot click twice.
        voteButtonBusyAnimation.play();
        voteButtonInfoLabel.setText(Res.get("dao.blindVote.startPublishing"));
        daoFacade.publishBlindVote(stake,
                () -> {
                    if (!DevEnv.isDevMode())
                        new Popup().feedback(Res.get("dao.blindVote.success")).show();
                }, exception -> {
                    voteButtonBusyAnimation.stop();
                    voteButtonInfoLabel.setText("");
                    updateViews();
                    new Popup().warning(exception.toString()).show();
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

    private void updateViews() {
        boolean isBlindVotePhaseButNotLastBlock = isBlindVotePhaseButNotLastBlock();
        boolean hasVotedOnProposal = hasVotedOnProposal();
        voteButton.setDisable(!hasVotedOnProposal ||
                !stakeInputTextField.getValidator().validate(stakeInputTextField.getText()).isValid);

        List<MyVote> myVoteListForCycle = daoFacade.getMyVoteListForCycle();
        boolean hasAlreadyVoted = !myVoteListForCycle.isEmpty();
        if (selectedItem != null) {
            stakeInputTextField.setMouseTransparent(hasAlreadyVoted || !isBlindVotePhaseButNotLastBlock);
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
            if (myVoteListForCycle.size() == 1) {
                Optional<MyVote> optionalMyVote = myVoteListForCycle.stream()
                        .filter(myVote -> daoFacade.isTxInCorrectCycle(myVote.getHeight(), daoFacade.getChainHeight()))
                        .findAny();
                if (optionalMyVote.isPresent()) {
                    MyVote myVote = optionalMyVote.get();
                    Coin stake = Coin.valueOf(myVote.getBlindVote().getStake());
                    stakeInputTextField.setValidator(new InputValidator());
                    stakeInputTextField.setText(bsqFormatter.formatCoinWithCode(stake));

                    if (myVote.getBlindVoteTxId() != null) {
                        blindVoteTxIdTextField.setup(myVote.getBlindVoteTxId());
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
                    new Popup().warning(msg).dontShowAgainId(id).show();
            }
            voteButton.setVisible(false);
            voteButton.setManaged(false);
        }

        switch (daoFacade.phaseProperty().get()) {
            case PROPOSAL:
                // We have a bug in removing a proposal which is not trivial to fix (p2p network layer). Until that bug is fixed
                // it is better to not show the remove button as it confused users and a removed proposal will reappear with a
                // high probability at the vote phase.
                //lastColumn.setText(Res.get("dao.proposal.table.header.remove"));
                lastColumn.setText("");
                break;
            case BLIND_VOTE:
                lastColumn.setText(Res.get("dao.proposal.table.header.myVote"));
                break;
            default:
                lastColumn.setText("");
                break;
        }

        if (selectedItem == null &&
                listItems.size() > 0 &&
                selectProposalWindow.isDisplayed() &&
                !shownVoteOnProposalWindowForTxId.equals("")) {
            Proposal proposal = selectProposalWindow.getProposal();

            Optional<ProposalsListItem> proposalsListItem = listItems.stream()
                    .filter(item -> item.getProposal().equals(proposal))
                    .findAny();

            selectProposalWindow.onHide(() -> proposalsListItem.ifPresent(
                    listItem -> tableView.getSelectionModel().select(listItem)));

            shownVoteOnProposalWindowForTxId = "";
            selectProposalWindow.hide();
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

    private void updateTableHeight(double height) {
        double newTableViewHeight = proposalTableViewHeight.apply(height);
        if (tableView.getHeight() != newTableViewHeight) {
            tableView.setMinHeight(newTableViewHeight);
            double diff = newTableViewHeight - tableView.getHeight();
            proposalsHeadline.setMaxHeight(proposalsHeadline.getHeight() + diff);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Create views
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createProposalsTableView() {
        proposalsHeadline = new TableGroupHeadline(Res.get("dao.proposal.active.header"));
        GridPane.setRowIndex(proposalsHeadline, ++gridRow);
        GridPane.setMargin(proposalsHeadline, new Insets(Layout.GROUP_DISTANCE, -10, -10, -10));
        root.getChildren().add(proposalsHeadline);

        tableView = new TableView<>();
        tableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noData")));
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        createProposalColumns();
        GridPane.setRowIndex(tableView, gridRow);
        GridPane.setHgrow(tableView, Priority.ALWAYS);
        GridPane.setVgrow(tableView, Priority.SOMETIMES);
        GridPane.setMargin(tableView, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE, -10, 5, -10));
        root.getChildren().add(tableView);

        tableView.setItems(sortedList);
    }

    private void createVoteView() {
        TitledGroupBg voteTitledGroupBg = addTitledGroupBg(root, ++gridRow, 4,
                Res.get("dao.proposal.votes.header"), 20);
        voteTitledGroupBg.getStyleClass().add("last");
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
                Res.get("shared.blindVoteTxId"), 0);
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
        voteFields.add(voteButtonTuple.fourth);
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
                                    setText(DisplayUtils.formatDateTime(item.getProposal().getCreationDateAsDate()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(o3 -> o3.getProposal().getCreationDateAsDate()));
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
                                    Proposal proposal = item.getProposal();
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
        column.setComparator(Comparator.comparing(o -> o.getProposal().getLink()));
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
                                    setText(item.getProposalTypeAsString());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        column.setComparator(Comparator.comparing(ProposalsListItem::getProposalTypeAsString));
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
                                ProposalsListItem.IconButtonType iconButtonType = (ProposalsListItem.IconButtonType) iconButton.getUserData();
                                iconButton.setOnAction(e -> {
                                    selectedItem = item;
                                    if (areVoteButtonsVisible) {
                                        if (iconButtonType == ProposalsListItem.IconButtonType.ACCEPT)
                                            onReject();
                                        else if (iconButtonType == ProposalsListItem.IconButtonType.REJECT)
                                            onIgnore();
                                        else if (iconButtonType == ProposalsListItem.IconButtonType.IGNORE)
                                            onAccept();
                                    } else {
                                        if (iconButtonType == ProposalsListItem.IconButtonType.REMOVE_PROPOSAL)
                                            onRemoveProposal();
                                    }
                                });

                                if (!areVoteButtonsVisible && iconButtonType != ProposalsListItem.IconButtonType.REMOVE_PROPOSAL) {
                                    iconButton.setMouseTransparent(true);
                                    iconButton.setStyle("-fx-cursor: default;");
                                }

                                // We have a bug in removing a proposal which is not trivial to fix (p2p network layer).
                                // Until that bug is fixed
                                // it is better to not show the remove button as it confused users and a removed proposal will reappear with a
                                // high probability at the vote phase. The following lines can be removed once the bug is fixed.
                                boolean showIcon = iconButtonType != null &&
                                        iconButtonType != ProposalsListItem.IconButtonType.REMOVE_PROPOSAL;
                                iconButton.setVisible(showIcon);
                                iconButton.setManaged(showIcon);

                                setGraphic(iconButton);
                            } else {
                                setGraphic(null);
                            }
                        } else {
                            setGraphic(null);
                        }
                    }
                };
            }
        });
        column.setComparator(Comparator.comparing(item -> ((ProposalsListItem.IconButtonType) item.getIconButton().getUserData()).getTitle()));
        tableView.getColumns().add(column);
        lastColumn = column;
    }
}
