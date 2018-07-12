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

package bisq.desktop.main.dao.bonding.unlock;

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.indicator.TxConfidenceIndicator;
import bisq.desktop.util.GUIUtil;

import bisq.core.btc.listeners.TxConfidenceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxType;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;

import javafx.scene.control.Tooltip;

import java.util.Date;
import java.util.Optional;

import lombok.Data;

import static com.google.common.base.Preconditions.checkNotNull;

@Data
class LockedTxListItem {
    private final Transaction transaction;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final DaoFacade daoFacade;
    private final StateService stateService;
    private final BsqFormatter bsqFormatter;
    private final Date date;
    private final String txId;

    private int confirmations = 0;
    private Coin amount = Coin.ZERO;
    private int lockTime;
    private AutoTooltipButton button;

    private TxConfidenceIndicator txConfidenceIndicator;
    private TxConfidenceListener txConfidenceListener;
    private boolean issuanceTx;

    LockedTxListItem(Transaction transaction,
                     BsqWalletService bsqWalletService,
                     BtcWalletService btcWalletService,
                     DaoFacade daoFacade,
                     StateService stateService,
                     Date date,
                     BsqFormatter bsqFormatter) {
        this.transaction = transaction;
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.daoFacade = daoFacade;
        this.stateService = stateService;
        this.date = date;
        this.bsqFormatter = bsqFormatter;

        this.txId = transaction.getHashAsString();

        setupConfidence(bsqWalletService);

        checkNotNull(transaction, "transaction must not be null as we only have list items from transactions " +
                "which are available in the wallet");

        stateService.getLockupTxOutput(transaction.getHashAsString()).ifPresent(
                out -> amount = Coin.valueOf(out.getValue()));

        //TODO SQ: use DaoFacade instead of direct access to stateService
        Optional<Integer> opLockTime = stateService.getLockTime(transaction.getHashAsString());
        lockTime = opLockTime.orElse(-1);

        button = new AutoTooltipButton();
        button.setMinWidth(70);
        button.setText(Res.get("dao.bonding.unlock.unlock"));
        button.setVisible(true);
        button.setManaged(true);
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

    public boolean isLockedAndUnspent() {
        return !isSpent() && getTxType() == TxType.LOCKUP;
    }

    public void cleanup() {
        bsqWalletService.removeTxConfidenceListener(txConfidenceListener);
    }

    // TODO SQ use daoFacade
    public boolean isSpent() {
        Optional<TxOutput> optionalTxOutput = stateService.getLockupTxOutput(txId);
        if (!optionalTxOutput.isPresent())
            return true;

        return !stateService.isUnspent(optionalTxOutput.get().getKey());
    }

    public TxType getTxType() {
        return daoFacade.getTx(txId)
                .flatMap(tx -> daoFacade.getOptionalTxType(tx.getId()))
                .orElse(confirmations == 0 ? TxType.UNVERIFIED : TxType.UNDEFINED_TX_TYPE);
    }

    public void setAmount(Coin amount) {
        this.amount = amount;
    }
}
