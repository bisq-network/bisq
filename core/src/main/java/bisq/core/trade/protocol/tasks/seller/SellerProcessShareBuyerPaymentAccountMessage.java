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

package bisq.core.trade.protocol.tasks.seller;

import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.trade.Contract;
import bisq.core.trade.Trade;
import bisq.core.trade.messages.ShareBuyerPaymentAccountMessage;
import bisq.core.trade.protocol.ProcessModel;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.common.crypto.Sig;
import bisq.common.taskrunner.TaskRunner;
import bisq.common.util.Utilities;

import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.util.Validator.checkTradeId;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class SellerProcessShareBuyerPaymentAccountMessage extends TradeTask {
    public SellerProcessShareBuyerPaymentAccountMessage(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            ShareBuyerPaymentAccountMessage message = (ShareBuyerPaymentAccountMessage) processModel.getTradeMessage();
            checkNotNull(message);
            checkTradeId(processModel.getOfferId(), message);

            PaymentAccountPayload buyerPaymentAccountPayload = message.getBuyerPaymentAccountPayload();

            byte[] buyerPaymentAccountPayloadHash = ProcessModel.hashOfPaymentAccountPayload(buyerPaymentAccountPayload);
            Contract contract = trade.getContract();
            byte[] peersPaymentAccountPayloadHash = checkNotNull(contract).getHashOfPeersPaymentAccountPayload(processModel.getPubKeyRing());
            checkArgument(Arrays.equals(buyerPaymentAccountPayloadHash, peersPaymentAccountPayloadHash),
                    "Hash of payment account is invalid");

            processModel.getTradingPeer().setPaymentAccountPayload(buyerPaymentAccountPayload);
            checkNotNull(contract).setPaymentAccountPayloads(buyerPaymentAccountPayload,
                    processModel.getPaymentAccountPayload(trade),
                    processModel.getPubKeyRing());

            // As we have added the payment accounts we need to update the json. We also update the signature
            // thought that has less relevance with the changes of 1.7.0
            String contractAsJson = Utilities.objectToJson(contract);
            String signature = Sig.sign(processModel.getKeyRing().getSignatureKeyPair().getPrivate(), contractAsJson);
            trade.setContractAsJson(contractAsJson);
            if (contract.isBuyerMakerAndSellerTaker()) {
                trade.setTakerContractSignature(signature);
            } else {
                trade.setMakerContractSignature(signature);
            }

            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
