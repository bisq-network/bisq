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
import bisq.desktop.util.BsqFormatter;
import bisq.desktop.util.GUIUtil;

import bisq.core.btc.listeners.TxConfidenceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletService;
import bisq.core.dao.blockchain.BsqBlockChain;
import bisq.core.dao.blockchain.vo.TxType;

import bisq.common.locale.Res;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutput;

import javafx.scene.control.Tooltip;

import java.util.Date;
import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;


@ToString
@Slf4j
@EqualsAndHashCode
class BsqTxListItem {
    @Getter
    private final Transaction transaction;
    private BsqWalletService bsqWalletService;
    private BtcWalletService btcWalletService;
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
    private Optional<TxType> txTypeOptional;
    @Getter
    private boolean isBurnedBsqTx;
    private BsqFormatter bsqFormatter;
    @Getter
    private TxConfidenceIndicator txConfidenceIndicator;

    private TxConfidenceListener txConfidenceListener;

    public BsqTxListItem(Transaction transaction,
                         BsqWalletService bsqWalletService,
                         BtcWalletService btcWalletService,
                         Optional<TxType> txTypeOptional,
                         boolean isBurnedBsqTx,
                         BsqBlockChain bsqBlockChain,
                         BsqFormatter bsqFormatter) {
        this.transaction = transaction;
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.txTypeOptional = txTypeOptional;
        this.isBurnedBsqTx = isBurnedBsqTx;
        this.bsqFormatter = bsqFormatter;

        txId = transaction.getHashAsString();
        date = transaction.getUpdateTime();

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
        if (txTypeOptional.isPresent())
            return txTypeOptional.get();
        else
            return confirmations == 0 ? TxType.UNVERIFIED : TxType.UNDEFINED_TX_TYPE;
    }
}

