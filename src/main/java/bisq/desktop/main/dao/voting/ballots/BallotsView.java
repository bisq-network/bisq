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

package bisq.desktop.main.dao.voting.ballots;

import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.dao.ItemsView;
import bisq.desktop.main.dao.ListItem;
import bisq.desktop.main.dao.voting.BallotListItem;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.BSFormatter;
import bisq.desktop.util.BsqFormatter;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqBalanceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.voting.ballot.Ballot;
import bisq.core.locale.Res;

import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import javafx.scene.control.Button;
import javafx.scene.control.Label;

import javafx.collections.ListChangeListener;

import java.util.List;
import java.util.stream.Collectors;

import static bisq.desktop.util.FormBuilder.add3ButtonsAfterGroup;
import static bisq.desktop.util.FormBuilder.addButtonBusyAnimationLabelAfterGroup;
import static bisq.desktop.util.FormBuilder.addLabelInputTextField;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class BallotsView extends ItemsView implements BsqBalanceListener {
    protected Button acceptButton, rejectButton, removeMyVoteButton, voteButton;
    protected InputTextField stakeInputTextField;
    protected BusyAnimation voteButtonBusyAnimation;
    protected Label voteButtonInfoLabel;
    protected ListChangeListener<Ballot> listChangeListener;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private BallotsView(DaoFacade daoFacade,
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
                bsqWalletService.getPendingBalance(),
                bsqWalletService.getLockedForVotingBalance(),
                bsqWalletService.getLockedInBondsBalance());

        voteButton.setOnAction(e -> onVote());
    }


    @Override
    protected void deactivate() {
        super.deactivate();

        daoFacade.getActiveOrMyUnconfirmedBallots().removeListener(listChangeListener);
        bsqWalletService.removeBsqBalanceListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onUpdateBalances(Coin confirmedBalance,
                                 Coin pendingBalance,
                                 Coin lockedForVotingBalance,
                                 Coin lockedInBondsBalance) {
        stakeInputTextField.setPromptText(Res.get("dao.proposal.myVote.stake.prompt",
                bsqFormatter.formatCoinWithCode(confirmedBalance)));
    }


    @Override
    protected void onPhaseChanged(DaoPhase.Phase phase) {
        super.onPhaseChanged(phase);

        if (selectedListItem != null && proposalDisplay != null) {
            if (phase == DaoPhase.Phase.BLIND_VOTE) {
                if (acceptButton == null) {
                    Tuple3<Button, Button, Button> tuple = add3ButtonsAfterGroup(detailsGridPane, proposalDisplay
                                    .incrementAndGetGridRow(),
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
                } else {
                    //TODO prob. not possible code path
                    acceptButton.setManaged(true);
                    acceptButton.setVisible(true);
                    rejectButton.setManaged(true);
                    rejectButton.setVisible(true);
                    removeMyVoteButton.setManaged(true);
                    removeMyVoteButton.setVisible(true);
                }
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void onAccept() {
        //TODO
        // daoFacade.setVote(selectedProposalListItem.getProposal(), new BooleanVote(true));
        updateStateAfterVote();
    }


    protected void onReject() {
        //TODO
        // daoFacade.setVote(selectedProposalListItem.getProposal(), new BooleanVote(false));
        updateStateAfterVote();
    }

    protected void onCancelVote() {
        //TODO
        // daoFacade.setVote(selectedProposalListItem.getProposal(), null);
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

    @Override
    protected void fillListItems() {
        List<Ballot> list = daoFacade.getActiveOrMyUnconfirmedBallots();
        proposalListItems.setAll(list.stream()
                .map(ballot -> new BallotListItem(ballot, daoFacade, bsqWalletService, bsqFormatter))
                .collect(Collectors.toSet()));
    }

    protected void publishBlindVote(Coin stake) {
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

    protected void onSelectProposal(ListItem item) {
        super.onSelectProposal(item);
        if (item != null) {
            if (acceptButton != null) {
                acceptButton.setManaged(false);
                acceptButton.setVisible(false);
                acceptButton = null;
            }
            if (rejectButton != null) {
                rejectButton.setManaged(false);
                rejectButton.setVisible(false);
                rejectButton = null;
            }
            if (removeMyVoteButton != null) {
                removeMyVoteButton.setManaged(false);
                removeMyVoteButton.setVisible(false);
                removeMyVoteButton = null;
            }

            onPhaseChanged(daoFacade.phaseProperty().get());
        }
    }

    protected void updateStateAfterVote() {
        hideProposalDisplay();
        proposalTableView.getSelectionModel().clearSelection();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Create views
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void createVoteView() {
        TitledGroupBg titledGroupBg = addTitledGroupBg(root, ++gridRow, 1, Res.get("dao.proposal.votes.header"),
                Layout.GROUP_DISTANCE - 20);
        final Tuple2<Label, InputTextField> tuple2 = addLabelInputTextField(root, gridRow,
                Res.getWithCol("dao.proposal.myVote.stake"), Layout
                        .FIRST_ROW_AND_GROUP_DISTANCE - 20);
        stakeInputTextField = tuple2.second;
        Tuple3<Button, BusyAnimation, Label> tuple = addButtonBusyAnimationLabelAfterGroup(root, ++gridRow, Res.get("dao.proposal.myVote.button"));
        voteButton = tuple.first;
        voteButtonBusyAnimation = tuple.second;
        voteButtonInfoLabel = tuple.third;
    }
}
