/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.trade.protocol.tasks.taker;

import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.filter.PaymentAccountFilter;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.messages.PublishDepositTxRequest;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.bisq.core.util.Validator.checkTradeId;
import static io.bisq.core.util.Validator.nonEmptyStringOf;

@Slf4j
public class TakerProcessPublishDepositTxRequest extends TradeTask {
    @SuppressWarnings({"WeakerAccess", "unused"})
    public TakerProcessPublishDepositTxRequest(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            log.debug("current trade state " + trade.getState());
            PublishDepositTxRequest publishDepositTxRequest = (PublishDepositTxRequest) processModel.getTradeMessage();
            checkTradeId(processModel.getId(), publishDepositTxRequest);
            checkNotNull(publishDepositTxRequest);

            PaymentAccountPayload paymentAccountPayload = checkNotNull(publishDepositTxRequest.makerPaymentAccountPayload);
            final PaymentAccountFilter[] appliedPaymentAccountFilter = new PaymentAccountFilter[1];
            if (processModel.isPeersPaymentAccountDataAreBanned(paymentAccountPayload, appliedPaymentAccountFilter)) {
                failed("Other trader is banned by his trading account data.\n" +
                        "paymentAccountPayload=" + paymentAccountPayload.getPaymentDetails() + "\n" +
                        "banFilter=" + appliedPaymentAccountFilter[0].toString());
                return;
            }

            processModel.tradingPeer.setPaymentAccountPayload(paymentAccountPayload);
            processModel.tradingPeer.setAccountId(nonEmptyStringOf(publishDepositTxRequest.makerAccountId));
            processModel.tradingPeer.setMultiSigPubKey(checkNotNull(publishDepositTxRequest.makerMultiSigPubKey));
            processModel.tradingPeer.setContractAsJson(nonEmptyStringOf(publishDepositTxRequest.makerContractAsJson));
            processModel.tradingPeer.setContractSignature(nonEmptyStringOf(publishDepositTxRequest.makerContractSignature));
            processModel.tradingPeer.setPayoutAddressString(nonEmptyStringOf(publishDepositTxRequest.makerPayoutAddressString));
            processModel.tradingPeer.setRawTransactionInputs(checkNotNull(publishDepositTxRequest.makerInputs));
            processModel.setPreparedDepositTx(checkNotNull(publishDepositTxRequest.preparedDepositTx));
            checkArgument(publishDepositTxRequest.makerInputs.size() > 0);

            // update to the latest peer address of our peer if the message is correct
            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());
            trade.setState(Trade.State.TAKER_RECEIVED_PUBLISH_DEPOSIT_TX_REQUEST);
            
            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}