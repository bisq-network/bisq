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

package bisq.core.trade.protocol.bisq_v1.tasks.maker;

import bisq.core.btc.wallet.Restrictions;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

import bisq.common.config.Config;
import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MakerSetsLockTime extends TradeTask {
    public MakerSetsLockTime(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            // 10 days for altcoins, 20 days for other payment methods
            // For regtest dev environment we use 5 blocks
            int delay = Config.baseCurrencyNetwork().isRegtest() ?
                    5 :
                    Restrictions.getLockTime(processModel.getOffer().getPaymentMethod().isBlockchain());

            long lockTime = processModel.getBtcWalletService().getBestChainHeight() + delay;
            log.info("lockTime={}, delay={}", lockTime, delay);
            trade.setLockTime(lockTime);

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
