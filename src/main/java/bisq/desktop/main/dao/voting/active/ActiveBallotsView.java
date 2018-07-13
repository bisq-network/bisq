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

package bisq.desktop.main.dao.voting.active;

import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.components.InputTextField;
import bisq.desktop.main.dao.BaseProposalListItem;
import bisq.desktop.main.dao.BaseProposalView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqBalanceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.BlockListener;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.voting.ballot.Ballot;
import bisq.core.dao.voting.ballot.vote.BooleanVote;
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
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.ImageView;

import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.collections.ListChangeListener;

import javafx.util.Callback;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static bisq.desktop.util.FormBuilder.add3ButtonsAfterGroup;
import static bisq.desktop.util.FormBuilder.addButtonBusyAnimationLabelAfterGroup;
import static bisq.desktop.util.FormBuilder.addLabelInputTextField;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class ActiveBallotsView extends BaseProposalView implements BsqBalanceListener, BlockListener {
    private Button acceptButton, rejectButton, removeMyVoteButton, voteButton;
    private InputTextField stakeInputTextField;
    private BusyAnimation voteButtonBusyAnimation;
    private Label voteButtonInfoLabel;
    private ListChangeListener<Ballot> listChangeListener;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ActiveBallotsView(DaoFacade daoFacade,
                              BsqWalletService bsqWalletService,
                              BsqFormatter bsqFormatter,
                              BSFormatter btcFormatter) {

        super(daoFacade, bsqWalletService, bsqFormatter, btcFormatter);
    }

    @Override
    public void initialize() {
        super.initialize();

        createProposalsTableView();
        createVoteView();
        createEmptyProposalDisplay();

        listChangeListener = c -> updateListItems();
    }

    @Override
    protected void activate() {
        super.activate();

        bsqWalletService.addBsqBalanceListener(this);
        daoFacade.getActiveOrMyUnconfirmedBallots().addListener(listChangeListener);

        onUpdateBalances(bsqWalletService.getAvailableBalance(),
                bsqWalletService.getAvailableNonBsqBalance(),
                bsqWalletService.getUnverifiedBalance(),
                bsqWalletService.getLockedForVotingBalance(),
                bsqWalletService.getLockupBondsBalance(),
                bsqWalletService.getUnlockingBondsBalance());

        voteButton.setOnAction(e -> onVote());

        daoFacade.addBlockListener(this);

        updateButtons();
    }


    @Override
    protected void deactivate() {
        super.deactivate();

        daoFacade.getActiveOrMyUnconfirmedBallots().removeListener(listChangeListener);
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
        stakeInputTextField.setPromptText(Res.get("dao.proposal.myVote.stake.prompt",
                bsqFormatter.formatCoinWithCode(confirmedBalance)));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // BlockListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onBlockAdded(Block block) {
        updateButtons();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void fillListItems() {
        List<Ballot> list = daoFacade.getActiveOrMyUnconfirmedBallots();
        proposalBaseProposalListItems.setAll(list.stream()
                .map(ballot -> new ActiveBallotListItem(ballot, daoFacade, bsqWalletService, bsqFormatter))
                .collect(Collectors.toSet()));
        updateButtons();
    }

    @Override
    protected void createAllFieldsOnProposalDisplay(Proposal proposal) {
        super.createAllFieldsOnProposalDisplay(proposal);

        Tuple3<Button, Button, Button> tuple = add3ButtonsAfterGroup(detailsGridPane,
                proposalDisplay.incrementAndGetGridRow(),
                Res.get("dao.proposal.myVote.accept"),
                Res.get("dao.proposal.myVote.reject"),
                Res.get("dao.proposal.myVote.removeMyVote"));
        acceptButton = tuple.first;
        acceptButton.setDefaultButton(false);
        rejectButton = tuple.second;
        removeMyVoteButton = tuple.third;
        acceptButton.setOnAction(event -> onAccept());
        rejectButton.setOnAction(event -> onReject());
        removeMyVoteButton.setOnAction(event -> onCancelVote());
    }

    @Override
    protected void hideProposalDisplay() {
        super.hideProposalDisplay();

        if (acceptButton != null) {
            acceptButton.setManaged(false);
            acceptButton.setVisible(false);
        }
        if (rejectButton != null) {
            rejectButton.setManaged(false);
            rejectButton.setVisible(false);
        }
        if (removeMyVoteButton != null) {
            removeMyVoteButton.setManaged(false);
            removeMyVoteButton.setVisible(false);
        }
    }

    @Override
    protected void onPhaseChanged(DaoPhase.Phase phase) {
        super.onPhaseChanged(phase);

        updateButtons();
    }

    @Override
    protected void onSelectProposal(BaseProposalListItem item) {
        super.onSelectProposal(item);

        updateButtons();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

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
        hideProposalDisplay();
        proposalTableView.getSelectionModel().clearSelection();
        proposalTableView.refresh();
    }

    private ActiveBallotListItem getBallotListItem() {
        return (ActiveBallotListItem) selectedBaseProposalListItem;
    }

    private void updateButtons() {
        final boolean isBlindVotePhase = daoFacade.phaseProperty().get() == DaoPhase.Phase.BLIND_VOTE;
        stakeInputTextField.setDisable(!isBlindVotePhase);
        voteButton.setDisable(!isBlindVotePhase);

        if (acceptButton != null) acceptButton.setDisable(!isBlindVotePhase);
        if (rejectButton != null) rejectButton.setDisable(!isBlindVotePhase);
        if (removeMyVoteButton != null) removeMyVoteButton.setDisable(!isBlindVotePhase);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Create views
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createVoteView() {
        addTitledGroupBg(root, ++gridRow, 1,
                Res.get("dao.proposal.votes.header"), Layout.GROUP_DISTANCE - 20);
        final Tuple2<Label, InputTextField> tuple2 = addLabelInputTextField(root, gridRow,
                Res.getWithCol("dao.proposal.myVote.stake"), Layout.FIRST_ROW_AND_GROUP_DISTANCE - 20);
        stakeInputTextField = tuple2.second;
        Tuple3<Button, BusyAnimation, Label> tuple = addButtonBusyAnimationLabelAfterGroup(root, ++gridRow,
                Res.get("dao.proposal.myVote.button"));
        voteButton = tuple.first;
        voteButtonBusyAnimation = tuple.second;
        voteButtonInfoLabel = tuple.third;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TableColumns
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void createProposalColumns(TableView<BaseProposalListItem> tableView) {
        super.createProposalColumns(tableView);
        createConfidenceColumn(tableView);

        TableColumn<BaseProposalListItem, BaseProposalListItem> actionColumn = new TableColumn<>();
        actionColumn.setMinWidth(130);
        actionColumn.setMaxWidth(actionColumn.getMinWidth());

        actionColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));

        actionColumn.setCellFactory(new Callback<TableColumn<BaseProposalListItem, BaseProposalListItem>,
                TableCell<BaseProposalListItem, BaseProposalListItem>>() {

            @Override
            public TableCell<BaseProposalListItem, BaseProposalListItem> call(TableColumn<BaseProposalListItem,
                    BaseProposalListItem> column) {
                return new TableCell<BaseProposalListItem, BaseProposalListItem>() {
                    ImageView imageView;

                    @Override
                    public void updateItem(final BaseProposalListItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {
                            ActiveBallotListItem activeBallotListItem = (ActiveBallotListItem) item;
                            if (imageView == null) {
                                imageView = activeBallotListItem.getImageView();
                                setGraphic(imageView);
                            }
                            activeBallotListItem.onPhaseChanged(currentPhase);
                        } else {
                            setGraphic(null);
                            if (imageView != null)
                                imageView = null;
                        }
                    }
                };
            }
        });
        actionColumn.setComparator(Comparator.comparing(BaseProposalListItem::getConfirmations));
        tableView.getColumns().add(actionColumn);
    }
}
