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

package bisq.core.trade.atomic.protocol.tasks.maker;

import bisq.core.trade.atomic.AtomicTrade;
import bisq.core.trade.atomic.protocol.tasks.AtomicSetupTxListener;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Address;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AtomicMakerSetupTxListener extends AtomicSetupTxListener {

    @SuppressWarnings({"unused"})
    public AtomicMakerSetupTxListener(TaskRunner<AtomicTrade> taskHandler, AtomicTrade atomicTrade) {
        super(taskHandler, atomicTrade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            // Find address to listen to
            if (atomicProcessModel.getMakerBtcAddress() != null) {
                walletService = atomicProcessModel.getBtcWalletService();
                myAddress = Address.fromString(walletService.getParams(), atomicProcessModel.getMakerBtcAddress());
            } else if (atomicProcessModel.getMakerBsqAddress() != null){
                // Listen to BSQ address
                walletService = atomicProcessModel.getBsqWalletService();
                myAddress = Address.fromString(walletService.getParams(), atomicProcessModel.getMakerBsqAddress());
            } else {
                failed("No maker address set");
            }

            super.run();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
