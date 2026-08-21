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

package bisq.desktop.components;

import bisq.desktop.components.indicator.TxConfidenceIndicator;
import bisq.desktop.util.GUIUtil;

import bisq.core.btc.listeners.TxConfidenceListener;
import bisq.core.btc.wallet.BsqWalletService;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;

import com.google.common.base.Suppliers;

import javafx.scene.control.Tooltip;

import java.util.function.Supplier;

import lombok.Getter;

public class TxConfidenceListItem {
    @Getter
    protected final BsqWalletService bsqWalletService;
    @Getter
    protected final String txId;
    private final TxConfidenceListener txConfidenceListener;
    private final Supplier<LazyFields> lazyFieldsSupplier;
    private volatile LazyFields lazyFields;

    private static class LazyFields {
        int confirmations;
        TxConfidenceIndicator txConfidenceIndicator;
        Tooltip tooltip;
    }

    private LazyFields lazy() {
        return lazyFieldsSupplier.get();
    }

    protected TxConfidenceListItem(Transaction transaction,
                                   BsqWalletService bsqWalletService) {
        this.bsqWalletService = bsqWalletService;

        txId = transaction.getTxId().toString();
        lazyFieldsSupplier = Suppliers.memoize(() -> new LazyFields() {{
            txConfidenceIndicator = new TxConfidenceIndicator();
            txConfidenceIndicator.setId("funds-confidence");
            tooltip = new Tooltip();
            txConfidenceIndicator.setProgress(0);
            txConfidenceIndicator.setPrefSize(24, 24);
            txConfidenceIndicator.setTooltip(tooltip);

            lazyFields = this;
            updateConfidence(bsqWalletService.getConfidenceForTxId(txId));
        }});

        txConfidenceListener = new TxConfidenceListener(txId) {
            @Override
            public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
                updateConfidence(confidence);
            }
        };
        bsqWalletService.addTxConfidenceListener(txConfidenceListener);
    }

    protected TxConfidenceListItem() {
        bsqWalletService = null;
        txId = null;
        txConfidenceListener = null;
        lazyFieldsSupplier = null;
    }

    public int getNumConfirmations() {
        return lazy().confirmations;
    }

    public TxConfidenceIndicator getTxConfidenceIndicator() {
        return lazy().txConfidenceIndicator;
    }

    private void updateConfidence(TransactionConfidence confidence) {
        if (confidence != null && lazyFields != null) {
            GUIUtil.updateConfidence(confidence, lazyFields.tooltip, lazyFields.txConfidenceIndicator);
            lazyFields.confirmations = confidence.getDepthInBlocks();
        }
    }

    public void cleanup() {
        bsqWalletService.removeTxConfidenceListener(txConfidenceListener);
    }
}
