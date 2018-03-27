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

package bisq.desktop.main.dao.proposal.active;

import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.dao.proposal.BaseProposalView;
import bisq.desktop.main.dao.proposal.ProposalListItem;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.BsqFormatter;
import bisq.desktop.util.Layout;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqBalanceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.ChangeBelowDustException;
import bisq.core.btc.wallet.InsufficientBsqException;
import bisq.core.dao.DaoPeriodService;
import bisq.core.dao.blockchain.BsqBlockChain;
import bisq.core.dao.blockchain.BsqBlockChainChangeDispatcher;
import bisq.core.dao.proposal.Proposal;
import bisq.core.dao.proposal.ProposalCollectionsService;
import bisq.core.dao.vote.BooleanVoteResult;
import bisq.core.dao.vote.VoteService;
import bisq.core.locale.Res;

import bisq.common.crypto.CryptoException;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;

import com.google.protobuf.InvalidProtocolBufferException;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import com.google.common.util.concurrent.FutureCallback;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import javafx.beans.property.ReadOnlyObjectWrapper;

import javafx.util.Callback;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static bisq.desktop.util.FormBuilder.add3ButtonsAfterGroup;
import static bisq.desktop.util.FormBuilder.addButtonAfterGroup;
import static bisq.desktop.util.FormBuilder.addLabelInputTextField;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class ActiveProposalsView extends BaseProposalView implements BsqBalanceListener {

    private final VoteService voteService;

    private Button removeButton, acceptButton, rejectButton, cancelVoteButton, voteButton;
    private InputTextField stakeInputTextField;
    private List<Node> voteViewItems = new ArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ActiveProposalsView(ProposalCollectionsService voteRequestManger,
                                DaoPeriodService daoPeriodService,
                                VoteService voteService,
                                BsqWalletService bsqWalletService,
                                BsqBlockChain bsqBlockChain,
                                BsqBlockChainChangeDispatcher bsqBlockChainChangeDispatcher,
                                BsqFormatter bsqFormatter) {
        super(voteRequestManger, bsqWalletService, bsqBlockChain, bsqBlockChainChangeDispatcher, daoPeriodService,
                bsqFormatter);
        this.voteService = voteService;
    }

    @Override
    public void initialize() {
        super.initialize();

        createProposalsTableView();
        createVoteView();
        createProposalDisplay();
    }


    @Override
    protected void activate() {
        super.activate();

        bsqWalletService.addBsqBalanceListener(this);

        onUpdateBalances(bsqWalletService.getAvailableBalance(),
                bsqWalletService.getPendingBalance(),
                bsqWalletService.getLockedForVotingBalance(),
                bsqWalletService.getLockedInBondsBalance());

        if (voteButton != null) {
            voteButton.setOnAction(e -> {
                Coin stake = bsqFormatter.parseToCoin(stakeInputTextField.getText());
                // TODO verify stake
                //TODO show popup
                try {
                    voteService.publishBlindVote(stake, new FutureCallback<Transaction>() {
                        @Override
                        public void onSuccess(@Nullable Transaction result) {
                            //TODO
                        }

                        @Override
                        public void onFailure(@NotNull Throwable t) {
                            //TODO
                        }
                    });
                } catch (CryptoException e1) {
                    //TODO show error popup
                    e1.printStackTrace();
                } catch (InsufficientBsqException e1) {
                    e1.printStackTrace();
                } catch (WalletException e1) {
                    e1.printStackTrace();
                } catch (TransactionVerificationException e1) {
                    e1.printStackTrace();
                } catch (InsufficientMoneyException e1) {
                    e1.printStackTrace();
                } catch (ChangeBelowDustException e1) {
                    e1.printStackTrace();
                } catch (InvalidProtocolBufferException e1) {
                    e1.printStackTrace();
                }
            });
        }
    }

    @Override
    protected void deactivate() {
        super.deactivate();

        bsqWalletService.removeBsqBalanceListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Create views
    ///////////////////////////////////////////////////////////////////////////////////////////


    private void createVoteView() {
        TitledGroupBg titledGroupBg = addTitledGroupBg(root, ++gridRow, 1, Res.get("dao.proposal.votes.header"),
                Layout.GROUP_DISTANCE - 20);
        final Tuple2<Label, InputTextField> tuple2 = addLabelInputTextField(root, gridRow,
                Res.getWithCol("dao.proposal.myVote.stake"), Layout
                        .FIRST_ROW_AND_GROUP_DISTANCE - 20);
        stakeInputTextField = tuple2.second;
        voteButton = addButtonAfterGroup(root, ++gridRow, Res.get("dao.proposal.myVote.button"));

        voteViewItems.add(titledGroupBg);
        voteViewItems.add(tuple2.first);
        voteViewItems.add(stakeInputTextField);
        voteViewItems.add(voteButton);

        changeVoteViewItemsVisibility(false);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onUpdateBalances(Coin confirmedBalance,
                                 Coin pendingBalance,
                                 Coin lockedForVotingBalance,
                                 Coin lockedInBondsBalance) {
        stakeInputTextField.setPromptText(Res.get("dao.proposal.myVote.stake.prompt",
                bsqFormatter.formatCoinWithCode(confirmedBalance)));
    }

    protected void onSelectProposal(ProposalListItem item) {
        super.onSelectProposal(item);
        if (item != null) {
            if (removeButton != null) {
                removeButton.setManaged(false);
                removeButton.setVisible(false);
                removeButton = null;
            }
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
            if (cancelVoteButton != null) {
                cancelVoteButton.setManaged(false);
                cancelVoteButton.setVisible(false);
                cancelVoteButton = null;
            }

            onPhaseChanged(daoPeriodService.getPhaseProperty().get());
        }
    }

    private void onAccept() {
        selectedProposalListItem.getProposal().setVoteResult(new BooleanVoteResult(true));
        updateStateAfterVote();
    }

    private void onReject() {
        selectedProposalListItem.getProposal().setVoteResult(new BooleanVoteResult(false));
        updateStateAfterVote();
    }

    private void onCancelVote() {
        selectedProposalListItem.getProposal().setVoteResult(null);
        updateStateAfterVote();
    }

    @Override
    protected void onPhaseChanged(DaoPeriodService.Phase phase) {
        super.onPhaseChanged(phase);

        changeVoteViewItemsVisibility(phase == DaoPeriodService.Phase.BLIND_VOTE);

        if (removeButton != null) {
            removeButton.setManaged(false);
            removeButton.setVisible(false);
            removeButton = null;
        }
        if (selectedProposalListItem != null &&
                proposalDisplay != null &&
                !daoPeriodService.isTxInPastCycle(selectedProposalListItem.getProposal().getTxId())) {
            final Proposal proposal = selectedProposalListItem.getProposal();
            switch (phase) {
                case PROPOSAL:
                    if (proposalCollectionsService.isMine(proposal)) {
                        if (removeButton == null) {
                            removeButton = addButtonAfterGroup(detailsGridPane, proposalDisplay.incrementAndGetGridRow(), Res.get("dao.proposal.active.remove"));
                            removeButton.setOnAction(event -> onRemove());
                        } else {
                            removeButton.setManaged(true);
                            removeButton.setVisible(true);
                        }
                    }
                    break;
                case BREAK1:
                    break;
                case BLIND_VOTE:
                    if (acceptButton == null) {
                        Tuple3<Button, Button, Button> tuple = add3ButtonsAfterGroup(detailsGridPane, proposalDisplay
                                        .incrementAndGetGridRow(),
                                Res.get("dao.proposal.myVote.accept"),
                                Res.get("dao.proposal.myVote.reject"),
                                Res.get("dao.proposal.myVote.cancelVote"));
                        acceptButton = tuple.first;
                        acceptButton.setDefaultButton(false);
                        rejectButton = tuple.second;
                        cancelVoteButton = tuple.third;
                        acceptButton.setOnAction(event -> onAccept());
                        rejectButton.setOnAction(event -> onReject());
                        cancelVoteButton.setOnAction(event -> onCancelVote());
                    } else {
                        acceptButton.setManaged(true);
                        acceptButton.setVisible(true);
                        rejectButton.setManaged(true);
                        rejectButton.setVisible(true);
                        cancelVoteButton.setManaged(true);
                        cancelVoteButton.setVisible(true);
                    }
                    break;
                case BREAK2:
                    break;
                case VOTE_REVEAL:
                    break;
                case BREAK3:
                    break;
                case ISSUANCE:
                    break;
                case BREAK4:
                    break;
                case UNDEFINED:
                default:
                    log.warn("Undefined phase: " + phase);
                    break;
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void updateProposalList() {
        doUpdateProposalList(proposalCollectionsService.getActiveProposals());
    }

    private void updateStateAfterVote() {
        hideProposalDisplay();
        proposalCollectionsService.persist();
        proposalTableView.getSelectionModel().clearSelection();
    }

    private void changeVoteViewItemsVisibility(boolean value) {
        voteViewItems.forEach(node -> {
            node.setVisible(value);
            node.setManaged(value);
        });
    }

    private void onRemove() {
        if (proposalCollectionsService.removeProposal(selectedProposalListItem.getProposal()))
            hideProposalDisplay();
        else
            new Popup<>().warning(Res.get("dao.proposal.active.remove.failed")).show();

        proposalTableView.getSelectionModel().clearSelection();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TableColumns
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void createProposalColumns(TableView<ProposalListItem> tableView) {
        super.createProposalColumns(tableView);
        createConfidenceColumn(tableView);

        TableColumn<ProposalListItem, ProposalListItem> actionColumn = new TableColumn<>();
        actionColumn.setMinWidth(130);
        actionColumn.setMaxWidth(actionColumn.getMinWidth());

        actionColumn.setCellValueFactory((item) -> new ReadOnlyObjectWrapper<>(item.getValue()));

        actionColumn.setCellFactory(new Callback<TableColumn<ProposalListItem, ProposalListItem>,
                TableCell<ProposalListItem, ProposalListItem>>() {

            @Override
            public TableCell<ProposalListItem, ProposalListItem> call(TableColumn<ProposalListItem,
                    ProposalListItem> column) {
                return new TableCell<ProposalListItem, ProposalListItem>() {
                    Node node;

                    @Override
                    public void updateItem(final ProposalListItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {
                            if (node == null) {
                                node = item.getActionNode();
                                setGraphic(node);
                                item.setOnRemoveHandler(() -> {
                                    ActiveProposalsView.this.selectedProposalListItem = item;
                                    ActiveProposalsView.this.onRemove();
                                });
                                item.applyState(currentPhase, item.getProposal().getVoteResultProperty().get());
                            }
                        } else {
                            setGraphic(null);
                            if (node != null) {
                                if (node instanceof Button)
                                    ((Button) node).setOnAction(null);
                                node = null;
                            }
                        }
                    }
                };
            }
        });
        actionColumn.setComparator(Comparator.comparing(ProposalListItem::getConfirmations));
        tableView.getColumns().add(actionColumn);
    }
}

