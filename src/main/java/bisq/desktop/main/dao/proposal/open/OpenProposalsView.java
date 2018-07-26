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

package bisq.desktop.main.dao.proposal.open;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.TableGroupHeadline;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.dao.proposal.ProposalDisplay;
import bisq.desktop.main.dao.proposal.ProposalWindow;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.BsqValidator;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqBalanceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.BsqStateListener;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.voting.ballot.Ballot;
import bisq.core.dao.voting.ballot.vote.BooleanVote;
import bisq.core.dao.voting.myvote.MyVote;
import bisq.core.dao.voting.proposal.Proposal;
import bisq.core.locale.Res;
import bisq.core.util.BSFormatter;
import bisq.core.util.BsqFormatter;

import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

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
import java.util.stream.Collectors;

import static bisq.desktop.util.FormBuilder.*;

@FxmlView
public class OpenProposalsView extends ActivatableView<GridPane, Void> implements BsqBalanceListener, BsqStateListener {

    private final DaoFacade daoFacade;
    private final BsqWalletService bsqWalletService;
    private final BsqFormatter bsqFormatter;
    private final BSFormatter btcFormatter;

    private final ObservableList<OpenProposalListItem> listItems = FXCollections.observableArrayList();
    private final SortedList<OpenProposalListItem> sortedList = new SortedList<>(listItems);
    private TableView<OpenProposalListItem> tableView;
    private Subscription selectedProposalSubscription;
    private ProposalDisplay proposalDisplay;
    private int gridRow = 0;
    private OpenProposalListItem selectedItem;
    private GridPane proposalDisplayGridPane;
    private DaoPhase.Phase currentPhase;
    private Subscription phaseSubscription;
    private ScrollPane proposalDisplayView;
    private boolean proposalDisplayInitialized;


    private Button removeProposalButton, acceptButton, rejectButton, ignoreButton, voteButton;
    private InputTextField stakeInputTextField;
    private BusyAnimation voteButtonBusyAnimation;
    private Label voteButtonInfoLabel;
    private ListChangeListener<Proposal> proposalListChangeListener;
    private ListChangeListener<Ballot> ballotListChangeListener;
    private ChangeListener<String> stakeListener;
    private List<Control> voteControls = new ArrayList<>();
    private TitledGroupBg voteTitledGroupBg;
    private Label stakeLabel;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private OpenProposalsView(DaoFacade daoFacade,
                              BsqWalletService bsqWalletService,
                              BsqFormatter bsqFormatter,
                              BSFormatter btcFormatter) {

        this.daoFacade = daoFacade;
        this.bsqWalletService = bsqWalletService;
        this.bsqFormatter = bsqFormatter;
        this.btcFormatter = btcFormatter;
    }

    @Override
    public void initialize() {
        super.initialize();
        root.getStyleClass().add("vote-root");

        proposalDisplayGridPane = new GridPane();

        createProposalsTableView();
        createEmptyProposalDisplay();
        createVoteView();

        ballotListChangeListener = c -> updateListItems();
        proposalListChangeListener = c -> updateListItems();

        // ballot
        stakeListener = (observable, oldValue, newValue) -> updateButtonStates();
    }

    @Override
    protected void activate() {
        phaseSubscription = EasyBind.subscribe(daoFacade.phaseProperty(), this::onPhaseChanged);
        selectedProposalSubscription = EasyBind.subscribe(tableView.getSelectionModel().selectedItemProperty(), this::onSelectProposal);

        sortedList.comparatorProperty().bind(tableView.comparatorProperty());

        daoFacade.getActiveOrMyUnconfirmedProposals().addListener(proposalListChangeListener);
        daoFacade.getValidAndConfirmedBallots().addListener(ballotListChangeListener);

        stakeInputTextField.textProperty().addListener(stakeListener);
        bsqWalletService.addBsqBalanceListener(this);

        onUpdateBalances(bsqWalletService.getAvailableBalance(),
                bsqWalletService.getAvailableNonBsqBalance(),
                bsqWalletService.getUnverifiedBalance(),
                bsqWalletService.getLockedForVotingBalance(),
                bsqWalletService.getLockupBondsBalance(),
                bsqWalletService.getUnlockingBondsBalance());

        voteButton.setOnAction(e -> onVote());

        daoFacade.addBsqStateListener(this);

        updateListItems();
        updateButtonStates();
        stakeInputTextField.setText("");
    }

    @Override
    protected void deactivate() {
        phaseSubscription.unsubscribe();
        selectedProposalSubscription.unsubscribe();

        sortedList.comparatorProperty().unbind();

        listItems.forEach(OpenProposalListItem::cleanup);
        tableView.getSelectionModel().clearSelection();
        selectedItem = null;

        daoFacade.getActiveOrMyUnconfirmedProposals().removeListener(proposalListChangeListener);
        daoFacade.getValidAndConfirmedBallots().removeListener(ballotListChangeListener);

        stakeInputTextField.textProperty().removeListener(stakeListener);
        bsqWalletService.removeBsqBalanceListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqBalanceListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onUpdateBalances(Coin confirmedBalance,
                                 Coin availableNonBsqBalance,
                                 Coin pendingBalance,
                                 Coin lockedForVotingBalance,
                                 Coin lockupBondsBalance,
                                 Coin unlockingBondsBalance) {
        if (isBlindVotePhase())
            stakeInputTextField.setPromptText(Res.get("dao.proposal.myVote.stake.prompt",
                    bsqFormatter.formatCoinWithCode(confirmedBalance)));
        else
            stakeInputTextField.setPromptText("");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onNewBlockHeight(int blockHeight) {
    }

    @Override
    public void onEmptyBlockAdded(Block block) {
    }

    @Override
    public void onParseTxsComplete(Block block) {
        stakeInputTextField.setText("");
        updateButtonStates();
    }

    @Override
    public void onParseBlockChainComplete() {
        GUIUtil.setFitToRowsForTableView(tableView, 33, 28, 80);

        // hack to get layout correct
        if (!tableView.getItems().isEmpty()) {
            onSelectProposal(tableView.getItems().get(0));
            onSelectProposal(null);
        }

        updateListItems();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void fillListItems() {
        if (daoFacade.phaseProperty().get().ordinal() < DaoPhase.Phase.BLIND_VOTE.ordinal()) {
            // proposal phase
            List<Proposal> list = daoFacade.getActiveOrMyUnconfirmedProposals();
            listItems.setAll(list.stream()
                    .map(proposal -> new OpenProposalListItem(proposal, daoFacade, bsqWalletService, bsqFormatter))
                    .collect(Collectors.toSet()));
        } else {
            // blind vote phase
            List<Ballot> ballotList = daoFacade.getValidAndConfirmedBallots();
            listItems.setAll(ballotList.stream()
                    .map(ballot -> new OpenProposalListItem(ballot, daoFacade, bsqWalletService, bsqFormatter))
                    .collect(Collectors.toSet()));
        }

        updateButtonStates();
    }

    private void updateListItems() {
        listItems.forEach(OpenProposalListItem::cleanup);
        listItems.clear();

        fillListItems();

        if (listItems.isEmpty())
            hideProposalDisplay();
    }

    private void createAllFieldsOnProposalDisplay(Proposal proposal) {
        proposalDisplayView.setVisible(true);
        proposalDisplayView.setManaged(true);

        proposalDisplay.createAllFields(Res.get("dao.proposal.selectedProposal"), 0, 0, proposal.getType(),
                false);
        proposalDisplay.setEditable(false);
        proposalDisplay.applyProposalPayload(proposal);
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
        ignoreButton.setOnAction(event -> onCancelVote());

        voteControls.clear();
        voteControls.add(voteButton);
        voteControls.add(acceptButton);
        voteControls.add(rejectButton);
        voteControls.add(ignoreButton);
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
            onSelectProposal(selectedItem);
        }

        if (removeProposalButton != null) {
            //noinspection IfCanBeSwitch,IfCanBeSwitch,IfCanBeSwitch
            if (phase == DaoPhase.Phase.PROPOSAL) {
                if (selectedItem != null && selectedItem.getProposal() != null) {
                    final boolean isMyProposal = daoFacade.isMyProposal(selectedItem.getProposal());
                    removeProposalButton.setVisible(isMyProposal);
                    removeProposalButton.setManaged(isMyProposal);
                }
            } else {
                removeProposalButton.setVisible(false);
                removeProposalButton.setManaged(false);
            }
        }

        updateButtonStates();

        boolean isBlindVotePhaseOrLater = daoFacade.phaseProperty().get().ordinal() >= DaoPhase.Phase.BLIND_VOTE.ordinal();
        voteTitledGroupBg.setVisible(isBlindVotePhaseOrLater);
        voteTitledGroupBg.setManaged(isBlindVotePhaseOrLater);
        stakeLabel.setVisible(isBlindVotePhaseOrLater);
        stakeLabel.setManaged(isBlindVotePhaseOrLater);
        stakeInputTextField.setVisible(isBlindVotePhaseOrLater);
        stakeInputTextField.setManaged(isBlindVotePhaseOrLater);
    }

    private void onRemoveProposal() {
        if (daoFacade.phaseProperty().get() == DaoPhase.Phase.PROPOSAL) {
            final Proposal proposal = selectedItem.getProposal();
            if (daoFacade.removeMyProposal(proposal)) {
                hideProposalDisplay();
            } else {
                new Popup<>().warning(Res.get("dao.proposal.active.remove.failed")).show();
            }
            tableView.getSelectionModel().clearSelection();
        }
    }

    private void onSelectProposal(OpenProposalListItem item) {
        selectedItem = item;
        if (selectedItem != null)
            createAllFieldsOnProposalDisplay(selectedItem.getProposal());
        else
            hideProposalDisplay();

        onPhaseChanged(daoFacade.phaseProperty().get());

        updateButtonStates();
    }

    private void onAccept() {
        daoFacade.setVote(getBallotListItem().getBallot(), new BooleanVote(true));
        updateStateAfterVote();
    }

    private void onReject() {
        daoFacade.setVote(getBallotListItem().getBallot(), new BooleanVote(false));
        updateStateAfterVote();
    }

    private void onCancelVote() {
        daoFacade.setVote(getBallotListItem().getBallot(), null);
        updateStateAfterVote();
    }

    private void onVote() {
        // TODO verify stake
        Coin stake = bsqFormatter.parseToCoin(stakeInputTextField.getText());
        final Coin blindVoteFee = daoFacade.getBlindVoteFeeForCycle();
        Transaction dummyTx = null;
        try {
            // We create a dummy tx to get the mining blindVoteFee for confirmation popup
            dummyTx = daoFacade.getDummyBlindVoteTx(stake, blindVoteFee);
        } catch (InsufficientMoneyException | WalletException | TransactionVerificationException exception) {
            new Popup<>().warning(exception.toString()).show();
        }

        if (dummyTx != null) {
            Coin miningFee = dummyTx.getFee();
            int txSize = dummyTx.bitcoinSerialize().length;
            GUIUtil.showBsqFeeInfoPopup(blindVoteFee, miningFee, txSize, bsqFormatter, btcFormatter,
                    Res.get("dao.blindVote"), () -> publishBlindVote(stake));
        }
    }

    private void publishBlindVote(Coin stake) {
        voteButtonBusyAnimation.play();
        voteButtonInfoLabel.setText(Res.get("dao.blindVote.startPublishing"));
        daoFacade.publishBlindVote(stake,
                () -> {
                    voteButtonBusyAnimation.stop();
                    voteButtonInfoLabel.setText("");
                    new Popup().feedback(Res.get("dao.blindVote.success"))
                            .show();
                }, exception -> {
                    voteButtonBusyAnimation.stop();
                    voteButtonInfoLabel.setText("");
                    new Popup<>().warning(exception.toString()).show();
                });
    }


    private void updateStateAfterVote() {
        updateButtonStates();
        //hideProposalDisplay();
        //proposalTableView.getSelectionModel().clearSelection();
        tableView.refresh();
    }

    private OpenProposalListItem getBallotListItem() {
        return selectedItem;
    }

    private void updateButtonStates() {
        boolean isBlindVotePhase = isBlindVotePhase();
        voteControls.forEach(control -> {
            control.setVisible(isBlindVotePhase);
            control.setManaged(isBlindVotePhase);
        });

        boolean hasVoted = listItems.stream()
                .filter(e -> e.getBallot() != null)
                .map(OpenProposalListItem::getBallot)
                .anyMatch(e -> e.getVote() != null);
        voteButton.setDisable(!hasVoted ||
                !stakeInputTextField.getValidator().validate(stakeInputTextField.getText()).isValid);

        List<MyVote> myVoteListForCycle = daoFacade.getMyVoteListForCycle();
        boolean hasAlreadyVoted = !myVoteListForCycle.isEmpty();
        if (isBlindVotePhase && selectedItem != null && acceptButton != null) {
            Optional<BooleanVote> optionalVote = selectedItem.getBooleanVote();
            boolean isPresent = optionalVote.isPresent();
            boolean isAccepted = isPresent && optionalVote.get().isAccepted();
            boolean isBlindVotePhaseButNotLastBlock = isBlindVotePhaseButNotLastBlock();
            boolean doDisable = hasAlreadyVoted || !isBlindVotePhaseButNotLastBlock;
            acceptButton.setDisable(doDisable || (isPresent && isAccepted));
            rejectButton.setDisable(doDisable || (isPresent && !isAccepted));
            ignoreButton.setDisable(doDisable || !isPresent);
            stakeInputTextField.setDisable(doDisable);
        } else {
            stakeInputTextField.setDisable(true);
        }

        if (hasAlreadyVoted) {
            voteTitledGroupBg.setText(Res.get("dao.proposal.votes.header.voted"));
            if (myVoteListForCycle.size() == 1) {
                myVoteListForCycle.stream().findAny()
                        .ifPresent(myVote -> {
                            Coin stake = Coin.valueOf(myVote.getBlindVote().getStake());
                            stakeInputTextField.setText(bsqFormatter.formatCoinWithCode(stake));
                        });
            } else {
                String msg = "We found multiple MyVote entries in that cycle. That is not supported by the UI.";
                log.warn(msg);
                new Popup<>().error(msg).show();
            }
            voteButton.setVisible(false);
            voteButton.setManaged(false);

        }
    }

    private boolean isBlindVotePhase() {
        return daoFacade.phaseProperty().get() == DaoPhase.Phase.BLIND_VOTE;
    }

    private boolean isBlindVotePhaseButNotLastBlock() {
        int getLastBlockOfBlindVotePhase = daoFacade.getLastBlockOfPhase(daoFacade.getChainHeight(), DaoPhase.Phase.BLIND_VOTE);
        return isBlindVotePhase() && daoFacade.getChainHeight() < getLastBlockOfBlindVotePhase;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Create views
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createProposalsTableView() {
        TableGroupHeadline proposalsHeadline = new TableGroupHeadline(Res.get("dao.proposal.active.header"));
        GridPane.setRowIndex(proposalsHeadline, gridRow);
        GridPane.setMargin(proposalsHeadline, new Insets(0, -10, -10, -10));
        GridPane.setColumnSpan(proposalsHeadline, 2);
        root.getChildren().add(proposalsHeadline);

        tableView = new TableView<>();
        tableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noData")));
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setMinHeight(80);
        tableView.setMaxHeight(80);

        createProposalColumns();
        GridPane.setRowIndex(tableView, gridRow);
        GridPane.setHgrow(tableView, Priority.ALWAYS);
        GridPane.setMargin(tableView, new Insets(20, -10, 5, -10));
        GridPane.setColumnSpan(tableView, 2);
        root.getChildren().add(tableView);

        tableView.setItems(sortedList);
    }

    private void createEmptyProposalDisplay() {
        proposalDisplay = new ProposalDisplay(proposalDisplayGridPane, bsqFormatter, bsqWalletService, daoFacade);
        proposalDisplayView = proposalDisplay.getView();
        GridPane.setMargin(proposalDisplayView, new Insets(0, -10, 0, -10));
        GridPane.setRowIndex(proposalDisplayView, ++gridRow);
        GridPane.setColumnSpan(proposalDisplayView, 2);
        GridPane.setHgrow(proposalDisplayView, Priority.ALWAYS);
        root.getChildren().add(proposalDisplayView);
    }

    private void createVoteView() {
        voteTitledGroupBg = addTitledGroupBg(root, ++gridRow, 1,
                Res.get("dao.proposal.votes.header"), Layout.GROUP_DISTANCE - 20);
        Tuple2<Label, InputTextField> tuple2 = addLabelInputTextField(root, gridRow,
                Res.getWithCol("dao.proposal.myVote.stake"), Layout.FIRST_ROW_AND_GROUP_DISTANCE - 20);
        stakeLabel = tuple2.first;
        stakeInputTextField = tuple2.second;
        stakeInputTextField.setValidator(new BsqValidator(bsqFormatter));

        Tuple3<Button, BusyAnimation, Label> tuple = addButtonBusyAnimationLabelAfterGroup(root, ++gridRow,
                Res.get("dao.proposal.myVote.button"));
        voteButton = tuple.first;
        voteControls.add(voteButton);
        voteButtonBusyAnimation = tuple.second;
        voteButtonInfoLabel = tuple.third;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TableColumns
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createProposalColumns() {
        TableColumn<OpenProposalListItem, OpenProposalListItem> dateColumn = new AutoTooltipTableColumn<OpenProposalListItem, OpenProposalListItem>(Res.get("shared.dateTime"));
        dateColumn.setMinWidth(190);
        dateColumn.setMinWidth(190);

        dateColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        dateColumn.setCellFactory(
                new Callback<TableColumn<OpenProposalListItem, OpenProposalListItem>, TableCell<OpenProposalListItem,
                        OpenProposalListItem>>() {
                    @Override
                    public TableCell<OpenProposalListItem, OpenProposalListItem> call(
                            TableColumn<OpenProposalListItem, OpenProposalListItem> column) {
                        return new TableCell<OpenProposalListItem, OpenProposalListItem>() {
                            @Override
                            public void updateItem(final OpenProposalListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(bsqFormatter.formatDateTime(item.getProposal().getCreationDate()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        dateColumn.setComparator(Comparator.comparing(o3 -> o3.getProposal().getCreationDate()));
        dateColumn.setSortType(TableColumn.SortType.DESCENDING);
        tableView.getColumns().add(dateColumn);
        tableView.getSortOrder().add(dateColumn);

        TableColumn<OpenProposalListItem, OpenProposalListItem> nameColumn = new AutoTooltipTableColumn<>(Res.get("shared.name"));
        nameColumn.setMinWidth(60);
        nameColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        nameColumn.setCellFactory(
                new Callback<TableColumn<OpenProposalListItem, OpenProposalListItem>, TableCell<OpenProposalListItem,
                        OpenProposalListItem>>() {
                    @Override
                    public TableCell<OpenProposalListItem, OpenProposalListItem> call(
                            TableColumn<OpenProposalListItem, OpenProposalListItem> column) {
                        return new TableCell<OpenProposalListItem, OpenProposalListItem>() {
                            @Override
                            public void updateItem(final OpenProposalListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null)
                                    setText(item.getProposal().getName());
                                else
                                    setText("");
                            }
                        };
                    }
                });
        nameColumn.setComparator(Comparator.comparing(o2 -> o2.getProposal().getName()));
        tableView.getColumns().add(nameColumn);

        TableColumn<OpenProposalListItem, OpenProposalListItem> uidColumn = new AutoTooltipTableColumn<>(Res.get("shared.id"));
        uidColumn.setMinWidth(60);
        uidColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        uidColumn.setCellFactory(
                new Callback<TableColumn<OpenProposalListItem, OpenProposalListItem>, TableCell<OpenProposalListItem,
                        OpenProposalListItem>>() {

                    @Override
                    public TableCell<OpenProposalListItem, OpenProposalListItem> call(TableColumn<OpenProposalListItem,
                            OpenProposalListItem> column) {
                        return new TableCell<OpenProposalListItem, OpenProposalListItem>() {
                            private HyperlinkWithIcon field;

                            @Override
                            public void updateItem(final OpenProposalListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    final Proposal proposal = item.getProposal();
                                    field = new HyperlinkWithIcon(proposal.getShortId());
                                    field.setOnAction(event -> {
                                        new ProposalWindow(bsqFormatter, bsqWalletService, proposal, daoFacade).show();
                                    });
                                    field.setTooltip(new Tooltip(Res.get("tooltip.openPopupForDetails")));
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
        uidColumn.setComparator(Comparator.comparing(o -> o.getProposal().getUid()));
        tableView.getColumns().add(uidColumn);


        TableColumn<OpenProposalListItem, OpenProposalListItem> confidenceColumn = new TableColumn<>(Res.get("shared.confirmations"));
        confidenceColumn.setMinWidth(130);
        confidenceColumn.setMaxWidth(confidenceColumn.getMinWidth());
        confidenceColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        confidenceColumn.setCellFactory(new Callback<TableColumn<OpenProposalListItem, OpenProposalListItem>,
                TableCell<OpenProposalListItem, OpenProposalListItem>>() {

            @Override
            public TableCell<OpenProposalListItem, OpenProposalListItem> call(TableColumn<OpenProposalListItem,
                    OpenProposalListItem> column) {
                return new TableCell<OpenProposalListItem, OpenProposalListItem>() {

                    @Override
                    public void updateItem(final OpenProposalListItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {
                            setGraphic(item.getTxConfidenceIndicator());
                        } else {
                            setGraphic(null);
                        }
                    }
                };
            }
        });
        confidenceColumn.setComparator(Comparator.comparing(OpenProposalListItem::getConfirmations));
        tableView.getColumns().add(confidenceColumn);

        TableColumn<OpenProposalListItem, OpenProposalListItem> actionColumn = new TableColumn<>();
        actionColumn.setMinWidth(130);
        actionColumn.setMaxWidth(actionColumn.getMinWidth());
        actionColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));
        actionColumn.setCellFactory(new Callback<TableColumn<OpenProposalListItem, OpenProposalListItem>,
                TableCell<OpenProposalListItem, OpenProposalListItem>>() {

            @Override
            public TableCell<OpenProposalListItem, OpenProposalListItem> call(TableColumn<OpenProposalListItem,
                    OpenProposalListItem> column) {
                return new TableCell<OpenProposalListItem, OpenProposalListItem>() {
                    ImageView imageView;

                    @Override
                    public void updateItem(final OpenProposalListItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {
                            if (imageView == null) {
                                imageView = item.getImageView();
                                setGraphic(imageView);
                            }
                            item.onPhaseChanged(currentPhase);
                        } else {
                            setGraphic(null);
                            if (imageView != null)
                                imageView = null;
                        }
                    }
                };
            }
        });
        tableView.getColumns().add(actionColumn);
    }
}
