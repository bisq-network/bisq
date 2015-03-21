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
import io.bitsquare.trade.protocol.trade.taker.models.TakerAsSellerModel;

import org.bitcoinj.core.Transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TakerCommitDepositTx extends Task<TakerAsSellerModel> {
    private static final Logger log = LoggerFactory.getLogger(TakerCommitDepositTx.class);

    public TakerCommitDepositTx(TaskRunner taskHandler, TakerAsSellerModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void doRun() {
        try {
            // We need to put the tx into our wallet to have a fully setup tx
            Transaction localDepositTx = model.tradeWalletService.takerCommitsDepositTx(model.trade.getDepositTx());
            model.trade.setDepositTx(localDepositTx);
            
            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
