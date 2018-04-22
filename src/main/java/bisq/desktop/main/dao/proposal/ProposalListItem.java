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

package bisq.desktop.main.dao.proposal;

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.indicator.TxConfidenceIndicator;
import bisq.desktop.util.BsqFormatter;

import bisq.core.btc.listeners.TxConfidenceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.consensus.ballot.Ballot;
import bisq.core.dao.consensus.period.PeriodService;
import bisq.core.dao.consensus.period.Phase;
import bisq.core.dao.consensus.state.Block;
import bisq.core.dao.consensus.state.BlockListener;
import bisq.core.dao.consensus.state.StateService;
import bisq.core.dao.consensus.state.blockchain.Tx;
import bisq.core.dao.consensus.vote.BooleanVote;
import bisq.core.dao.consensus.vote.Vote;
import bisq.core.dao.consensus.ballot.MyBallotListService;
import bisq.core.locale.Res;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;

import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;

import javafx.beans.value.ChangeListener;

import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@ToString
@Slf4j
@EqualsAndHashCode
public class ProposalListItem implements BlockListener {
    @Getter
    private final Ballot ballot;
    private final MyBallotListService myBallotListService;
    private final PeriodService PeriodService;
    private final BsqWalletService bsqWalletService;
    private final StateService stateService;
    private final BsqFormatter bsqFormatter;
    private final ChangeListener<Number> chainHeightListener;
    private final ChangeListener<Vote> voteResultChangeListener;
    @Getter
    private TxConfidenceIndicator txConfidenceIndicator;
    @Getter
    private Integer confirmations = 0;

    private TxConfidenceListener txConfidenceListener;
    private Tooltip tooltip = new Tooltip(Res.get("confidence.unknown"));
    private Transaction walletTransaction;
    private ChangeListener<Phase> phaseChangeListener;
    private AutoTooltipButton actionButton;
    private ImageView actionButtonIconView;
    @Setter
    private Runnable onRemoveHandler;
    private Node actionNode;

    ProposalListItem(Ballot ballot,
                     MyBallotListService myBallotListService,
                     PeriodService PeriodService,
                     BsqWalletService bsqWalletService,
                     StateService stateService,
                     BsqFormatter bsqFormatter) {
        this.ballot = ballot;
        this.myBallotListService = myBallotListService;
        this.PeriodService = PeriodService;
        this.bsqWalletService = bsqWalletService;
        this.stateService = stateService;
        this.bsqFormatter = bsqFormatter;


        txConfidenceIndicator = new TxConfidenceIndicator();
        txConfidenceIndicator.setId("funds-confidence");

        txConfidenceIndicator.setProgress(-1);
        txConfidenceIndicator.setPrefSize(24, 24);
        txConfidenceIndicator.setTooltip(tooltip);

        actionButton = new AutoTooltipButton();
        actionButton.setMinWidth(70);
        actionButtonIconView = new ImageView();

        chainHeightListener = (observable, oldValue, newValue) -> setupConfidence();
        bsqWalletService.getChainHeightProperty().addListener(chainHeightListener);
        setupConfidence();

        stateService.addBlockListener(this);

        phaseChangeListener = (observable, oldValue, newValue) -> {
            applyState(newValue, ballot.getVote());
        };

        voteResultChangeListener = (observable, oldValue, newValue) -> {
            applyState(PeriodService.phaseProperty().get(), newValue);
        };

        PeriodService.phaseProperty().addListener(phaseChangeListener);
        ballot.getVoteResultProperty().addListener(voteResultChangeListener);
    }

    public void applyState(Phase phase, Vote vote) {
        if (phase != null) {
            actionButton.setText("");
            actionButton.setVisible(false);
            actionButton.setOnAction(null);
            final boolean isTxInPastCycle = PeriodService.isTxInPastCycle(ballot.getTxId(),
                    stateService.getChainHeight());
            switch (phase) {
                case UNDEFINED:
                    log.error("invalid state UNDEFINED");
                    break;
                case PROPOSAL:
                    if (myBallotListService.isMine(ballot.getProposal())) {
                        actionButton.setVisible(!isTxInPastCycle);
                        actionButtonIconView.setVisible(actionButton.isVisible());
                        actionButton.setText(Res.get("shared.remove"));
                        actionButton.setGraphic(actionButtonIconView);
                        actionButtonIconView.setId("image-remove");
                        actionButton.setOnAction(e -> {
                            if (onRemoveHandler != null)
                                onRemoveHandler.run();
                        });
                        actionNode = actionButton;
                    }
                    break;
                case BREAK1:
                    break;
                case BLIND_VOTE:
                    if (!isTxInPastCycle) {
                        actionNode = actionButtonIconView;
                        actionButton.setVisible(false);
                        if (ballot.getVote() != null) {
                            actionButtonIconView.setVisible(true);
                            if (vote instanceof BooleanVote) {
                                if (((BooleanVote) vote).isAccepted()) {
                                    actionButtonIconView.setId("accepted");
                                } else {
                                    actionButtonIconView.setId("rejected");
                                }
                            } else {
                                //TODO
                            }
                        } else {
                            actionButtonIconView.setVisible(false);
                        }
                    }
                    break;
                case BREAK2:
                    break;
                case VOTE_REVEAL:
                    break;
                case BREAK3:
                    break;
                case VOTE_RESULT:
                    break;
                case BREAK4:
                    break;
                default:
                    log.error("invalid state " + phase);
            }
            actionButton.setManaged(actionButton.isVisible());

            // Don't set managed as otherwise the update does not work (not sure why but probably table
            // cell item issue)
            //actionButtonIconView.setManaged(actionButtonIconView.isVisible());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onBlockAdded(Block block) {
        //TODO do we want that here???
        setupConfidence();
    }


    private void setupConfidence() {
        final Tx tx = stateService.getTxMap().get(ballot.getProposal().getTxId());
        if (tx != null) {
            final String txId = tx.getId();

            // We cache the walletTransaction once found
            if (walletTransaction == null) {
                final Optional<Transaction> transactionOptional = bsqWalletService.isWalletTransaction(txId);
                transactionOptional.ifPresent(transaction -> walletTransaction = transaction);
            }

            if (walletTransaction != null) {
                // It is our tx so we get confidence updates
                if (txConfidenceListener == null) {
                    txConfidenceListener = new TxConfidenceListener(txId) {
                        @Override
                        public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
                            updateConfidence(confidence.getConfidenceType(), confidence.getDepthInBlocks(), confidence.numBroadcastPeers());
                        }
                    };
                    bsqWalletService.addTxConfidenceListener(txConfidenceListener);
                }
            } else {
                // tx from other users, we dont get confidence updates but as we have the bsq tx we can calculate it
                // we get setupConfidence called at each new block from above listener so no need to register a new listener
                int depth = bsqWalletService.getChainHeightProperty().get() - tx.getBlockHeight() + 1;
                if (depth > 0)
                    updateConfidence(TransactionConfidence.ConfidenceType.BUILDING, depth, -1);
                //log.error("name={}, id ={}, depth={}", compensationRequest.getPayload().getName(), compensationRequest.getPayload().getUid(), depth);
            }

            final TransactionConfidence confidence = bsqWalletService.getConfidenceForTxId(txId);
            if (confidence != null)
                updateConfidence(confidence, confidence.getDepthInBlocks());
        }
    }

    private void updateConfidence(TransactionConfidence confidence, int depthInBlocks) {
        if (confidence != null) {
            updateConfidence(confidence.getConfidenceType(), confidence.getDepthInBlocks(), confidence.numBroadcastPeers());
            confirmations = depthInBlocks;
        }
    }

    public void cleanup() {
        stateService.removeBlockListener(this);
        bsqWalletService.getChainHeightProperty().removeListener(chainHeightListener);
        if (txConfidenceListener != null)
            bsqWalletService.removeTxConfidenceListener(txConfidenceListener);

        PeriodService.phaseProperty().removeListener(phaseChangeListener);
        ballot.getVoteResultProperty().removeListener(voteResultChangeListener);
    }

    private void updateConfidence(TransactionConfidence.ConfidenceType confidenceType, int depthInBlocks, int numBroadcastPeers) {
        switch (confidenceType) {
            case UNKNOWN:
                tooltip.setText(Res.get("confidence.unknown"));
                txConfidenceIndicator.setProgress(0);
                break;
            case PENDING:
                tooltip.setText(Res.get("confidence.seen", numBroadcastPeers > -1 ? numBroadcastPeers : Res.get("shared.na")));
                txConfidenceIndicator.setProgress(-1.0);
                break;
            case BUILDING:
                tooltip.setText(Res.get("confidence.confirmed", depthInBlocks));
                txConfidenceIndicator.setProgress(Math.min(1, (double) depthInBlocks / 6.0));
                break;
            case DEAD:
                tooltip.setText(Res.get("confidence.invalid"));
                txConfidenceIndicator.setProgress(0);
                break;
        }

        txConfidenceIndicator.setPrefSize(24, 24);
    }

    public Node getActionNode() {
        return actionNode;
    }
}

