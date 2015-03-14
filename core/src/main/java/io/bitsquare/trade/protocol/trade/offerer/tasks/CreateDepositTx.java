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

import io.bitsquare.btc.FeePolicy;
import io.bitsquare.trade.protocol.trade.offerer.BuyerAsOffererModel;
import io.bitsquare.util.taskrunner.Task;
import io.bitsquare.util.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateDepositTx extends Task<BuyerAsOffererModel> {
    private static final Logger log = LoggerFactory.getLogger(CreateDepositTx.class);

    public CreateDepositTx(TaskRunner taskHandler, BuyerAsOffererModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void doRun() {
        try {
            String offererPubKey = model.getWalletService().getAddressInfoByTradeID(model.getTrade().getId()).getPubKeyAsHexString();
            Coin offererInputAmount = model.getTrade().getSecurityDeposit().add(FeePolicy.TX_FEE);
            Transaction transaction = model.getWalletService().offererCreatesMSTxAndAddPayment(
                    offererInputAmount,
                    offererPubKey,
                    model.getTradePubKeyAsHex(),
                    model.getArbitratorPubKey(),
                    model.getTrade().getId());

            String preparedOffererDepositTxAsHex = Utils.HEX.encode(transaction.bitcoinSerialize());
            long offererTxOutIndex = transaction.getInput(0).getOutpoint().getIndex();

            model.setOffererPubKey(offererPubKey);
            model.setPreparedOffererDepositTxAsHex(preparedOffererDepositTxAsHex);
            model.setOffererTxOutIndex(offererTxOutIndex);

            complete();
        } catch (InsufficientMoneyException e) {
            failed(e);
        }
    }

    @Override
    protected void updateStateOnFault() {
    }
}
