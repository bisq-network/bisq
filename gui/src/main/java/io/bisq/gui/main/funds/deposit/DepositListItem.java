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

package io.bisq.gui.main.funds.deposit;

import io.bisq.common.locale.Res;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.listeners.BalanceListener;
import io.bisq.core.btc.listeners.TxConfidenceListener;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.gui.components.indicator.TxConfidenceIndicator;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.GUIUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Tooltip;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DepositListItem {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final StringProperty balance = new SimpleStringProperty();
    private final BtcWalletService walletService;
    private Coin balanceAsCoin;
    private final TxConfidenceIndicator txConfidenceIndicator;
    private final Tooltip tooltip;
    private final String addressString;
    private String usage = "-";
    private TxConfidenceListener txConfidenceListener;
    private int numTxOutputs = 0;

    public DepositListItem(AddressEntry addressEntry, BtcWalletService walletService, BSFormatter formatter) {
        this.walletService = walletService;

        addressString = addressEntry.getAddressString();

        // confidence
        txConfidenceIndicator = new TxConfidenceIndicator();
        txConfidenceIndicator.setId("funds-confidence");
        tooltip = new Tooltip(Res.get("shared.notUsedYet"));
        txConfidenceIndicator.setProgress(0);
        txConfidenceIndicator.setPrefHeight(30);
        txConfidenceIndicator.setPrefWidth(30);
        txConfidenceIndicator.setTooltip(tooltip);

        final Address address = addressEntry.getAddress();
        walletService.addBalanceListener(new BalanceListener(address) {
            @Override
            public void onBalanceChanged(Coin balanceAsCoin, Transaction tx) {
                DepositListItem.this.balanceAsCoin = balanceAsCoin;
                DepositListItem.this.balance.set(formatter.formatCoin(balanceAsCoin));
                GUIUtil.updateConfidence(walletService.getConfidenceForTxId(tx.getHashAsString()), tooltip, txConfidenceIndicator);
                updateUsage(address);
            }
        });

        balanceAsCoin = walletService.getBalanceForAddress(address);
        balance.set(formatter.formatCoin(balanceAsCoin));

        updateUsage(address);

        TransactionConfidence confidence = walletService.getConfidenceForAddress(address);
        if (confidence != null) {
            GUIUtil.updateConfidence(confidence, tooltip, txConfidenceIndicator);

            txConfidenceListener = new TxConfidenceListener(confidence.getTransactionHash().toString()) {
                @Override
                public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
                    GUIUtil.updateConfidence(confidence, tooltip, txConfidenceIndicator);
                }
            };
            walletService.addTxConfidenceListener(txConfidenceListener);
        }
    }

    private void updateUsage(Address address) {
        numTxOutputs = walletService.getNumTxOutputsForAddress(address);
        usage = numTxOutputs == 0 ? Res.get("funds.deposit.unused") : Res.get("funds.deposit.usedInTx", numTxOutputs);
    }

    public void cleanup() {
        walletService.removeTxConfidenceListener(txConfidenceListener);
    }

    public TxConfidenceIndicator getTxConfidenceIndicator() {
        return txConfidenceIndicator;
    }

    public String getAddressString() {
        return addressString;
    }

    public String getUsage() {
        return usage;
    }

    public final StringProperty balanceProperty() {
        return this.balance;
    }

    public String getBalance() {
        return balance.get();
    }

    public Coin getBalanceAsCoin() {
        return balanceAsCoin;
    }

    public int getNumTxOutputs() {
        return numTxOutputs;
    }
}
