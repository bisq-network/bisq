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

package io.bisq.gui.main.dao.compensation;

import io.bisq.common.locale.Res;
import io.bisq.core.btc.listeners.TxConfidenceListener;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.dao.blockchain.BsqBlockChainChangeDispatcher;
import io.bisq.core.dao.blockchain.BsqBlockChainListener;
import io.bisq.core.dao.blockchain.parse.BsqBlockChain;
import io.bisq.core.dao.blockchain.vo.Tx;
import io.bisq.core.dao.compensation.CompensationRequest;
import io.bisq.gui.components.indicator.TxConfidenceIndicator;
import io.bisq.gui.util.BsqFormatter;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Tooltip;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;

import java.util.Optional;


@ToString
@Slf4j
@EqualsAndHashCode
public class CompensationRequestListItem implements BsqBlockChainListener {
    @Getter
    private final CompensationRequest compensationRequest;
    private final BsqWalletService bsqWalletService;
    private final BsqBlockChain bsqBlockChain;
    private final BsqBlockChainChangeDispatcher bsqBlockChainChangeDispatcher;
    private final BsqFormatter bsqFormatter;
    private final ChangeListener<Number> chainHeightListener;
    @Getter
    private TxConfidenceIndicator txConfidenceIndicator;
    @Getter
    private Integer confirmations = 0;

    private TxConfidenceListener txConfidenceListener;
    private Tooltip tooltip = new Tooltip(Res.get("confidence.unknown"));
    private Transaction walletTransaction;

    public CompensationRequestListItem(CompensationRequest compensationRequest,
                                       BsqWalletService bsqWalletService,
                                       BsqBlockChain bsqBlockChain,
                                       BsqBlockChainChangeDispatcher bsqBlockChainChangeDispatcher,
                                       BsqFormatter bsqFormatter) {
        this.compensationRequest = compensationRequest;
        this.bsqWalletService = bsqWalletService;
        this.bsqBlockChain = bsqBlockChain;
        this.bsqBlockChainChangeDispatcher = bsqBlockChainChangeDispatcher;
        this.bsqFormatter = bsqFormatter;


        txConfidenceIndicator = new TxConfidenceIndicator();
        txConfidenceIndicator.setId("funds-confidence");

        txConfidenceIndicator.setProgress(-1);
        txConfidenceIndicator.setPrefSize(24, 24);
        txConfidenceIndicator.setTooltip(tooltip);

        chainHeightListener = (observable, oldValue, newValue) -> setupConfidence();
        bsqWalletService.getChainHeightProperty().addListener(chainHeightListener);
        setupConfidence();

        bsqBlockChainChangeDispatcher.addBsqBlockChainListener(this);
    }

    @Override
    public void onBsqBlockChainChanged() {
        setupConfidence();
    }

    private void setupConfidence() {
        final Tx tx = bsqBlockChain.getTxMap().get(compensationRequest.getPayload().getTxId());
        if (tx != null) {
            final String txId = tx.getId();

            // We cache the walletTransaction once found
            if (walletTransaction == null) {
                final Optional<Transaction> transactionOptional = bsqWalletService.isWalletTransaction(txId);
                if (transactionOptional.isPresent())
                    walletTransaction = transactionOptional.get();
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
                log.error("name={}, id ={}, depth={}", compensationRequest.getPayload().getName(), compensationRequest.getPayload().getUid(), depth);
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
        bsqBlockChainChangeDispatcher.removeBsqBlockChainListener(this);
        bsqWalletService.getChainHeightProperty().removeListener(chainHeightListener);
        if (txConfidenceListener != null)
            bsqWalletService.removeTxConfidenceListener(txConfidenceListener);
    }

    public void updateConfidence(TransactionConfidence.ConfidenceType confidenceType, int depthInBlocks, int numBroadcastPeers) {
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

