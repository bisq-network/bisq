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

import bisq.core.app.BisqEnvironment;
import bisq.core.trade.Trade;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.common.app.DevEnv;
import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MakerSetsLockTime extends TradeTask {
    @SuppressWarnings({"unused"})
    public MakerSetsLockTime(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            // 10 days for altcoins, 20 days for other payment methods
            int delay = processModel.getOffer().getPaymentMethod().isAsset() ? 144 * 10 : 144 * 20;
            if (BisqEnvironment.getBaseCurrencyNetwork().isRegtest()) {
                delay = 5;
            }

            long lockTime = processModel.getBtcWalletService().getBestChainHeight() + delay;
            log.info("lockTime={}, delay={}", lockTime, delay);
            trade.setLockTime(lockTime);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
