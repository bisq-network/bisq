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

package bisq.desktop.main.dao.proposal.myvotes;

import bisq.desktop.components.indicator.TxConfidenceIndicator;

import bisq.core.btc.listeners.TxConfidenceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.blockchain.BsqBlockChain;
import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.blockchain.vo.BsqBlock;
import bisq.core.dao.blockchain.vo.Tx;
import bisq.core.dao.blockchain.vo.TxOutput;
import bisq.core.dao.vote.myvote.MyVote;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;

import javafx.scene.control.Tooltip;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
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
public class VoteListItem implements BsqBlockChain.Listener {
    @Getter
    private final MyVote myVote;
    private final BsqWalletService bsqWalletService;
    private final ReadableBsqBlockChain readableBsqBlockChain;
    private final BsqFormatter bsqFormatter;
    private final ChangeListener<Number> chainHeightListener;
    @Getter
    private TxConfidenceIndicator txConfidenceIndicator;
    @Getter
    private Integer confirmations = 0;

    private TxConfidenceListener txConfidenceListener;
    private Tooltip tooltip = new Tooltip(Res.get("confidence.unknown"));
    private Transaction walletTransaction;
    @Setter
    private Runnable onRemoveHandler;
    @Getter
    private long stake = 0;
    @Getter
    private StringProperty stakeAsStringProperty = new SimpleStringProperty("");

    VoteListItem(MyVote myVote,
                 BsqWalletService bsqWalletService,
                 ReadableBsqBlockChain readableBsqBlockChain,
                 BsqFormatter bsqFormatter) {
        this.myVote = myVote;
        this.bsqWalletService = bsqWalletService;
        this.readableBsqBlockChain = readableBsqBlockChain;
        this.bsqFormatter = bsqFormatter;

        txConfidenceIndicator = new TxConfidenceIndicator();
        txConfidenceIndicator.setId("funds-confidence");

        txConfidenceIndicator.setProgress(-1);
        txConfidenceIndicator.setPrefSize(24, 24);
        txConfidenceIndicator.setTooltip(tooltip);
        readableBsqBlockChain.addListener(this);

        chainHeightListener = (observable, oldValue, newValue) -> setupConfidence();
        bsqWalletService.getChainHeightProperty().addListener(chainHeightListener);
        setupConfidence();
        calculateStake();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqBlockChain.Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onBlockAdded(BsqBlock bsqBlock) {
        //TODO do we want that here???
        setupConfidence();
    }


    private void setupConfidence() {
        calculateStake();
        final Tx tx = readableBsqBlockChain.getTxMap().get(myVote.getBlindVote().getTxId());
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

    private void calculateStake() {
        if (stake == 0) {
            String txId = myVote.getTxId();
            stake = readableBsqBlockChain.getBlindVoteStakeTxOutputs().stream()
                    .filter(txOutput -> txOutput.getTxId().equals(txId))
                    .filter(txOutput -> txOutput.getIndex() == 0)
                    .mapToLong(TxOutput::getValue)
                    .sum();
            stakeAsStringProperty.set(bsqFormatter.formatCoin(Coin.valueOf(stake)));
        }
    }

    private void updateConfidence(TransactionConfidence confidence, int depthInBlocks) {
        if (confidence != null) {
            updateConfidence(confidence.getConfidenceType(), confidence.getDepthInBlocks(), confidence.numBroadcastPeers());
            confirmations = depthInBlocks;
        }
    }

    public void cleanup() {
        bsqWalletService.getChainHeightProperty().removeListener(chainHeightListener);
        readableBsqBlockChain.removeListener(this);
        if (txConfidenceListener != null)
            bsqWalletService.removeTxConfidenceListener(txConfidenceListener);
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
}

