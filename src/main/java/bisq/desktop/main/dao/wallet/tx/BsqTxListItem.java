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

package bisq.desktop.main.dao.wallet.tx;

import bisq.desktop.components.indicator.TxConfidenceIndicator;
import bisq.desktop.util.GUIUtil;

import bisq.core.btc.listeners.TxConfidenceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletService;
import bisq.core.dao.blockchain.vo.Tx;
import bisq.core.dao.blockchain.vo.TxType;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutput;

import javafx.scene.control.Tooltip;

import java.util.Date;
import java.util.Optional;

import lombok.Data;

import static com.google.common.base.Preconditions.checkNotNull;


@Data
class BsqTxListItem {
    private final Transaction transaction;
    private final Optional<Tx> optionalTx;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private Date date;
    private final String txId;
    private int confirmations = 0;
    private final String address;
    private final String direction;
    private Coin amount;
    private boolean received;
    private boolean isBurnedBsqTx;
    private BsqFormatter bsqFormatter;
    private TxConfidenceIndicator txConfidenceIndicator;
    private TxConfidenceListener txConfidenceListener;
    private boolean issuanceTx;

    BsqTxListItem(Transaction transaction,
                  Optional<Tx> optionalTx,
                  BsqWalletService bsqWalletService,
                  BtcWalletService btcWalletService,
                  boolean isBurnedBsqTx,
                  Date date,
                  BsqFormatter bsqFormatter) {
        this.transaction = transaction;
        this.optionalTx = optionalTx;
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.isBurnedBsqTx = isBurnedBsqTx;
        this.date = date;
        this.bsqFormatter = bsqFormatter;

        txId = transaction.getHashAsString();

        setupConfidence(bsqWalletService);

        checkNotNull(transaction, "transaction must not be null as we only have list items from transactions " +
                "which are available in the wallet");

        Coin valueSentToMe = bsqWalletService.getValueSentToMeForTransaction(transaction);
        Coin valueSentFromMe = bsqWalletService.getValueSentFromMeForTransaction(transaction);
        amount = valueSentToMe.subtract(valueSentFromMe);
        if (amount.isPositive()) {
            direction = Res.get("funds.tx.direction.receivedWith");
            received = true;
        } else if (amount.isNegative()) {
            direction = Res.get("funds.tx.direction.sentTo");
            received = false;
        } else {
            // Self send
            direction = "";
        }

        String sendToAddress = null;
        for (TransactionOutput output : transaction.getOutputs()) {
            if (!bsqWalletService.isTransactionOutputMine(output) &&
                    !btcWalletService.isTransactionOutputMine(output) &&
                    WalletService.isOutputScriptConvertibleToAddress(output)) {
                // We don't support send txs with multiple outputs to multiple receivers, so we can
                // assume that only one output is not from our own wallets.
                sendToAddress = bsqFormatter.getBsqAddressStringFromAddress(WalletService.getAddressFromOutput(output));
                break;
            }
        }

        // In the case we sent to ourselves (either to BSQ or BTC wallet) we show the first as the other is
        // usually the change output.
        String receivedWithAddress = Res.get("shared.na");
        if (sendToAddress != null) {
            for (TransactionOutput output : transaction.getOutputs()) {
                if (WalletService.isOutputScriptConvertibleToAddress(output)) {
                    receivedWithAddress = bsqFormatter.getBsqAddressStringFromAddress(WalletService.getAddressFromOutput(output));
                    break;
                }
            }
        }

        if (!isBurnedBsqTx)
            address = received ? receivedWithAddress : sendToAddress;
        else
            address = "";
    }

    private void setupConfidence(BsqWalletService bsqWalletService) {
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

    private void updateConfidence(TransactionConfidence confidence, Tooltip tooltip) {
        if (confidence != null) {
            GUIUtil.updateConfidence(confidence, tooltip, txConfidenceIndicator);
            confirmations = confidence.getDepthInBlocks();
        }
    }

    public void cleanup() {
        bsqWalletService.removeTxConfidenceListener(txConfidenceListener);
    }

    public TxType getTxType() {
        if (optionalTx.isPresent())
            return optionalTx.get().getTxType();
        else
            return confirmations == 0 ? TxType.UNVERIFIED : TxType.UNDEFINED_TX_TYPE;
    }

    public void setAmount(Coin amount) {
        this.amount = amount;
    }
}

