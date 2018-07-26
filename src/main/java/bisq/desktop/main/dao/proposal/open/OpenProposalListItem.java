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

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.indicator.TxConfidenceIndicator;

import bisq.core.btc.listeners.TxConfidenceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.BsqStateListener;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.voting.ballot.Ballot;
import bisq.core.dao.voting.ballot.vote.BooleanVote;
import bisq.core.dao.voting.ballot.vote.Vote;
import bisq.core.dao.voting.proposal.Proposal;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;

import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;

import javafx.beans.value.ChangeListener;

import java.util.Objects;
import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@ToString
@Slf4j
@EqualsAndHashCode
public class OpenProposalListItem implements BsqStateListener {
    @Getter
    private final Proposal proposal;
    private final DaoFacade daoFacade;
    private final BsqWalletService bsqWalletService;
    private final BsqFormatter bsqFormatter;

    @Getter
    @Nullable
    private Ballot ballot;

    @Getter
    private ImageView imageView;
    @Getter
    private AutoTooltipButton button;

    @Getter
    private TxConfidenceIndicator txConfidenceIndicator;
    @Getter
    private Integer confirmations = 0;
    private TxConfidenceListener txConfidenceListener;
    private Tooltip tooltip = new Tooltip(Res.get("confidence.unknown"));
    private Transaction walletTransaction;
    private ChangeListener<DaoPhase.Phase> phaseChangeListener;
    private ChangeListener<Number> chainHeightListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    OpenProposalListItem(Proposal proposal,
                         DaoFacade daoFacade,
                         BsqWalletService bsqWalletService,
                         BsqFormatter bsqFormatter) {
        this.proposal = proposal;
        this.daoFacade = daoFacade;
        this.bsqWalletService = bsqWalletService;
        this.bsqFormatter = bsqFormatter;

        init();
    }

    OpenProposalListItem(Ballot ballot,
                         DaoFacade daoFacade,
                         BsqWalletService bsqWalletService,
                         BsqFormatter bsqFormatter) {
        this.ballot = ballot;
        this.proposal = ballot.getProposal();
        this.daoFacade = daoFacade;
        this.bsqWalletService = bsqWalletService;
        this.bsqFormatter = bsqFormatter;

        init();
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
        setupConfidence();
    }

    @Override
    public void onParseBlockChainComplete() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void cleanup() {
        daoFacade.removeBsqStateListener(this);
        bsqWalletService.getChainHeightProperty().removeListener(chainHeightListener);
        if (txConfidenceListener != null)
            bsqWalletService.removeTxConfidenceListener(txConfidenceListener);

        daoFacade.phaseProperty().removeListener(phaseChangeListener);
    }

    public void onPhaseChanged(DaoPhase.Phase phase) {
        //noinspection IfCanBeSwitch
        if (phase == DaoPhase.Phase.PROPOSAL) {
            imageView.setId("image-remove");
            button.setGraphic(imageView);
            button.setText(Res.get("shared.remove"));
            final boolean isMyProposal = daoFacade.isMyProposal(proposal);
            button.setVisible(isMyProposal);
            button.setManaged(isMyProposal);
        } else if (phase == DaoPhase.Phase.BLIND_VOTE) {
            button.setGraphic(null);
            button.setText(Res.get("dao.proposal.active.vote"));
            button.setVisible(true);
            button.setManaged(true);
        } else {
            button.setVisible(false);
            button.setManaged(false);
        }


        // ballot
        if (ballot != null) {
            final Vote vote = ballot.getVote();
            if (vote != null) {
                imageView.setVisible(true);
                if (vote instanceof BooleanVote) {
                    if (((BooleanVote) vote).isAccepted()) {
                        imageView.setId("accepted");
                    } else {
                        imageView.setId("rejected");
                    }
                }/* else {
                // not impl.
            }*/
            } else {
                imageView.setVisible(false);
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void init() {
        txConfidenceIndicator = new TxConfidenceIndicator();
        txConfidenceIndicator.setId("funds-confidence");

        txConfidenceIndicator.setProgress(-1);
        txConfidenceIndicator.setPrefSize(24, 24);
        txConfidenceIndicator.setTooltip(tooltip);


        imageView = new ImageView();

        chainHeightListener = (observable, oldValue, newValue) -> setupConfidence();
        bsqWalletService.getChainHeightProperty().addListener(chainHeightListener);
        setupConfidence();

        daoFacade.addBsqStateListener(this);

        phaseChangeListener = (observable, oldValue, newValue) -> onPhaseChanged(newValue);

        daoFacade.phaseProperty().addListener(phaseChangeListener);

        button = new AutoTooltipButton();
        button.setMinWidth(70);
        onPhaseChanged(daoFacade.phaseProperty().get());
    }

    private void setupConfidence() {
        final String txId = proposal.getTxId();
        Optional<Tx> optionalTx = daoFacade.getTx(txId);
        optionalTx.ifPresent(tx -> {
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
                // tx from other users, we don't get confidence updates but as we have the bsq tx we can calculate it
                // we get setupConfidence called at each new block from above listener so no need to register a new listener
                int depth = bsqWalletService.getChainHeightProperty().get() - tx.getBlockHeight() + 1;
                if (depth > 0)
                    updateConfidence(TransactionConfidence.ConfidenceType.BUILDING, depth, -1);
            }

            final TransactionConfidence confidence = bsqWalletService.getConfidenceForTxId(txId);
            if (confidence != null)
                updateConfidence(confidence, confidence.getDepthInBlocks());
        });
    }

    private void updateConfidence(TransactionConfidence confidence, int depthInBlocks) {
        if (confidence != null) {
            updateConfidence(confidence.getConfidenceType(), confidence.getDepthInBlocks(), confidence.numBroadcastPeers());
            confirmations = depthInBlocks;
        }
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

    public Optional<BooleanVote> getBooleanVote() {
        return Optional.ofNullable(ballot)
                .map(Ballot::getVote)
                .filter(Objects::nonNull)
                .filter(vote -> vote instanceof BooleanVote)
                .map(v -> (BooleanVote) v);
    }
}
