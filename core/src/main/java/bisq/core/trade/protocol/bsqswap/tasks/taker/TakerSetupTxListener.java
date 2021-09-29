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

package bisq.core.trade.protocol.bsqswap.tasks.taker;

import bisq.core.trade.model.bsqswap.BsqSwapTrade;
import bisq.core.trade.protocol.bsqswap.tasks.SetupTxListener;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Address;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class TakerSetupTxListener extends SetupTxListener {

    @SuppressWarnings({"unused"})
    public TakerSetupTxListener(TaskRunner<BsqSwapTrade> taskHandler, BsqSwapTrade bsqSwapTrade) {
        super(taskHandler, bsqSwapTrade);
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
