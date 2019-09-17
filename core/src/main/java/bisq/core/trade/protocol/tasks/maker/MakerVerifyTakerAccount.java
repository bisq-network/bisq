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

package bisq.core.trade.protocol.tasks.maker;

import bisq.core.trade.Trade;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MakerVerifyTakerAccount extends TradeTask {
    @SuppressWarnings({"unused"})
    public MakerVerifyTakerAccount(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            //TODO impl. missing
           /* if (processModel.getBlockChainService().isAccountBlackListed(processModel.tradingPeer.getAccountId(),
                    processModel.tradingPeer.getPaymentAccountPayload())) {
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

