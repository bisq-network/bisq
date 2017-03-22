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
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import io.bisq.protobuffer.message.trade.PublishDepositTxRequest;
import io.bisq.protobuffer.payload.filter.PaymentAccountFilter;
import io.bisq.protobuffer.payload.payment.PaymentAccountPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.bisq.core.util.Validator.checkTradeId;
import static io.bisq.core.util.Validator.nonEmptyStringOf;

public class ProcessPublishDepositTxRequest extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(ProcessPublishDepositTxRequest.class);

    @SuppressWarnings({"WeakerAccess", "unused"})
    public ProcessPublishDepositTxRequest(TaskRunner taskHandler, Trade trade) {
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

            PaymentAccountPayload paymentAccountPayload = checkNotNull(publishDepositTxRequest.offererPaymentAccountPayload);
            final PaymentAccountFilter[] appliedPaymentAccountFilter = new PaymentAccountFilter[1];
            if (processModel.isPeersPaymentAccountDataAreBanned(paymentAccountPayload, appliedPaymentAccountFilter)) {
                failed("Other trader is banned by his trading account data.\n" +
                        "paymentAccountPayload=" + paymentAccountPayload.getPaymentDetails() + "\n" +
                        "banFilter=" + appliedPaymentAccountFilter[0].toString());
                return;
            }

            processModel.tradingPeer.setPaymentAccountPayload(paymentAccountPayload);
            processModel.tradingPeer.setAccountId(nonEmptyStringOf(publishDepositTxRequest.offererAccountId));
            processModel.tradingPeer.setMultiSigPubKey(checkNotNull(publishDepositTxRequest.offererMultiSigPubKey));
            processModel.tradingPeer.setContractAsJson(nonEmptyStringOf(publishDepositTxRequest.offererContractAsJson));
            processModel.tradingPeer.setContractSignature(nonEmptyStringOf(publishDepositTxRequest.offererContractSignature));
            processModel.tradingPeer.setPayoutAddressString(nonEmptyStringOf(publishDepositTxRequest.offererPayoutAddressString));
            processModel.tradingPeer.setRawTransactionInputs(checkNotNull(publishDepositTxRequest.offererInputs));
            processModel.setPreparedDepositTx(checkNotNull(publishDepositTxRequest.preparedDepositTx));
            checkArgument(publishDepositTxRequest.offererInputs.size() > 0);

            // update to the latest peer address of our peer if the message is correct
            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}