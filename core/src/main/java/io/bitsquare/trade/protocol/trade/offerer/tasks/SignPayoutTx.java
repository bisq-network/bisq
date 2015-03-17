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

import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.common.taskrunner.Task;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.offerer.models.BuyerAsOffererModel;

import org.bitcoinj.core.Coin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignPayoutTx extends Task<BuyerAsOffererModel> {
    private static final Logger log = LoggerFactory.getLogger(SignPayoutTx.class);

    public SignPayoutTx(TaskRunner taskHandler, BuyerAsOffererModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void doRun() {
        try {
            Trade trade = model.getTrade();
            Coin securityDeposit = trade.getSecurityDeposit();
            Coin offererPayoutAmount = trade.getTradeAmount().add(securityDeposit);
            @SuppressWarnings("UnnecessaryLocalVariable") Coin takerPayoutAmount = securityDeposit;

            TradeWalletService.TransactionDataResult result = model.getTradeWalletService().offererCreatesAndSignsPayoutTx(
                    trade.getDepositTx(),
                    offererPayoutAmount,
                    takerPayoutAmount,
                    model.taker.payoutAddress,
                    model.getWalletService().getAddressEntry(trade.getId()),
                    model.offerer.pubKey,
                    model.taker.pubKey,
                    model.getArbitratorPubKey());

            model.offerer.payoutTx = result.getPayoutTx();
            model.offerer.payoutSignature = result.getOffererSignature();
            model.offerer.payoutAmount = offererPayoutAmount;
            model.taker.payoutAmount = takerPayoutAmount;

            complete();
        } catch (Exception e) {
            failed(e);
        }
    }

    @Override
    protected void updateStateOnFault() {
    }
}

