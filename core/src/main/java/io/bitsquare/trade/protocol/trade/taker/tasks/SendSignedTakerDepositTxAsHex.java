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

import io.bitsquare.trade.listeners.SendMessageListener;
import io.bitsquare.trade.protocol.trade.taker.SellerAsTakerModel;
import io.bitsquare.trade.protocol.trade.taker.messages.RequestOffererPublishDepositTxMessage;
import io.bitsquare.util.taskrunner.Task;
import io.bitsquare.util.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendSignedTakerDepositTxAsHex extends Task<SellerAsTakerModel> {
    private static final Logger log = LoggerFactory.getLogger(SendSignedTakerDepositTxAsHex.class);

    public SendSignedTakerDepositTxAsHex(TaskRunner taskHandler, SellerAsTakerModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void doRun() {
        Transaction signedTakerDepositTx = model.getSignedTakerDepositTx();
        long takerTxOutIndex = model.getSignedTakerDepositTx().getInput(1).getOutpoint().getIndex();

        RequestOffererPublishDepositTxMessage tradeMessage = new RequestOffererPublishDepositTxMessage(
                model.getTrade().getId(),
                model.getBankAccount(),
                model.getAccountId(),
                model.getMessagePublicKey(),
                Utils.HEX.encode(signedTakerDepositTx.bitcoinSerialize()),
                Utils.HEX.encode(signedTakerDepositTx.getInput(1).getScriptBytes()),
                Utils.HEX.encode(signedTakerDepositTx.getInput(1).getConnectedOutput().getParentTransaction().bitcoinSerialize()),
                model.getTrade().getContractAsJson(),
                model.getTrade().getTakerContractSignature(),
                model.getWalletService().getAddressInfoByTradeID(model.getTrade().getId()).getAddressString(),
                takerTxOutIndex,
                model.getPeersTxOutIndex());

        model.getTradeMessageService().sendMessage(model.getPeer(), tradeMessage, new SendMessageListener() {
            @Override
            public void handleResult() {
                complete();
            }

            @Override
            public void handleFault() {
                failed("Sending RequestOffererDepositPublicationMessage failed");
            }
        });
    }
    @Override
    protected void rollBackOnFault() {
    }
}
