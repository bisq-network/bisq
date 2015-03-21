/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade.protocol.trade.taker.tasks;

import io.bitsquare.common.taskrunner.Task;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.offerer.models.OffererAsBuyerModel;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;

import javafx.application.Platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetupListenerForBlockChainConfirmation extends Task<OffererAsBuyerModel> {
    private static final Logger log = LoggerFactory.getLogger(SetupListenerForBlockChainConfirmation.class);

    private TransactionConfidence.Listener transactionConfidenceListener;
    private TransactionConfidence transactionConfidence;

    public SetupListenerForBlockChainConfirmation(TaskRunner taskHandler, OffererAsBuyerModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void doRun() {
        transactionConfidence = model.trade.getDepositTx().getConfidence();
        transactionConfidenceListener = new TransactionConfidence.Listener() {
            @Override
            public void onConfidenceChanged(Transaction tx, ChangeReason reason) {
                log.trace("onConfidenceChanged " + tx.getConfidence());
                if (reason == ChangeReason.TYPE && tx.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING) {
                    model.trade.setState(Trade.State.DEPOSIT_CONFIRMED);

                    // transactionConfidence use CopyOnWriteArrayList as listeners, but be safe and delay remove a bit.
                    Platform.runLater(() -> removeEventListener());
                }
            }
        };
        transactionConfidence.addEventListener(transactionConfidenceListener);

        complete();
    }

    private void removeEventListener() {
        if (!transactionConfidence.removeEventListener(transactionConfidenceListener))
            throw new RuntimeException("Remove transactionConfidenceListener failed at SetupListenerForBlockChainConfirmation.");
    }
}
