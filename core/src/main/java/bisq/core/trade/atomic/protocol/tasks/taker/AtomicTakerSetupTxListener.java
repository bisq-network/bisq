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

package bisq.core.trade.atomic.protocol.tasks.taker;

import bisq.core.trade.atomic.AtomicTrade;
import bisq.core.trade.atomic.protocol.tasks.AtomicSetupTxListener;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Address;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class AtomicTakerSetupTxListener extends AtomicSetupTxListener {

    @SuppressWarnings({"unused"})
    public AtomicTakerSetupTxListener(TaskRunner<AtomicTrade> taskHandler, AtomicTrade atomicTrade) {
        super(taskHandler, atomicTrade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            checkNotNull(bsqSwapProtocolModel, "AtomicModel must not be null");

            // Find address to listen to
            if (bsqSwapProtocolModel.getTakerBtcAddress() != null) {
                walletService = bsqSwapProtocolModel.getBtcWalletService();
                myAddress = Address.fromString(walletService.getParams(), bsqSwapProtocolModel.getTakerBtcAddress());
            } else if (bsqSwapProtocolModel.getTakerBsqAddress() != null) {
                // Listen to BSQ address
                walletService = bsqSwapProtocolModel.getBsqWalletService();
                myAddress = Address.fromString(walletService.getParams(), bsqSwapProtocolModel.getTakerBsqAddress());
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
