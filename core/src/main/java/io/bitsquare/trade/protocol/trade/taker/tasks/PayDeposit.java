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

package io.bitsquare.trade.protocol.trade.taker.tasks;

import io.bitsquare.btc.FeePolicy;
import io.bitsquare.trade.protocol.trade.taker.SellerAsTakerModel;
import io.bitsquare.util.taskrunner.Task;
import io.bitsquare.util.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PayDeposit extends Task<SellerAsTakerModel> {
    private static final Logger log = LoggerFactory.getLogger(PayDeposit.class);

    public PayDeposit(TaskRunner taskHandler, SellerAsTakerModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void doRun() {
        try {
            Coin amountToPay = model.getTrade().getTradeAmount().add(model.getTrade().getSecurityDeposit());
            Coin msOutputAmount = amountToPay.add(model.getTrade().getSecurityDeposit()).add(FeePolicy.TX_FEE);
            Transaction signedTakerDepositTx = model.getWalletService().takerAddPaymentAndSignTx(
                    amountToPay,
                    msOutputAmount,
                    model.getPreparedDepositTx(),
                    model.getTrade().getId(),
                    model.getOffererPubKey(),
                    model.getTakerPubKey(),
                    model.getArbitratorPubKey());

            model.setSignedTakerDepositTx(signedTakerDepositTx);

            complete();
        } catch (InsufficientMoneyException e) {
            failed(e);
        }
    }

    @Override
    protected void updateStateOnFault() {
    }
}
