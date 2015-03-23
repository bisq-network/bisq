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

package io.bitsquare.trade.protocol.trade.offerer.tasks;

import io.bitsquare.common.taskrunner.Task;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.offerer.models.OffererAsBuyerModel;

import org.bitcoinj.core.TransactionConfidence;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetupListenerForBlockChainConfirmation extends Task<OffererAsBuyerModel> {
    private static final Logger log = LoggerFactory.getLogger(SetupListenerForBlockChainConfirmation.class);

    public SetupListenerForBlockChainConfirmation(TaskRunner taskHandler, OffererAsBuyerModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void doRun() {
        TransactionConfidence  transactionConfidence = model.trade.getDepositTx().getConfidence();
        ListenableFuture<TransactionConfidence> future = transactionConfidence.getDepthFuture(1);
        Futures.addCallback(future, new FutureCallback<TransactionConfidence>() {
            @Override
            public void onSuccess(TransactionConfidence result) {
                model.trade.setProcessState(Trade.ProcessState.DEPOSIT_CONFIRMED);
            }

            @Override
            public void onFailure(Throwable t) {
                t.printStackTrace();
                log.error(t.getMessage());
                Throwables.propagate(t);
            }
        });

        complete();
    }
}
