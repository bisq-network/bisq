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

package bisq.core.trade.protocol.bisq_v1.tasks.seller;

import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.messages.ShareBuyerPaymentAccountMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.util.JsonUtil;

import bisq.common.crypto.Hash;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.taskrunner.TaskRunner;

import java.security.PrivateKey;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.trade.validation.TradeValidation.checkHashFromContract;
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

            Contract contract = checkNotNull(trade.getContract());
            PubKeyRing myPubKeyRing = processModel.getPubKeyRing();

            PaymentAccountPayload myPaymentAccountPayload = checkNotNull(processModel.getPaymentAccountPayload(trade),
                    "Payment account payload cannot be null for trade: " + trade.getId());

            PaymentAccountPayload peersPaymentAccountPayload = checkNotNull(message.getBuyerPaymentAccountPayload(),
                    "buyerPaymentAccountPayload must not be null for trade: " + trade.getId());
            byte[] peersHashFromAccountPayload = peersPaymentAccountPayload.getHashForContract();
            byte[] peersCommittedHashFromContract = contract.getHashOfPeersPaymentAccountPayload(myPubKeyRing);

            // Check if the hash of the provided payment account payload matches the hash from the contract
            // which the peer committed in early stages of the trade protocol.
            checkHashFromContract(peersHashFromAccountPayload, peersCommittedHashFromContract,
                    "peersPaymentAccountPayloadHash");

            processModel.getTradePeer().setPaymentAccountPayload(peersPaymentAccountPayload);

            // Apply both peers and my payloads to contract
            contract.setPaymentAccountPayloads(peersPaymentAccountPayload, myPaymentAccountPayload, myPubKeyRing);

            // As we have added the payment accounts we need to update the json. We also update the signature
            // thought that has less relevance with the changes of 1.7.0
            String contractAsJson = checkNotNull(JsonUtil.objectToJson(contract));
            byte[] contractHash = Hash.getSha256Hash(contractAsJson);

            PrivateKey privateKey = processModel.getKeyRing().getSignatureKeyPair().getPrivate();
            String signature = Sig.sign(privateKey, contractAsJson);

            // If nothing failed we commit to trade
            if (contract.isBuyerMakerAndSellerTaker()) {
                trade.setTakerContractSignature(signature);
            } else {
                trade.setMakerContractSignature(signature);
            }
            trade.setContractAsJson(contractAsJson);
            trade.setContractHash(contractHash);

            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
