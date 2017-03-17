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

package io.bisq.trade.protocol.tasks.offerer;

import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.trade.Trade;
import io.bisq.trade.protocol.tasks.TradeTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerifyTakerAccount extends TradeTask {
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(VerifyTakerAccount.class);

    @SuppressWarnings({"WeakerAccess", "unused"})
    public VerifyTakerAccount(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            //TODO impl. missing
           /* if (processModel.getBlockChainService().isAccountBlackListed(processModel.tradingPeer.getAccountId(),
                    processModel.tradingPeer.getPaymentAccountContractData())) {
                log.error("Taker is blacklisted");
                failed("Taker is blacklisted");
            }
            else {*/
            complete();
            //}
        } catch (Throwable t) {
            failed(t);
        }
    }
}

