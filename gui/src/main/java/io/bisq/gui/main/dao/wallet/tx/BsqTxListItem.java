/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.main.dao.wallet.tx;

import io.bisq.common.locale.Res;
import io.bisq.core.btc.listeners.TxConfidenceListener;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.gui.components.indicator.TxConfidenceIndicator;
import io.bisq.gui.util.GUIUtil;
import javafx.scene.control.Tooltip;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutput;

import java.util.Date;

import static com.google.common.base.Preconditions.checkNotNull;


@ToString
@Slf4j
class BsqTxListItem {
    @Getter
    private final Transaction transaction;
    private BsqWalletService bsqWalletService;
    @Getter
    private final Date date;
    @Getter
    private final String txId;
    @Getter
    private int confirmations = 0;
    @Getter
    private final String address;
    @Getter
    private final String direction;
    @Getter
    private final Coin amount;
    @Getter
    private boolean received;
    @Getter
    private TxConfidenceIndicator txConfidenceIndicator;

    private TxConfidenceListener txConfidenceListener;

    public BsqTxListItem(Transaction transaction, BsqWalletService bsqWalletService) {
        this.transaction = transaction;
        this.bsqWalletService = bsqWalletService;

        txId = transaction.getHashAsString();
        date = transaction.getUpdateTime();

        setupConfidence(bsqWalletService);

        checkNotNull(transaction, "transaction must not be null as we only have list items from transactions " +
                "which are available in the wallet");

        Coin valueSentToMe = bsqWalletService.getValueSentToMeForTransaction(transaction);
        Coin valueSentFromMe = bsqWalletService.getValueSentFromMeForTransaction(transaction);

        if (valueSentToMe.compareTo(valueSentFromMe) > 0) {
            amount = valueSentToMe.subtract(valueSentFromMe);
            direction = Res.get("funds.tx.direction.receivedWith");
            received = true;
        } else if (valueSentToMe.compareTo(valueSentFromMe) < 0) {
            amount = valueSentFromMe.subtract(valueSentToMe);
            direction = Res.get("funds.tx.direction.sentTo");
            received = false;
        } else {
            amount = Coin.ZERO;
            direction = "";
        }
        String result = null;
        for (TransactionOutput output : transaction.getOutputs()) {
            if (!bsqWalletService.isTransactionOutputMine(output)) {
                if (output.getScriptPubKey().isSentToAddress()
                        || output.getScriptPubKey().isPayToScriptHash()) {
                    result = output.getScriptPubKey().getToAddress(bsqWalletService.getParams()).toString();
                }
            }
        }
        address = result != null ? result : "";
    }

    private void setupConfidence(BsqWalletService bsqWalletService) {
        txConfidenceIndicator = new TxConfidenceIndicator();
        txConfidenceIndicator.setId("funds-confidence");
        Tooltip tooltip = new Tooltip();
        txConfidenceIndicator.setProgress(0);
        txConfidenceIndicator.setPrefHeight(30);
        txConfidenceIndicator.setPrefWidth(30);
        Tooltip.install(txConfidenceIndicator, tooltip);

        txConfidenceListener = new TxConfidenceListener(txId) {
            @Override
            public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
                updateConfidence(confidence, tooltip);
            }
        };
        bsqWalletService.addTxConfidenceListener(txConfidenceListener);
        updateConfidence(bsqWalletService.getConfidenceForTxId(txId), tooltip);
    }

    private void updateConfidence(TransactionConfidence confidence, Tooltip tooltip) {
        if (confidence != null) {
            GUIUtil.updateConfidence(confidence, tooltip, txConfidenceIndicator);
            confirmations = confidence.getDepthInBlocks();
        }
    }

    public void cleanup() {
        bsqWalletService.removeTxConfidenceListener(txConfidenceListener);
    }
}

