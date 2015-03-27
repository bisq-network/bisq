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

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.OffererTrade;

import org.bitcoinj.core.Coin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateAndSignPayoutTx extends OffererTradeTask {
    private static final Logger log = LoggerFactory.getLogger(CreateAndSignPayoutTx.class);

    public CreateAndSignPayoutTx(TaskRunner taskHandler, OffererTrade offererTradeProcessModel) {
        super(taskHandler, offererTradeProcessModel);
    }

    @Override
    protected void doRun() {
        try {
            Coin securityDeposit = offererTrade.getSecurityDeposit();
            Coin offererPayoutAmount = offererTrade.getTradeAmount().add(securityDeposit);
            @SuppressWarnings("UnnecessaryLocalVariable") Coin takerPayoutAmount = securityDeposit;

            byte[] offererPayoutTxSignature = offererTradeProcessModel.tradeWalletService.offererCreatesAndSignsPayoutTx(
                    offererTrade.getDepositTx(),
                    offererPayoutAmount,
                    takerPayoutAmount,
                    offererTradeProcessModel.offerer.addressEntry,
                    offererTradeProcessModel.taker.payoutAddressString,
                    offererTradeProcessModel.offerer.tradeWalletPubKey,
                    offererTradeProcessModel.taker.tradeWalletPubKey,
                    offererTradeProcessModel.arbitratorPubKey);

            offererTradeProcessModel.offerer.payoutTxSignature = offererPayoutTxSignature;
            offererTradeProcessModel.offerer.payoutAmount = offererPayoutAmount;
            offererTradeProcessModel.taker.payoutAmount = takerPayoutAmount;

            complete();
        } catch (Throwable t) {
            t.printStackTrace();
            offererTrade.setThrowable(t);
            failed(t);
        }
    }
}

