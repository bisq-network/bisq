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

import io.bitsquare.trade.protocol.trade.offerer.messages.BankTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.taker.SellerAsTakerModel;
import io.bitsquare.common.taskrunner.Task;
import io.bitsquare.common.taskrunner.TaskRunner;

import org.bitcoinj.core.ECKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bitsquare.util.Validator.*;

public class ProcessBankTransferStartedMessage extends Task<SellerAsTakerModel> {
    private static final Logger log = LoggerFactory.getLogger(ProcessBankTransferStartedMessage.class);

    public ProcessBankTransferStartedMessage(TaskRunner taskHandler, SellerAsTakerModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void doRun() {
        try {
            checkTradeId(model.getId(), model.getTradeMessage());
            BankTransferStartedMessage message = (BankTransferStartedMessage) model.getTradeMessage();

            model.setDepositTx(checkNotNull(message.getDepositTx()));
            model.setOffererSignature(checkNotNull(ECKey.ECDSASignature.decodeFromDER(message.getOffererSignature())));
            model.setOffererPayoutAmount(positiveCoinOf(nonZeroCoinOf(message.getOffererPayoutAmount())));
            model.setTakerPayoutAmount(positiveCoinOf(nonZeroCoinOf(message.getTakerPayoutAmount())));
            model.setOffererPayoutAddress(nonEmptyStringOf(message.getOffererPayoutAddress()));

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }

    @Override
    protected void updateStateOnFault() {
    }
}