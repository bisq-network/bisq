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

import io.bitsquare.btc.exceptions.SigningException;
import io.bitsquare.btc.exceptions.TransactionVerificationException;
import io.bitsquare.btc.exceptions.WalletException;
import io.bitsquare.common.taskrunner.Task;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.OffererTrade;
import io.bitsquare.trade.protocol.trade.offerer.models.OffererTradeProcessModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OffererTradeTask extends Task<OffererTrade> {
    private static final Logger log = LoggerFactory.getLogger(OffererTradeTask.class);
    protected final OffererTradeProcessModel offererTradeProcessModel;
    protected final OffererTrade offererTrade;

    public OffererTradeTask(TaskRunner taskHandler, OffererTrade model) {
        super(taskHandler, model);

        offererTrade = model;
        offererTradeProcessModel = offererTrade.getOffererTradeProcessModel();
    }

    @Override
    protected void doRun() throws WalletException, TransactionVerificationException, SigningException {
    }
}
