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

package bisq.desktop.main.portfolio.bsqswaps;

import bisq.desktop.components.indicator.TxConfidenceIndicator;
import bisq.desktop.util.GUIUtil;

import bisq.core.btc.listeners.TxConfidenceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;

import org.bitcoinj.core.TransactionConfidence;

import javafx.scene.control.Tooltip;

import lombok.Getter;

import javax.annotation.Nullable;

class UnconfirmedBsqSwapsListItem {
    @Getter
    private final BsqSwapTrade bsqSwapTrade;
    private final BsqWalletService bsqWalletService;
    private final String txId;
    @Getter
    private int confirmations = 0;
    @Getter
    private TxConfidenceIndicator txConfidenceIndicator;
    private TxConfidenceListener txConfidenceListener;

    UnconfirmedBsqSwapsListItem(BsqWalletService bsqWalletService, BsqSwapTrade bsqSwapTrade) {
        this.bsqSwapTrade = bsqSwapTrade;
        this.bsqWalletService = bsqWalletService;

        txId = bsqSwapTrade.getTxId();
        txConfidenceIndicator = new TxConfidenceIndicator();
        txConfidenceIndicator.setId("funds-confidence");
        Tooltip tooltip = new Tooltip();
        txConfidenceIndicator.setProgress(0);
        txConfidenceIndicator.setPrefSize(24, 24);
        txConfidenceIndicator.setTooltip(tooltip);

        txConfidenceListener = new TxConfidenceListener(txId) {
            @Override
            public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
                updateConfidence(confidence, tooltip);
            }
        };
        bsqWalletService.addTxConfidenceListener(txConfidenceListener);
        updateConfidence(bsqWalletService.getConfidenceForTxId(txId), tooltip);
    }

    UnconfirmedBsqSwapsListItem() {
        bsqSwapTrade = null;
        bsqWalletService = null;
        txId = null;

    }

    private void updateConfidence(@Nullable TransactionConfidence confidence, Tooltip tooltip) {
        if (confidence != null) {
            GUIUtil.updateConfidence(confidence, tooltip, txConfidenceIndicator);
            confirmations = confidence.getDepthInBlocks();
        }
    }

    public void cleanup() {
        bsqWalletService.removeTxConfidenceListener(txConfidenceListener);
    }

}
